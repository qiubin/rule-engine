package com.ruleengine.controller;

import com.ruleengine.domain.Rule;
import com.ruleengine.domain.RuleVersion;
import com.ruleengine.service.RuleService;
import com.ruleengine.service.RuleVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RuleVersionController {

    private final RuleVersionService versionService;
    private final RuleService ruleService;

    @GetMapping("/rules/{ruleId}/versions")
    public ResponseEntity<List<RuleVersion>> listByRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(versionService.findByRuleId(ruleId));
    }

    @GetMapping("/rules/{ruleId}/versions/{versionId}")
    public ResponseEntity<RuleVersion> getById(@PathVariable Long ruleId, @PathVariable Long versionId) {
        RuleVersion version = versionService.findById(versionId);
        if (!version.getRuleId().equals(ruleId)) {
            throw new RuntimeException("版本不属于该规则");
        }
        return ResponseEntity.ok(version);
    }

    @PostMapping("/rules/{ruleId}/rollback/{versionId}")
    public ResponseEntity<Rule> rollback(@PathVariable Long ruleId, @PathVariable Long versionId) {
        Rule rule = ruleService.findById(ruleId);
        return ResponseEntity.ok(versionService.rollback(rule, versionId));
    }
}
