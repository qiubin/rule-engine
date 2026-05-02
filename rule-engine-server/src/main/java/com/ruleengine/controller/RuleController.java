package com.ruleengine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruleengine.domain.Rule;
import com.ruleengine.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RuleController {

    private final RuleService ruleService;

    @GetMapping
    public ResponseEntity<List<Rule>> list(@RequestParam(required = false) Long ruleTypeId) {
        if (ruleTypeId != null) {
            return ResponseEntity.ok(ruleService.findByRuleTypeId(ruleTypeId));
        }
        return ResponseEntity.ok(ruleService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rule> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ruleService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Rule> create(@RequestBody Rule rule) {
        return ResponseEntity.ok(ruleService.save(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Rule> update(@PathVariable Long id, @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleService.update(id, rule));
    }

    @PutMapping("/{id}/canvas")
    public ResponseEntity<Rule> saveCanvas(@PathVariable Long id, @RequestBody JsonNode canvasData) {
        return ResponseEntity.ok(ruleService.saveCanvas(id, canvasData.toString()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Rule> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ruleService.publish(id));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> parameters) {
        Rule rule = ruleService.findById(id);
        return ResponseEntity.ok(ruleService.execute(rule.getCode(), parameters));
    }

    @PostMapping("/{id}/test-execute")
    public ResponseEntity<Map<String, Object>> testExecute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> parameters) {
        return ResponseEntity.ok(ruleService.testExecute(id, parameters));
    }

    @PostMapping("/batch-test-execute")
    public ResponseEntity<Map<String, Object>> batchTestExecute(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) request.get("ruleIds");
        List<Long> ruleIds = new ArrayList<>();
        for (Object id : rawIds) {
            if (id instanceof Number) {
                ruleIds.add(((Number) id).longValue());
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
        return ResponseEntity.ok(ruleService.batchTestExecute(ruleIds, parameters));
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeByCode(
            @RequestParam String ruleCode,
            @RequestBody Map<String, Object> parameters) {
        return ResponseEntity.ok(ruleService.execute(ruleCode, parameters));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
