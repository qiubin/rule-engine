package com.ruleengine.controller;

import com.ruleengine.domain.Dictionary;
import com.ruleengine.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionaries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DictionaryController {

    private final DictionaryService service;

    @GetMapping
    public ResponseEntity<List<Dictionary>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dictionary> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<Dictionary> create(@RequestBody Dictionary dictionary) {
        return ResponseEntity.ok(service.save(dictionary));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Dictionary> update(@PathVariable Long id, @RequestBody Dictionary dictionary) {
        dictionary.setId(id);
        return ResponseEntity.ok(service.save(dictionary));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
