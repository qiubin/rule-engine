package com.ruleengine.script;

import com.ruleengine.domain.DictionaryItem;
import com.ruleengine.repository.DictionaryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 字典脚本服务：支持在规则执行时动态查询字典内容进行匹配。
 * 解决字典项过大（如ICD10）无法直接存储在规则配置中的问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictScriptService {

    private final DictionaryItemRepository dictionaryItemRepository;

    /**
     * 根据字典编码和属性名获取所有启用的词条列表
     *
     * @param dictCode  字典编码
     * @param attr      属性名：itemName(名称) / itemCode(编码) / itemValue(值)
     * @return 指定属性的列表
     */
    public List<String> getDictItemsByAttr(String dictCode, String attr) {
        if (!StringUtils.hasText(dictCode)) {
            return Collections.emptyList();
        }
        String property = StringUtils.hasText(attr) ? attr.trim() : "itemName";
        try {
            List<DictionaryItem> items = dictionaryItemRepository.findByDictCode(dictCode);
            List<String> result = items.stream()
                    .filter(item -> item.getStatus() != null && item.getStatus().name().equals("ENABLED"))
                    .map(item -> {
                        switch (property) {
                            case "itemCode": return item.getItemCode();
                            case "itemValue": return item.getItemValue();
                            case "itemName":
                            default: return item.getItemName();
                        }
                    })
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            log.info("字典查询: dictCode={}, attr={}, 总条目={}, 有效条目={}, 前5条={}",
                    dictCode, property, items.size(), result.size(),
                    result.stream().limit(5).collect(Collectors.toList()));
            return result;
        } catch (Exception e) {
            log.error("查询字典失败: dictCode={}, attr={}", dictCode, property, e);
            return Collections.emptyList();
        }
    }

    /**
     * 兼容旧方法：默认获取 itemName
     */
    public List<String> getItemNamesByDictCode(String dictCode) {
        return getDictItemsByAttr(dictCode, "itemName");
    }

    /**
     * 白名单匹配（字典版）：校验输入字符串中只含有指定字典中的关键词。
     *
     * @param input          待校验的字符串
     * @param allowedDictCode 允许的关键词所在字典编码
     * @param allDictCode    全部关键词所在字典编码（用于识别文本中出现的所有关键词；若为空，则只做正向包含检查）
     * @param dictAttr       匹配字典的属性：itemName(名称) / itemCode(编码) / itemValue(值)
     * @return 输入中只含有允许的关键词时返回 TRUE
     */
    public boolean whitelistMatch(String input, String allowedDictCode, String allDictCode, String dictAttr) {
        if (!StringUtils.hasText(input) || !StringUtils.hasText(allowedDictCode)) {
            return false;
        }

        List<String> allowed = getDictItemsByAttr(allowedDictCode, dictAttr);
        if (allowed.isEmpty()) {
            log.warn("白名单字典为空: dictCode={}", allowedDictCode);
            return false;
        }

        log.debug("白名单字典匹配开始：输入='{}', 允许字典='{}'({}条), 全部字典='{}', 属性='{}'"
            , input, allowedDictCode, allowed.size(), allDictCode, dictAttr);

        List<String> all = StringUtils.hasText(allDictCode)
                ? getDictItemsByAttr(allDictCode, dictAttr)
                : new ArrayList<>(allowed);

        if (all.isEmpty()) {
            log.warn("全部字典为空: dictCode={}", allDictCode);
            return false;
        }

        // 找出输入中匹配到的所有关键词
        Set<String> foundInInput = new HashSet<>();
        for (String keyword : all) {
            if (input.contains(keyword)) {
                foundInInput.add(keyword);
            }
        }

        log.info("白名单匹配详情: 输入='{}', 属性='{}', 匹配到 {} 个关键词: {}", input, dictAttr, foundInInput.size(), foundInInput);

        if (foundInInput.isEmpty()) {
            log.info("白名单字典校验失败：输入中未匹配到任何关键词。输入='{}', 属性='{}', 字典条目数={}", input, dictAttr, all.size());
            return false;
        }

        // 必须至少匹配到一个允许的关键词
        boolean hasAllowed = foundInInput.stream().anyMatch(found ->
                allowed.stream().anyMatch(a -> a.equals(found))
        );
        if (!hasAllowed) {
            log.debug("白名单字典校验失败：输入中未包含任何允许的关键词。匹配到的词：{}, 允许列表大小：{}",
                    foundInInput, allowed.size());
            return false;
        }

        // 若提供了全部字典，则严格检查是否有不允许的关键词
        if (StringUtils.hasText(allDictCode)) {
            for (String found : foundInInput) {
                boolean isAllowed = allowed.stream().anyMatch(a -> a.equals(found));
                if (!isAllowed) {
                    log.info("白名单字典校验失败：发现不允许的关键词 '{}'，输入='{}'", found, input);
                    return false;
                }
            }
        }

        log.debug("白名单字典校验通过：输入='{}'", input);
        return true;
    }

    /**
     * 存在性冲突校验（字典版）：检查两个字段对同一字典中的关键词是否存在矛盾。
     *
     * @param strA     第一个字符串
     * @param strB     第二个字符串
     * @param dictCode 冲突关键词所在字典编码
     * @param dictAttr 匹配字典的属性：itemName(名称) / itemCode(编码) / itemValue(值)
     * @return 存在冲突时返回 TRUE
     */
    public boolean existenceConflict(String strA, String strB, String dictCode, String dictAttr) {
        if (!StringUtils.hasText(strA) || !StringUtils.hasText(strB) || !StringUtils.hasText(dictCode)) {
            return false;
        }

        List<String> keys = getDictItemsByAttr(dictCode, dictAttr);
        if (keys.isEmpty()) {
            log.warn("冲突字典为空: dictCode={}", dictCode);
            return false;
        }

        for (String key : keys) {
            boolean aHas = strA.contains(key);
            boolean bHas = strB.contains(key);

            if (aHas != bHas) {
                log.debug("存在性冲突(字典)：关键字 '{}', A包含={}, B包含={}", key, aHas, bHas);
                return true;
            }
        }

        return false;
    }

    /**
     * 矛盾判断（字典版）
     *
     * @param str1             第一个字符串
     * @param str2             第二个字符串
     * @param negativeDictCode 否定词字典编码
     * @param negDictAttr      否定词字典属性：itemName(名称) / itemCode(编码) / itemValue(值)
     * @param keywordDictCode  关键字字典编码
     * @param keywordDictAttr  关键字字典属性：itemName(名称) / itemCode(编码) / itemValue(值)
     */
    public boolean contradictionCheck(String str1, String str2, String negativeDictCode, String negDictAttr,
                                       String keywordDictCode, String keywordDictAttr) {
        if (!StringUtils.hasText(str1) || !StringUtils.hasText(str2)) {
            return false;
        }

        List<String> negWords = StringUtils.hasText(negativeDictCode)
                ? getDictItemsByAttr(negativeDictCode, negDictAttr)
                : Arrays.asList("无", "否认", "未发现");

        List<String> keys = StringUtils.hasText(keywordDictCode)
                ? getDictItemsByAttr(keywordDictCode, keywordDictAttr)
                : Collections.emptyList();

        if (keys.isEmpty()) return false;

        for (String key : keys) {
            String k = key.trim();
            if (!StringUtils.hasText(k)) continue;

            boolean str1Negative = containsNegativeContext(str1, k, negWords);
            boolean str1Positive = str1.contains(k) && !str1Negative;
            boolean str2Negative = containsNegativeContext(str2, k, negWords);
            boolean str2Positive = str2.contains(k) && !str2Negative;

            if ((str1Positive && str2Negative) || (str1Negative && str2Positive)) {
                log.debug("发现矛盾信息(字典)：针对关键字 '{}', str1='{}', str2='{}'", k, str1, str2);
                return true;
            }
        }
        return false;
    }

    private static boolean containsNegativeContext(String text, String keyword, List<String> negativeWords) {
        if (!text.contains(keyword)) {
            return false;
        }
        int keyIndex = text.indexOf(keyword);
        String prefixText = text.substring(Math.max(0, keyIndex - 10), keyIndex);
        for (String negWord : negativeWords) {
            if (prefixText.contains(negWord.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 多条件正则匹配（字典版）：抗菌药物集合从字典获取
     *
     * @param antiBioticsDictCode 抗菌药物字典编码
     * @param dictAttr            匹配字典的属性：itemName(名称) / itemCode(编码) / itemValue(值)
     * @param doctorsOrder        医嘱内容
     * @param courseRecord        病程记录
     */
    public boolean multiConditionRegexMatch(String antiBioticsDictCode, String dictAttr, String doctorsOrder, String courseRecord) {
        if (!StringUtils.hasText(antiBioticsDictCode) || !StringUtils.hasText(doctorsOrder)) {
            return false;
        }

        List<String> antibiotics = getDictItemsByAttr(antiBioticsDictCode, dictAttr);
        if (antibiotics.isEmpty()) {
            log.warn("抗菌药物字典为空: dictCode={}", antiBioticsDictCode);
            return false;
        }

        String matchedAntibiotic = null;
        for (String antibiotic : antibiotics) {
            if (doctorsOrder.contains(antibiotic.trim())) {
                matchedAntibiotic = antibiotic.trim();
                break;
            }
        }

        if (matchedAntibiotic == null) {
            return false;
        }

        if (!StringUtils.hasText(courseRecord) || !courseRecord.contains(matchedAntibiotic)) {
            return true;
        }

        return false;
    }

    /**
     * 单正则匹配（字典版）：将字典词条拼接成正则进行匹配
     *
     * @param input    输入字符串
     * @param dictCode 字典编码
     * @param dictAttr 匹配字典的属性：itemName(名称) / itemCode(编码) / itemValue(值)
     */
    public boolean regexMatch(String input, String dictCode, String dictAttr) {
        if (!StringUtils.hasText(input) || !StringUtils.hasText(dictCode)) {
            return true;
        }

        List<String> patterns = getDictItemsByAttr(dictCode, dictAttr);
        if (patterns.isEmpty()) {
            return false;
        }

        for (String patternStr : patterns) {
            if (!StringUtils.hasText(patternStr)) continue;
            try {
                if (input.matches(patternStr)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("正则表达式无效: {}", patternStr);
            }
        }
        return false;
    }
}
