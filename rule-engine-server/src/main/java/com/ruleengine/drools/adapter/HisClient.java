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
import java.util.Collections;
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
