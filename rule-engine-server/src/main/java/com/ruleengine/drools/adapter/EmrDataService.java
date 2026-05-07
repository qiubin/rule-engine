package com.ruleengine.drools.adapter;

import com.ruleengine.nlp.NlpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 病历数据获取服务：从 HIS 系统拉取患者全量病历数据，
 * 按数据元 camelName 平铺为 Map，供规则引擎 $param 使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmrDataService {

    private final HisClient hisClient;
    private final NlpService nlpService;

    /**
     * 根据患者 ID 获取完整病历数据（含当前住院、病历章节、医嘱、病程、上一份住院）。
     *
     * @param patientId    患者标识
     * @param admissionId  住院号（可选，不传则自动从当前住院信息中获取）
     * @return 按 camelName 平铺的数据 Map
     */
    public Map<String, Object> fetchPatientData(String patientId, String admissionId) {
        Map<String, Object> data = new HashMap<>();
        if (!hisClient.isEnabled()) {
            log.debug("HIS 客户端未启用，跳过数据获取");
            return data;
        }
        if (!StringUtils.hasText(patientId)) {
            return data;
        }

        try {
            // 1. 当前住院信息
            Map<String, Object> admission = hisClient.getCurrentAdmission(patientId);
            if (admission != null && !admission.isEmpty()) {
                data.putAll(flattenMap(admission));
                // 如果没传入 admissionId，从当前住院信息中取
                if (!StringUtils.hasText(admissionId)) {
                    Object admIdObj = admission.get("admissionId");
                    if (admIdObj != null) {
                        admissionId = String.valueOf(admIdObj);
                    }
                }
            }

            // 2. 病历各章节（需要 admissionId）
            if (StringUtils.hasText(admissionId)) {
                Map<String, Object> sections = hisClient.getEmrSections(admissionId);
                if (sections != null) {
                    data.putAll(flattenMap(sections));
                }

                // 3. 医嘱（全部，时间窗由规则侧过滤）
                List<Map<String, Object>> orders = hisClient.getOrders(admissionId, null, null);
                if (orders != null) {
                    data.put("orders", orders);
                    // 同时提供纯药品名列表，方便规则直接 contains 判断
                    List<String> drugNames = extractDrugNames(orders);
                    data.put("drugNames", drugNames);
                }

                // 4. 抢救记录标志
                boolean hasRescue = hisClient.hasRescueRecord(admissionId);
                data.put("hasRescueRecord", hasRescue);

                // 5. 病程记录
                List<Map<String, Object>> courseRecords = hisClient.getCourseRecords(admissionId);
                if (courseRecords != null) {
                    data.put("courseRecords", courseRecords);
                    // 分类提取：首次病程 / 日常病程 / 上级查房 / 术后
                    extractCourseByType(courseRecords, data);
                }

                // 6. 上一份住院（跨病历比对）
                Map<String, Object> prevAdmission = hisClient.getPreviousAdmission(patientId, admissionId);
                if (prevAdmission != null && !prevAdmission.isEmpty()) {
                    // 前缀 previous_ 避免字段冲突
                    Map<String, Object> prevFlat = flattenMap(prevAdmission);
                    Map<String, Object> prefixed = new HashMap<>();
                    for (Map.Entry<String, Object> e : prevFlat.entrySet()) {
                        prefixed.put("previous_" + e.getKey(), e.getValue());
                    }
                    data.putAll(prefixed);
                    data.put("previousAdmission", prevAdmission);
                }
            }

            // 7. NLP 处理：对病程记录进行医学实体提取和否定检测
            try {
                StringBuilder allText = new StringBuilder();
                if (data.containsKey("firstCourseRecord")) {
                    allText.append(data.get("firstCourseRecord")).append(" ");
                }
                if (data.containsKey("latestDailyCourseRecord")) {
                    allText.append(data.get("latestDailyCourseRecord")).append(" ");
                }
                if (data.containsKey("latestSuperiorVisitRecord")) {
                    allText.append(data.get("latestSuperiorVisitRecord")).append(" ");
                }
                // 拼接所有病程记录文本
                List<Map<String, Object>> courseRecords = (List<Map<String, Object>>) data.get("courseRecords");
                if (courseRecords != null) {
                    for (Map<String, Object> record : courseRecords) {
                        Object content = record.get("content");
                        if (content != null) {
                            allText.append(content).append(" ");
                        }
                    }
                }

                String combinedText = allText.toString().trim();
                if (StringUtils.hasText(combinedText)) {
                    Map<String, List<String>> nerResult = nlpService.medicalNerWithNegation(combinedText);
                    data.put("positiveSymptoms", nerResult.getOrDefault("symptoms_positive", Collections.emptyList()));
                    data.put("negativeSymptoms", nerResult.getOrDefault("symptoms_negative", Collections.emptyList()));
                    data.put("positiveSigns", nerResult.getOrDefault("signs_positive", Collections.emptyList()));
                    data.put("negativeSigns", nerResult.getOrDefault("signs_negative", Collections.emptyList()));
                    data.put("positiveDrugs", nerResult.getOrDefault("drugs_positive", Collections.emptyList()));
                    data.put("negativeDrugs", nerResult.getOrDefault("drugs_negative", Collections.emptyList()));
                    data.put("positiveExams", nerResult.getOrDefault("exams_positive", Collections.emptyList()));
                    data.put("negativeExams", nerResult.getOrDefault("exams_negative", Collections.emptyList()));
                    data.put("positiveSurgeries", nerResult.getOrDefault("surgeries_positive", Collections.emptyList()));
                    data.put("negativeSurgeries", nerResult.getOrDefault("surgeries_negative", Collections.emptyList()));
                    data.put("positiveDiseases", nerResult.getOrDefault("diseases_positive", Collections.emptyList()));
                    data.put("negativeDiseases", nerResult.getOrDefault("diseases_negative", Collections.emptyList()));
                    log.debug("NLP 处理完成: 阳性症状={}, 阴性症状={}, 阳性体征={}",
                            data.get("positiveSymptoms"), data.get("negativeSymptoms"), data.get("positiveSigns"));
                }
            } catch (Exception nlpEx) {
                log.warn("NLP 处理异常，跳过: {}", nlpEx.getMessage());
            }

            log.info("病历数据获取完成: patientId={}, 字段数={}, admissionId={}",
                    patientId, data.size(), admissionId);
        } catch (Exception e) {
            log.error("病历数据获取异常: patientId={}", patientId, e);
        }

        return data;
    }

    /**
     * 平铺 Map：将嵌套对象递归展开为单层 camelCase 键值对。
     * 目前只做单层展开，数组和深层嵌套保持原样。
     */
    private Map<String, Object> flattenMap(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>();
        if (source == null) return result;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // 跳过 null 值，保留空字符串（区分"字段存在但为空"和"字段不存在"）
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractDrugNames(List<Map<String, Object>> orders) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            Object category = order.get("category");
            if ("DRUG".equals(category) || "药品".equals(category)) {
                Object name = order.get("orderName");
                if (name != null) {
                    names.add(String.valueOf(name));
                }
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private void extractCourseByType(List<Map<String, Object>> courseRecords, Map<String, Object> data) {
        List<String> firstCourses = new ArrayList<>();
        List<String> dailyCourses = new ArrayList<>();
        List<String> superiorVisits = new ArrayList<>();
        List<String> postOpCourses = new ArrayList<>();
        List<String> criticalCourses = new ArrayList<>();

        for (Map<String, Object> record : courseRecords) {
            Object type = record.get("recordType");
            Object content = record.get("content");
            String text = content != null ? String.valueOf(content) : "";
            if (type == null) continue;
            String t = String.valueOf(type);
            switch (t) {
                case "FIRST_COURSE":
                    firstCourses.add(text);
                    break;
                case "DAILY_COURSE":
                    dailyCourses.add(text);
                    break;
                case "SUPERIOR_VISIT":
                    superiorVisits.add(text);
                    break;
                case "POST_OP":
                    postOpCourses.add(text);
                    break;
                case "CRITICAL":
                    criticalCourses.add(text);
                    break;
                default:
                    // 其他类型不单独分类
            }
        }

        // 提供单值（首个）和数组两种形式
        if (!firstCourses.isEmpty()) {
            data.put("firstCourseRecord", firstCourses.get(0));
            data.put("firstCourseRecords", firstCourses);
        }
        if (!dailyCourses.isEmpty()) {
            data.put("latestDailyCourseRecord", dailyCourses.get(dailyCourses.size() - 1));
            data.put("dailyCourseRecords", dailyCourses);
        }
        if (!superiorVisits.isEmpty()) {
            data.put("latestSuperiorVisitRecord", superiorVisits.get(superiorVisits.size() - 1));
            data.put("superiorVisitRecords", superiorVisits);
        }
        if (!postOpCourses.isEmpty()) {
            data.put("latestPostOpCourseRecord", postOpCourses.get(postOpCourses.size() - 1));
            data.put("postOpCourseRecords", postOpCourses);
        }
        if (!criticalCourses.isEmpty()) {
            data.put("latestCriticalCourseRecord", criticalCourses.get(criticalCourses.size() - 1));
            data.put("criticalCourseRecords", criticalCourses);
        }
    }
}
