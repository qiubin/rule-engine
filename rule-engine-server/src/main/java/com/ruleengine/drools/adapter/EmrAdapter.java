package com.ruleengine.drools.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * EMR 数据适配器：将 HIS 病历数据接入规则引擎。
 * 实现 DataAdapter 接口，供 AdapterFactory 调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmrAdapter implements DataAdapter {

    private final EmrDataService emrDataService;

    public static final String TYPE = "EMR";

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 获取病历数据。
     *
     * @param config  适配器配置（可包含指定字段列表、时间窗等）
     * @param context 执行上下文，必须包含 patientId（或 admissionId）
     * @return Map<String, Object>，按 camelName 平铺的病历数据
     */
    @Override
    public Object fetch(Map<String, Object> config, Map<String, Object> context) {
        String patientId = context != null ? String.valueOf(context.get("patientId")) : null;
        String admissionId = context != null && context.containsKey("admissionId")
                ? String.valueOf(context.get("admissionId")) : null;

        if (patientId == null || patientId.isEmpty() || "null".equals(patientId)) {
            log.warn("EMR 适配器缺少 patientId，无法获取病历数据");
            return context;
        }

        Map<String, Object> emrData = emrDataService.fetchPatientData(patientId, admissionId);
        if (context != null) {
            // 合并到上下文，HIS 数据覆盖传入参数（同名时）
            emrData.putAll(context);
        }
        return emrData;
    }
}
