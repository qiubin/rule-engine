package com.ruleengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleengine.domain.RuleExecutionLog;
import com.ruleengine.repository.RuleExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleExecutionLogService {

    private final RuleExecutionLogRepository logRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SneakyThrows
    public RuleExecutionLog saveLog(Long ruleId, String ruleCode, String ruleVersion,
                                     Map<String, Object> params, Map<String, Object> output,
                                     List<String> hitNodeIds, int firedCount, long durationMs,
                                     String status, String errorMessage) {
        RuleExecutionLog record = new RuleExecutionLog();
        record.setRuleId(ruleId);
        record.setRuleCode(ruleCode);
        record.setRuleVersion(ruleVersion);
        record.setParamsJson(objectMapper.writeValueAsString(params));
        record.setOutputJson(objectMapper.writeValueAsString(output));
        record.setHitNodeIds(objectMapper.writeValueAsString(hitNodeIds));
        record.setFiredCount(firedCount);
        record.setDurationMs(durationMs);
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        return logRepository.save(record);
    }

    public List<RuleExecutionLog> findByRuleId(Long ruleId) {
        return logRepository.findByRuleIdOrderByExecutedAtDesc(ruleId);
    }

    public RuleExecutionLog findById(Long id) {
        return logRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("执行日志不存在: " + id));
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public List<String> extractHitNodeIds(Map<String, Object> output) {
        Object resultsObj = output.get("results");
        if (!(resultsObj instanceof java.util.Collection)) {
            return java.util.Collections.emptyList();
        }
        return ((java.util.Collection<Map<String, Object>>) resultsObj).stream()
                .flatMap(r -> {
                    List<String> ids = new java.util.ArrayList<>();
                    Object resultNodeId = r.get("resultNodeId");
                    if (resultNodeId != null) {
                        ids.add(String.valueOf(resultNodeId));
                    }
                    Object hitIds = r.get("hitConditionIds");
                    if (hitIds instanceof java.util.List) {
                        ((java.util.List<?>) hitIds).forEach(id -> ids.add(String.valueOf(id)));
                    }
                    return ids.stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
