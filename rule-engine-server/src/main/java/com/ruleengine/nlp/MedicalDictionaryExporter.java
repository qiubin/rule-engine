package com.ruleengine.nlp;

import com.ruleengine.domain.Dictionary;
import com.ruleengine.domain.DictionaryItem;
import com.ruleengine.repository.DictionaryItemRepository;
import com.ruleengine.repository.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 医学词典导出器：将数据库中的字典表条目导出为 HanLP 自定义词典格式。
 * 应用启动时自动执行，支持按字典编码映射到 HanLP 词性标签。
 *
 * HanLP 自定义词典格式：词语 词性 词频（空格分隔）
 * 示例：发热 nsym 100
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalDictionaryExporter implements ApplicationRunner {

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryItemRepository dictionaryItemRepository;

    // 字典编码 → HanLP 词性标签映射
    private static final Map<String, String> DICT_CODE_TO_NATURE = new HashMap<>();

    static {
        DICT_CODE_TO_NATURE.put("SYMPTOM", "nsym");      // 症状
        DICT_CODE_TO_NATURE.put("SIGN", "nsig");         // 体征
        DICT_CODE_TO_NATURE.put("DRUG", "ndrg");         // 药品
        DICT_CODE_TO_NATURE.put("EXAM", "nexm");         // 检查
        DICT_CODE_TO_NATURE.put("SURGERY", "nsur");      // 手术
        DICT_CODE_TO_NATURE.put("DISEASE", "ndis");      // 疾病
        DICT_CODE_TO_NATURE.put("ICD10", "ndis");        // ICD10 归类为疾病
        DICT_CODE_TO_NATURE.put("NEGATION", "nneg");     // 否定词
    }

    // 输出目录：HanLP 自定义词典路径
    private static final String OUTPUT_DIR = "data/dictionary/custom";

    @Override
    public void run(ApplicationArguments args) {
        exportAll();
    }

    /**
     * 导出所有医学字典到 HanLP 自定义词典文件。
     */
    public void exportAll() {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            log.error("创建词典输出目录失败: {}", OUTPUT_DIR, e);
            return;
        }

        List<Dictionary> dictionaries = dictionaryRepository.findAll();
        int totalExported = 0;

        for (Dictionary dict : dictionaries) {
            String nature = DICT_CODE_TO_NATURE.get(dict.getCode());
            if (nature == null) {
                // 未映射的字典跳过（或尝试用字典编码前缀匹配）
                nature = guessNature(dict.getCode());
                if (nature == null) {
                    log.debug("字典 [{}] 未映射到 HanLP 词性，跳过导出", dict.getCode());
                    continue;
                }
            }

            List<DictionaryItem> items = dictionaryItemRepository.findByDictCode(dict.getCode());
            if (items.isEmpty()) {
                continue;
            }

            int count = exportDictionary(dict.getCode(), dict.getName(), nature, items);
            totalExported += count;
            log.info("导出字典 [{}] → {} 条，词性={}", dict.getCode(), count, nature);
        }

        log.info("医学词典导出完成：共 {} 个字典，{} 个词条", dictionaries.size(), totalExported);

        // 同时导出一个否定词专用文件（供 NLP 否定检测使用）
        exportNegationWords();
    }

    private int exportDictionary(String dictCode, String dictName, String nature, List<DictionaryItem> items) {
        String fileName = dictCode.toLowerCase() + ".txt";
        File file = new File(OUTPUT_DIR, fileName);

        Set<String> uniqueWords = new LinkedHashSet<>();
        for (DictionaryItem item : items) {
            // 优先使用 itemName 作为词典词条
            String word = StringUtils.hasText(item.getItemName()) ? item.getItemName() : item.getItemCode();
            if (StringUtils.hasText(word)) {
                uniqueWords.add(word.trim());
            }
            // 同时导出 itemValue（如果有且与 itemName 不同）
            if (StringUtils.hasText(item.getItemValue()) && !item.getItemValue().equals(item.getItemName())) {
                uniqueWords.add(item.getItemValue().trim());
            }
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("# " + dictName + " (" + dictCode + ")");
            writer.newLine();
            for (String word : uniqueWords) {
                // HanLP 自定义词典格式：词语 词性 词频
                writer.write(word + " " + nature + " 100");
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("导出字典文件失败: {}", file.getAbsolutePath(), e);
            return 0;
        }

        return uniqueWords.size();
    }

    private void exportNegationWords() {
        List<String> negWords = Arrays.asList(
                "无", "否认", "未发现", "未闻及", "未触及", "未引出", "未出现",
                "不存在", "没有", "不见", "未", "非", "不伴", "不伴发",
                "未及", "未查见", "未扪及", "未闻", "未触及"
        );

        File file = new File(OUTPUT_DIR, "negation.txt");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("# 否定词列表");
            writer.newLine();
            for (String word : negWords) {
                writer.write(word + " nneg 100");
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("导出否定词文件失败", e);
        }
    }

    /**
     * 根据字典编码猜测词性（兜底策略）
     */
    private String guessNature(String dictCode) {
        String upper = dictCode.toUpperCase();
        if (upper.contains("SYMPTOM")) return "nsym";
        if (upper.contains("SIGN")) return "nsig";
        if (upper.contains("DRUG") || upper.contains("MEDICINE")) return "ndrg";
        if (upper.contains("EXAM") || upper.contains("CHECK") || upper.contains("INSPECT")) return "nexm";
        if (upper.contains("SURGERY") || upper.contains("OPERATION")) return "nsur";
        if (upper.contains("DISEASE") || upper.contains("DIAGNOSIS") || upper.contains("ICD")) return "ndis";
        if (upper.contains("NEGAT")) return "nneg";
        return null;
    }

    /**
     * 手动触发导出（供管理员接口调用）
     */
    public void reExport() {
        log.info("手动触发医学词典重新导出...");
        exportAll();
    }
}
