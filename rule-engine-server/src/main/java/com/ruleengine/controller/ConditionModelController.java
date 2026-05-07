package com.ruleengine.controller;

import com.ruleengine.domain.ConditionModel;
import com.ruleengine.service.ConditionModelService;
import com.ruleengine.service.ConditionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/condition-models")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConditionModelController {

    private final ConditionModelService conditionModelService;
    private final ConditionSyncService conditionSyncService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        ConditionSyncService.SyncResult result = conditionSyncService.sync();
        Map<String, Object> map = new HashMap<>();
        map.put("categoryCount", result.getCategoryCount());
        map.put("conditionCount", result.getConditionCount());
        return ResponseEntity.ok(map);
    }

    @GetMapping
    public ResponseEntity<List<ConditionModel>> list() {
        return ResponseEntity.ok(conditionModelService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConditionModel> getById(@PathVariable Long id) {
        return ResponseEntity.ok(conditionModelService.findById(id));
    }

    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<List<ConditionModel>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(conditionModelService.findByCategoryId(categoryId));
    }

    @PostMapping
    public ResponseEntity<ConditionModel> create(@RequestBody ConditionModel conditionModel) {
        return ResponseEntity.ok(conditionModelService.save(conditionModel));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConditionModel> update(@PathVariable Long id, @RequestBody ConditionModel conditionModel) {
        conditionModel.setId(id);
        return ResponseEntity.ok(conditionModelService.save(conditionModel));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        conditionModelService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
