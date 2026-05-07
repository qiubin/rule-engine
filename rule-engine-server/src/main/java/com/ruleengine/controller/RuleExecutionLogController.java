package com.ruleengine.controller;

import com.ruleengine.domain.RuleExecutionLog;
import com.ruleengine.service.RuleExecutionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RuleExecutionLogController {

    private final RuleExecutionLogService logService;

    @GetMapping("/rules/{ruleId}/logs")
    public ResponseEntity<List<RuleExecutionLog>> listByRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(logService.findByRuleId(ruleId));
    }

    @GetMapping("/rule-logs/{id}")
    public ResponseEntity<RuleExecutionLog> getById(@PathVariable Long id) {
        return ResponseEntity.ok(logService.findById(id));
    }
}
