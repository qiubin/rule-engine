package com.ruleengine.drools.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HIS 系统 REST 客户端
 * 封装认证、超时、重试、字段映射等逻辑。
 */
@Slf4j
@Component
public class HisClient {

    @Value("${his.enabled:false}")
    private boolean enabled;

    @Value("${his.base-url:}")
    private String baseUrl;

    @Value("${his.auth-type:none}")
    private String authType;

    @Value("${his.auth-token:}")
    private String authToken;

    @Value("${his.api-key:}")
    private String apiKey;

    @Value("${his.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${his.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${his.adapter-path:/api/v1/adapter/emr}")
    private String adapterPath;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("HIS 客户端已启用，baseUrl={}", baseUrl);
        } else {
            log.info("HIS 客户端未启用，规则执行将仅依赖传入参数");
        }
    }

    public boolean isEnabled() {
        return enabled && StringUtils.hasText(baseUrl);
    }

    /**
     * 获取当前住院信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCurrentAdmission(String patientId) {
        String url = baseUrl + "/v1/patient/" + patientId + "/current-admission";
        return getForMap(url, "当前住院信息");
    }

    /**
     * 获取病历各章节文本
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getEmrSections(String admissionId) {
        String url = baseUrl + "/v1/admission/" + admissionId + "/emr-sections";
        return getForMap(url, "病历章节");
    }

    /**
     * 获取医嘱数据（支持时间窗）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOrders(String admissionId, String startTime, String endTime) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/admission/" + admissionId + "/orders");
        boolean hasParam = false;
        if (StringUtils.hasText(startTime)) {
            url.append(hasParam ? "&" : "?").append("startTime=").append(startTime);
            hasParam = true;
        }
        if (StringUtils.hasText(endTime)) {
            url.append(hasParam ? "&" : "?").append("endTime=").append(endTime);
        }
        Map<String, Object> result = getForMap(url.toString(), "医嘱数据");
        if (result != null && result.get("orders") instanceof List) {
            return (List<Map<String, Object>>) result.get("orders");
        }
        return Collections.emptyList();
    }

    /**
     * 获取抢救记录
     */
    public boolean hasRescueRecord(String admissionId) {
        String url = baseUrl + "/v1/admission/" + admissionId + "/rescue-records";
        Map<String, Object> result = getForMap(url, "抢救记录");
        if (result != null) {
            Object hasRescue = result.get("hasRescue");
            return Boolean.TRUE.equals(hasRescue) || "true".equals(String.valueOf(hasRescue));
        }
        return false;
    }

    /**
     * 获取上一份住院病历
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPreviousAdmission(String patientId, String currentAdmissionId) {
        String url = baseUrl + "/v1/patient/" + patientId + "/previous-admission";
        if (StringUtils.hasText(currentAdmissionId)) {
            url += "?currentAdmissionId=" + currentAdmissionId;
        }
        return getForMap(url, "上一份住院");
    }

    /**
     * 获取病程记录列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCourseRecords(String admissionId) {
        String url = baseUrl + "/v1/admission/" + admissionId + "/course-records";
        Map<String, Object> result = getForMap(url, "病程记录");
        if (result != null && result.get("courseRecords") instanceof List) {
            return (List<Map<String, Object>>) result.get("courseRecords");
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getForMap(String url, String desc) {
        if (!isEnabled()) {
            return Collections.emptyMap();
        }
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode codeNode = root.get("code");
                int code = codeNode != null ? codeNode.asInt() : 200;
                if (code == 200) {
                    JsonNode dataNode = root.get("data");
                    if (dataNode != null && dataNode.isObject()) {
                        return objectMapper.convertValue(dataNode, Map.class);
                    }
                } else {
                    JsonNode msgNode = root.get("message");
                    log.warn("HIS 返回错误 [{}]: code={}, msg={}", desc, code, msgNode != null ? msgNode.asText() : "");
                }
            }
        } catch (Exception e) {
            log.error("HIS 接口调用失败 [{}]: {}", desc, e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 从适配器获取病历数据（POST 请求，datasetList 结构）。
     *
     * @param patientId       患者标识
     * @param admissionId     住院号
     * @param visitId         就诊号
     * @param medicalRecordNo 病历号
     * @param eventNo         事件号
     * @param identityCardNo  身份证号
     * @return 平铺后的字段 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchEmrDataFromAdapter(String patientId, String admissionId,
                                                        String visitId, String medicalRecordNo,
                                                        String eventNo, String identityCardNo) {
        if (!isEnabled()) {
            return Collections.emptyMap();
        }

        String url = baseUrl + adapterPath;

        Map<String, Object> capability = new HashMap<>();
        Map<String, Object> selection = new HashMap<>();
        selection.put("field", Arrays.asList("patientBasicInfo", "firstCourseRecord", "dailyCourseRecord",
                "superiorVisitRecord", "operationRecord", "rescueRecord", "deathRecord",
                "criticalValueRecord", "transfusionRecord", "consultationRecord",
                "handoverRecord", "transferRecord", "difficultCaseRecord",
                "preoperativeDiscussion", "postoperativeCourseRecord", "emrSection"));
        capability.put("selection", selection);

        Map<String, Object> params = new HashMap<>();
        if (StringUtils.hasText(patientId)) params.put("patientId", patientId);
        if (StringUtils.hasText(admissionId)) params.put("admissionId", admissionId);
        if (StringUtils.hasText(visitId)) params.put("visitId", visitId);
        if (StringUtils.hasText(medicalRecordNo)) params.put("medicalRecordNo", medicalRecordNo);
        if (StringUtils.hasText(eventNo)) params.put("eventNo", eventNo);
        if (StringUtils.hasText(identityCardNo)) params.put("identityCardNo", identityCardNo);

        Map<String, Object> body = new HashMap<>();
        body.put("capability", capability);
        body.put("params", params);

        Map<String, Object> response = postForMap(url, body, "适配器病历数据");
        if (response == null || response.isEmpty()) {
            return Collections.emptyMap();
        }

        return flattenDatasetList(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(String url, Map<String, Object> body, String desc) {
        if (!isEnabled()) {
            return Collections.emptyMap();
        }
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode codeNode = root.get("code");
                String code = codeNode != null ? codeNode.asText() : "";
                if ("AA".equals(code)) {
                    JsonNode dataNode = root.get("data");
                    if (dataNode != null && dataNode.isObject()) {
                        return objectMapper.convertValue(dataNode, Map.class);
                    }
                } else {
                    JsonNode msgNode = root.get("message");
                    log.warn("适配器返回错误 [{}]: code={}, msg={}", desc, code, msgNode != null ? msgNode.asText() : "");
                }
            }
        } catch (Exception e) {
            log.error("适配器接口调用失败 [{}]: {}", desc, e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 将适配器返回的 datasetList 结构平铺为单层 Map。
     * datasetList[0].{datasetKey}[0] 的字段直接提取到顶层。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenDatasetList(Map<String, Object> responseData) {
        Map<String, Object> result = new HashMap<>();
        if (responseData == null) return result;

        Object datasetListObj = responseData.get("datasetList");
        if (!(datasetListObj instanceof List)) return result;

        List<Map<String, Object>> datasetList = (List<Map<String, Object>>) datasetListObj;
        if (datasetList.isEmpty()) return result;

        Map<String, Object> firstDataset = datasetList.get(0);
        if (firstDataset == null) return result;

        for (Map.Entry<String, Object> entry : firstDataset.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) value;
                if (!records.isEmpty()) {
                    Map<String, Object> firstRecord = records.get(0);
                    if (firstRecord != null) {
                        for (Map.Entry<String, Object> field : firstRecord.entrySet()) {
                            if (field.getValue() != null) {
                                result.put(field.getKey(), field.getValue());
                            }
                        }
                    }
                }
            } else if (value != null) {
                result.put(entry.getKey(), value);
            }
        }

        return result;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if ("bearer".equalsIgnoreCase(authType) && StringUtils.hasText(authToken)) {
            headers.setBearerAuth(authToken);
        } else if ("apikey".equalsIgnoreCase(authType) && StringUtils.hasText(apiKey)) {
            headers.set("X-API-Key", apiKey);
        }
        return headers;
    }
}
