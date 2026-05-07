package com.ruleengine.nlp;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;

/**
 * 医学 NLP 服务：基于 HanLP 实现病历文本的实体提取与否定检测。
 * 支持症状、体征、药品、检查、手术等医学实体识别，以及否定词上下文判断。
 */
@Slf4j
@Service
public class NlpService {

    // 否定词列表（中文病历常见否定表达）
    private static final List<String> DEFAULT_NEGATION_WORDS = Arrays.asList(
            "无", "否认", "未发现", "未闻及", "未触及", "未引出", "未出现",
            "不存在", "没有", "不见", "未", "非", "不伴", "不伴发",
            "未及", "未查见", "未扪及", "未闻", "未触及"
    );

    // 医学实体类型标签（HanLP 自定义词性）
    public static final String SYMPTOM = "nsym";      // 症状
    public static final String SIGN = "nsig";         // 体征
    public static final String DRUG = "ndrg";         // 药品
    public static final String EXAM = "nexm";         // 检查
    public static final String SURGERY = "nsur";      // 手术
    public static final String DISEASE = "ndis";      // 疾病

    private final List<String> negationWords = new ArrayList<>(DEFAULT_NEGATION_WORDS);

    /**
     * 初始化：尝试加载自定义词典（如果已生成）
     */
    @PostConstruct
    public void init() {
        try {
            File customDictDir = new File("data/dictionary/custom");
            if (customDictDir.exists() && customDictDir.isDirectory()) {
                log.info("HanLP 自定义词典目录存在，医学 NLP 服务已就绪");
            } else {
                log.info("HanLP 自定义词典目录尚未生成，等待 MedicalDictionaryExporter 导出");
            }
        } catch (Exception e) {
            log.warn("NLP 初始化检查异常: {}", e.getMessage());
        }
    }

    /**
     * 医学实体提取：从病历文本中提取症状、体征、药品、检查、手术等实体。
     *
     * @param text 病历文本（如主诉、病程记录）
     * @return Map，key 为实体类型（symptoms/signs/drugs/exams/surgeries/diseases），value 为实体列表
     */
    public Map<String, List<String>> medicalNer(String text) {
        Map<String, List<String>> result = new HashMap<>();
        result.put("symptoms", new ArrayList<>());
        result.put("signs", new ArrayList<>());
        result.put("drugs", new ArrayList<>());
        result.put("exams", new ArrayList<>());
        result.put("surgeries", new ArrayList<>());
        result.put("diseases", new ArrayList<>());

        if (!StringUtils.hasText(text)) {
            return result;
        }

        try {
            List<Term> terms = HanLP.segment(text);
            for (Term term : terms) {
                String word = term.word;
                String nature = term.nature != null ? term.nature.toString() : "";
                switch (nature) {
                    case SYMPTOM:
                        result.get("symptoms").add(word);
                        break;
                    case SIGN:
                        result.get("signs").add(word);
                        break;
                    case DRUG:
                        result.get("drugs").add(word);
                        break;
                    case EXAM:
                        result.get("exams").add(word);
                        break;
                    case SURGERY:
                        result.get("surgeries").add(word);
                        break;
                    case DISEASE:
                        result.get("diseases").add(word);
                        break;
                    default:
                        // 同时尝试用 HanLP 内置的词性做兜底
                        if ("nsym".equals(nature) || nature.contains("sym")) {
                            result.get("symptoms").add(word);
                        }
                }
            }
        } catch (Exception e) {
            log.error("医学实体提取失败: text={}", text.substring(0, Math.min(text.length(), 100)), e);
        }

        // 去重
        for (Map.Entry<String, List<String>> entry : result.entrySet()) {
            List<String> unique = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            entry.setValue(unique);
        }

        return result;
    }

    /**
     * 否定检测：判断文本中某个实体是否处于否定语境中。
     * 扫描实体前 N 个字符的窗口，查找否定词。
     *
     * @param text   病历文本
     * @param entity 待检测实体
     * @return true 表示实体被否定（如"无发热"），false 表示未被否定
     */
    public boolean isNegated(String text, String entity) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(entity)) {
            return false;
        }

        int index = text.indexOf(entity);
        if (index < 0) {
            return false;
        }

        // 扫描实体前的上下文窗口（默认15个字符）
        int windowStart = Math.max(0, index - 15);
        String prefix = text.substring(windowStart, index);

        for (String negWord : negationWords) {
            if (prefix.contains(negWord)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量否定检测：对文本中提取的所有实体进行否定判断。
     *
     * @param text 病历文本
     * @return Map，key 为实体类型 + "_positive"/"_negative"，value 为实体列表
     */
    public Map<String, List<String>> medicalNerWithNegation(String text) {
        Map<String, List<String>> entities = medicalNer(text);
        Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : entities.entrySet()) {
            String type = entry.getKey(); // symptoms, signs, etc.
            List<String> positive = new ArrayList<>();
            List<String> negative = new ArrayList<>();

            for (String entity : entry.getValue()) {
                if (isNegated(text, entity)) {
                    negative.add(entity);
                } else {
                    positive.add(entity);
                }
            }

            result.put(type + "_positive", positive);
            result.put(type + "_negative", negative);
        }

        return result;
    }

    /**
     * 文本分词：基于 HanLP 的标准分词。
     *
     * @param text 输入文本
     * @return 分词后的词语列表
     */
    public List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<Term> terms = HanLP.segment(text);
        List<String> tokens = new ArrayList<>();
        for (Term term : terms) {
            String word = term.word.trim();
            if (word.length() > 0) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * 基于 HanLP 分词的 Jaccard 相似度计算。
     *
     * @param a 文本 A
     * @param b 文本 B
     * @return Jaccard 相似度 [0, 1]
     */
    public double tokenJaccard(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return 0.0;
        }
        Set<String> sa = new HashSet<>(tokenize(a));
        Set<String> sb = new HashSet<>(tokenize(b));
        if (sa.isEmpty() && sb.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * 更新否定词列表（支持动态配置）
     */
    public void updateNegationWords(List<String> words) {
        negationWords.clear();
        negationWords.addAll(words);
    }

    public List<String> getNegationWords() {
        return new ArrayList<>(negationWords);
    }
}
