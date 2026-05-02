package com.ruleengine.controller;

import com.ruleengine.domain.ConditionModelCategory;
import com.ruleengine.service.ConditionModelCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/condition-model-categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConditionModelCategoryController {

    private final ConditionModelCategoryService service;

    @GetMapping
    public ResponseEntity<List<ConditionModelCategory>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConditionModelCategory> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ConditionModelCategory> create(@RequestBody ConditionModelCategory category) {
        return ResponseEntity.ok(service.save(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConditionModelCategory> update(@PathVariable Long id, @RequestBody ConditionModelCategory category) {
        category.setId(id);
        return ResponseEntity.ok(service.save(category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
