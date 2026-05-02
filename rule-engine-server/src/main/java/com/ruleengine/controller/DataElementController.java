package com.ruleengine.controller;

import com.ruleengine.domain.DataElement;
import com.ruleengine.service.DataElementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-elements")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DataElementController {

    private final DataElementService service;

    @GetMapping
    public ResponseEntity<List<DataElement>> list(@RequestParam(required = false) Long datasetId) {
        if (datasetId != null) {
            return ResponseEntity.ok(service.findByDatasetId(datasetId));
        }
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataElement> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<DataElement> create(@RequestBody DataElement element) {
        return ResponseEntity.ok(service.save(element));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataElement> update(@PathVariable Long id, @RequestBody DataElement element) {
        element.setId(id);
        return ResponseEntity.ok(service.save(element));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
