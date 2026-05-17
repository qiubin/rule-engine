package com.ruleengine.controller;

import com.ruleengine.domain.RuleType;
import com.ruleengine.service.RuleTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rule-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RuleTypeController {

    private final RuleTypeService ruleTypeService;

    @GetMapping
    public ResponseEntity<List<RuleType>> list(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false, defaultValue = "false") boolean flat) {
        if (flat) {
            return ResponseEntity.ok(ruleTypeService.findAllFlat());
        }
        if (parentId != null) {
            return ResponseEntity.ok(ruleTypeService.findByParentId(parentId));
        }
        return ResponseEntity.ok(ruleTypeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuleType> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ruleTypeService.findById(id));
    }

    @PostMapping
    public ResponseEntity<RuleType> create(@RequestBody RuleType ruleType) {
        return ResponseEntity.ok(ruleTypeService.save(ruleType));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuleType> update(@PathVariable Long id, @RequestBody RuleType ruleType) {
        ruleType.setId(id);
        return ResponseEntity.ok(ruleTypeService.save(ruleType));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleTypeService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
