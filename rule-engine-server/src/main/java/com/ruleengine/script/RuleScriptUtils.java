package com.ruleengine.script;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则引擎业务处理脚本/工具类
 * 封装了正则匹配、矛盾判断、数据判断和时间判断等核心方法，供规则执行时调用。
 */
@Slf4j
public class RuleScriptUtils {

    /**
     * 1. 正则匹配：基于输入的1个字符串，判断是否命中，命中时返回TRUE。
     * 应用场景：诱因正则匹配，描述不清返回TRUE；缺症状时间描述，描述不清返回TRUE；
     *
     * @param input 待匹配的字符串
     * @param regex 匹配规则（正则表达式）
     * @return 命中正则，或输入为空（即描述不清/缺失）时返回 TRUE
     */
    public static boolean regexMatch(String input, String regex) {
        if (!StringUtils.hasText(input)) {
            // 描述不清/缺描述返回 TRUE
            return true;
        }
        if (!StringUtils.hasText(regex)) {
            return false;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }

    /**
     * 2. 正则匹配（多条件）：基于条件1的字符串与条件2的字符串计算结果，再与条件3的字符串判断是否命中。
     * 应用场景：抗菌药物匹配。条件1（抗菌药物集合）与条件2（医嘱药物）匹配出使用的药物，
     * 再与条件3（病程记录）匹配是否描述。未描述返回 TRUE。
     *
     * @param antiBioticsSet  条件1：抗菌药物集合（可通过逗号分隔的字符串传入）
     * @param doctorsOrder    条件2：医嘱中的药物字符串
     * @param courseRecord    条件3：病程记录字符串
     * @return 医嘱中使用了抗菌药物但病程记录中未描述，返回 TRUE
     */
    public static boolean multiConditionRegexMatch(String antiBioticsSet, String doctorsOrder, String courseRecord) {
        if (!StringUtils.hasText(antiBioticsSet) || !StringUtils.hasText(doctorsOrder)) {
            return false; // 缺少基础匹配条件
        }

        // 1. 从抗菌药物集合中提取出医嘱里实际使用的抗菌药物
        List<String> antibiotics = Arrays.asList(antiBioticsSet.split("[,|，]"));
        String matchedAntibiotic = null;
        for (String antibiotic : antibiotics) {
            if (doctorsOrder.contains(antibiotic.trim())) {
                matchedAntibiotic = antibiotic.trim();
                break;
            }
        }

        // 2. 如果医嘱中没有使用抗菌药物，则不需要进行后续判断
        if (matchedAntibiotic == null) {
            return false;
        }

        // 3. 判断条件3（病程记录）中是否包含了该抗菌药物记录
        // 如果病程记录为空，或者未包含该抗菌药物的相关描述，返回 TRUE（表示"未描述"）
        if (!StringUtils.hasText(courseRecord) || !courseRecord.contains(matchedAntibiotic)) {
            return true;
        }

        return false;
    }

    /**
     * 3. 矛盾判断：基于输入的2个字符串，判断是否存在矛盾关系，命中时返回TRUE。
     * 应用场景：个人史描述与既往诊断存在矛盾；过敏史与既往史描述矛盾；门诊主诉与入院主诉症状矛盾。
     * 
     * @param str1 第一个字符串（如：既往史/门诊主诉）
     * @param str2 第二个字符串（如：过敏史/入院主诉）
     * @param negativeWords 否定词表（如："无", "否认", "未发现"），多词用逗号分隔
     * @param keywords 需要比对的关键症状或疾病词汇集合（逗号分隔）
     * @return 存在矛盾关系时返回 TRUE
     */
    public static boolean contradictionCheck(String str1, String str2, String negativeWords, String keywords) {
        if (!StringUtils.hasText(str1) || !StringUtils.hasText(str2)) {
            return false;
        }
        
        List<String> negWords = Arrays.asList(negativeWords.split("[,|，]"));
        List<String> keys = Arrays.asList(keywords.split("[,|，]"));

        for (String key : keys) {
            String k = key.trim();
            if (!StringUtils.hasText(k)) continue;

            // 判断 str1 中是否否定了关键信息
            boolean str1Negative = containsNegativeContext(str1, k, negWords);
            // 判断 str1 中是否肯定了关键信息
            boolean str1Positive = str1.contains(k) && !str1Negative;

            // 判断 str2 中是否否定了关键信息
            boolean str2Negative = containsNegativeContext(str2, k, negWords);
            // 判断 str2 中是否肯定了关键信息
            boolean str2Positive = str2.contains(k) && !str2Negative;

            // 矛盾情况1：str1 肯定但 str2 否定，或矛盾情况2：str1 否定但 str2 肯定
            if ((str1Positive && str2Negative) || (str1Negative && str2Positive)) {
                log.debug("发现矛盾信息：针对关键字 '{}', str1='{}', str2='{}'", k, str1, str2);
                return true;
            }
        }
        return false;
    }

    /**
     * 辅助方法：判断文本中是否对目标词汇包含否定语境。
     */
    private static boolean containsNegativeContext(String text, String keyword, List<String> negativeWords) {
        if (!text.contains(keyword)) {
            return false;
        }
        // 简单策略：目标词汇前面一定字符范围内是否存在否定词
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
     * 4. 数据判断：判断输入的1个数据大小，命中时返回TRUE。
     * 
     * @param inputData 输入的数据
     * @param operator 运算符（如 ">", "<", "==", ">=", "<="）
     * @param threshold 比较的阈值
     * @return 命中条件时返回 TRUE
     */
    public static boolean dataCheck(Double inputData, String operator, Double threshold) {
        if (inputData == null || threshold == null || !StringUtils.hasText(operator)) {
            return false;
        }

        switch (operator.trim()) {
            case ">":
                return inputData > threshold;
            case "<":
                return inputData < threshold;
            case "==":
            case "=":
                return Double.compare(inputData, threshold) == 0;
            case ">=":
                return inputData >= threshold;
            case "<=":
                return inputData <= threshold;
            case "!=":
                return Double.compare(inputData, threshold) != 0;
            default:
                log.warn("不支持的操作符: {}", operator);
                return false;
        }
    }

    /**
     * 5. 时间判断：判断输入的时间是否在设定的时间范围内，命中时返回TRUE。
     *
     * @param targetTime 输入的目标时间字符串（如 "2024-05-01 10:00:00"）
     * @param baseTime 基准时间字符串（如 "2024-05-01 08:00:00"）
     * @param minHours 最小相差小时数（如果为 null，则不设下限）
     * @param maxHours 最大相差小时数（如果为 null，则不设上限）
     * @return 在时间范围内返回 TRUE
     */
    public static boolean timeCheck(String targetTime, String baseTime, Integer minHours, Integer maxHours) {
        if (!StringUtils.hasText(targetTime) || !StringUtils.hasText(baseTime)) {
            return false;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime target = LocalDateTime.parse(targetTime, formatter);
            LocalDateTime base = LocalDateTime.parse(baseTime, formatter);

            // 计算时间差（以小时为单位）
            long diffHours = Math.abs(ChronoUnit.HOURS.between(base, target));

            boolean minCondition = (minHours == null) || (diffHours >= minHours);
            boolean maxCondition = (maxHours == null) || (diffHours <= maxHours);

            return minCondition && maxCondition;
        } catch (Exception e) {
            log.error("时间判断解析错误, targetTime: {}, baseTime: {}", targetTime, baseTime, e);
            return false; // 解析错误返回 false
        }
    }

}
