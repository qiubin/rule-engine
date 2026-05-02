package com.ruleengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleengine.domain.Rule;
import com.ruleengine.domain.enums.RuleStatus;
import com.ruleengine.drools.compiler.DrlCompiler;
import com.ruleengine.drools.runtime.RuleExecutor;
import com.ruleengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleService {

    private final RuleRepository ruleRepository;
    private final DrlCompiler drlCompiler;
    private final RuleExecutor ruleExecutor;
    private final ObjectMapper objectMapper;

    public List<Rule> findAll() {
        return ruleRepository.findAll();
    }

    public List<Rule> findByRuleTypeId(Long ruleTypeId) {
        return ruleRepository.findByRuleTypeId(ruleTypeId);
    }

    public Rule findById(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
    }

    public Rule findByCode(String code) {
        return ruleRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + code));
    }

    @Transactional
    public Rule save(Rule rule) {
        validateRuleType(rule);
        if (rule.getStatus() == null) {
            rule.setStatus(RuleStatus.DRAFT);
        }
        return ruleRepository.save(rule);
    }

    @Transactional
    public Rule update(Long id, Rule rule) {
        Rule existing = findById(id);
        if (existing.getStatus() == RuleStatus.PUBLISHED) {
            throw new RuntimeException("已发布的规则不能直接修改，请先创建新版本");
        }
        validateRuleType(rule);
        existing.setName(rule.getName());
        existing.setRuleTypeId(rule.getRuleTypeId());
        return ruleRepository.save(existing);
    }

    @SneakyThrows
    @Transactional
    public Rule saveCanvas(Long id, String canvasData) {
        Rule rule = findById(id);
        if (rule.getStatus() == RuleStatus.PUBLISHED) {
            throw new RuntimeException("已发布的规则不能修改画布");
        }
        validateRuleType(rule);
        rule.setCanvasData(canvasData);

        JsonNode canvasNode = objectMapper.readTree(canvasData);
        String drl = drlCompiler.compile(rule.getCode(), canvasNode);
        rule.setDroolsDrl(drl);
        log.info("规则 [{}] 画布已保存，DRL 已生成", rule.getCode());

        return ruleRepository.save(rule);
    }

    @Transactional
    public Rule publish(Long id) {
        Rule rule = findById(id);
        if (rule.getDroolsDrl() == null || rule.getDroolsDrl().isEmpty()) {
            throw new RuntimeException("规则尚未配置画布，无法发布");
        }
        rule.setStatus(RuleStatus.PUBLISHED);
        ruleExecutor.compileAndCache(rule.getCode(), rule.getDroolsDrl());
        log.info("规则 [{}] 已发布", rule.getCode());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteById(Long id) {
        ruleRepository.deleteById(id);
    }

    public Map<String, Object> execute(String ruleCode, Map<String, Object> parameters) {
        Rule rule = findByCode(ruleCode);
        if (rule.getStatus() != RuleStatus.PUBLISHED) {
            throw new RuntimeException("规则未发布，无法执行");
        }
        return ruleExecutor.execute(ruleCode, rule.getDroolsDrl(), parameters);
    }

    @SneakyThrows
    public Map<String, Object> testExecute(Long id, Map<String, Object> parameters) {
        Rule rule = findById(id);
        if (rule.getCanvasData() == null || rule.getCanvasData().isEmpty()) {
            throw new RuntimeException("规则尚未配置画布，无法测试执行");
        }
        // 测试执行时重新编译 DRL，确保使用最新的编译器逻辑
        JsonNode canvasNode = objectMapper.readTree(rule.getCanvasData());
        String drl = drlCompiler.compile(rule.getCode(), canvasNode);
        if (drl == null || drl.isEmpty()) {
            throw new RuntimeException("规则画布为空，无法生成 DRL");
        }
        return ruleExecutor.execute(rule.getCode(), drl, parameters);
    }

    @SneakyThrows
    public Map<String, Object> batchTestExecute(List<Long> ruleIds, Map<String, Object> parameters) {
        List<Map<String, Object>> results = new ArrayList<>();
        int matchedCount = 0;
        int executedCount = 0;

        for (Long id : ruleIds) {
            Rule rule = null;
            try {
                rule = findById(id);
                if (rule.getCanvasData() == null || rule.getCanvasData().isEmpty()) {
                    continue;
                }
                JsonNode canvasNode = objectMapper.readTree(rule.getCanvasData());
                String drl = drlCompiler.compile(rule.getCode(), canvasNode);
                if (drl == null || drl.isEmpty()) {
                    continue;
                }
                Map<String, Object> result = ruleExecutor.execute(rule.getCode(), drl, parameters);
                executedCount++;

                Map<String, Object> summary = new HashMap<>();
                summary.put("ruleId", rule.getId());
                summary.put("ruleCode", rule.getCode());
                summary.put("ruleName", rule.getName());
                summary.put("status", rule.getStatus());
                summary.put("matched", result.get("matched"));
                summary.put("firedRules", result.get("firedRules"));
                summary.put("results", result.get("results"));

                if (Boolean.TRUE.equals(result.get("matched"))) {
                    matchedCount++;
                }
                results.add(summary);
            } catch (Exception e) {
                log.warn("规则 [{}] 批量测试执行失败: {}", rule != null ? rule.getCode() : id, e.getMessage());
                Map<String, Object> summary = new HashMap<>();
                summary.put("ruleId", id);
                summary.put("ruleCode", rule != null ? rule.getCode() : "");
                summary.put("ruleName", rule != null ? rule.getName() : "");
                summary.put("status", rule != null ? rule.getStatus() : "");
                summary.put("matched", false);
                summary.put("firedRules", 0);
                summary.put("error", e.getMessage());
                results.add(summary);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", ruleIds.size());
        response.put("executed", executedCount);
        response.put("matched", matchedCount);
        response.put("details", results);
        response.put("parameters", parameters);
        return response;
    }

    private void validateRuleType(Rule rule) {
        if (rule.getRuleTypeId() == null) {
            throw new RuntimeException("规则必须选择规则类型");
        }
    }
}
