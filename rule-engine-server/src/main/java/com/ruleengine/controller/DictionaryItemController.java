package com.ruleengine.controller;

import com.ruleengine.domain.DictionaryItem;
import com.ruleengine.service.DictionaryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionary-items")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DictionaryItemController {

    private final DictionaryItemService service;

    @GetMapping
    public ResponseEntity<List<DictionaryItem>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DictionaryItem>> search(
            @RequestParam String dictCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));
        return ResponseEntity.ok(service.searchByDictCode(dictCode, keyword, status, pageable));
    }

    @GetMapping("/by-dict-code/{dictCode}")
    public ResponseEntity<List<DictionaryItem>> getByDictCode(@PathVariable String dictCode) {
        return ResponseEntity.ok(service.findByDictCode(dictCode));
    }

    @GetMapping("/by-dictionary/{dictionaryId}")
    public ResponseEntity<List<DictionaryItem>> getByDictionaryId(@PathVariable Long dictionaryId) {
        return ResponseEntity.ok(service.findByDictionaryId(dictionaryId));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<DictionaryItem> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<DictionaryItem> create(@RequestBody DictionaryItem item) {
        return ResponseEntity.ok(service.save(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DictionaryItem> update(@PathVariable Long id, @RequestBody DictionaryItem item) {
        item.setId(id);
        return ResponseEntity.ok(service.save(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
