package com.ruleengine.controller;

import com.ruleengine.domain.ResultConfig;
import com.ruleengine.service.ResultConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/result-configs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ResultConfigController {

    private final ResultConfigService resultConfigService;

    @GetMapping
    public ResponseEntity<List<ResultConfig>> list() {
        return ResponseEntity.ok(resultConfigService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResultConfig> getById(@PathVariable Long id) {
        return ResponseEntity.ok(resultConfigService.findById(id));
    }

    @GetMapping("/by-condition/{conditionModelId}")
    public ResponseEntity<List<ResultConfig>> getByConditionModelId(@PathVariable Long conditionModelId) {
        return ResponseEntity.ok(resultConfigService.findByConditionModelId(conditionModelId));
    }

    @PostMapping
    public ResponseEntity<ResultConfig> create(@RequestBody ResultConfig resultConfig) {
        return ResponseEntity.ok(resultConfigService.save(resultConfig));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResultConfig> update(@PathVariable Long id, @RequestBody ResultConfig resultConfig) {
        resultConfig.setId(id);
        return ResponseEntity.ok(resultConfigService.save(resultConfig));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resultConfigService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
