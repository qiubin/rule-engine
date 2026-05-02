package com.ruleengine.controller;

import com.ruleengine.domain.DataSet;
import com.ruleengine.service.DataSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-sets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DataSetController {

    private final DataSetService service;

    @GetMapping
    public ResponseEntity<List<DataSet>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSet> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<DataSet> create(@RequestBody DataSet dataSet) {
        return ResponseEntity.ok(service.save(dataSet));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataSet> update(@PathVariable Long id, @RequestBody DataSet dataSet) {
        dataSet.setId(id);
        return ResponseEntity.ok(service.save(dataSet));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
