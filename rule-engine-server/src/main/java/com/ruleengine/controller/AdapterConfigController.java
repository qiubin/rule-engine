package com.ruleengine.controller;

import com.ruleengine.domain.AdapterConfig;
import com.ruleengine.service.AdapterConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/adapter-config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdapterConfigController {

    private final AdapterConfigService adapterConfigService;

    @GetMapping
    public ResponseEntity<List<AdapterConfig>> list() {
        return ResponseEntity.ok(adapterConfigService.findAll());
    }

    @GetMapping("/first")
    public ResponseEntity<AdapterConfig> getFirst() {
        return ResponseEntity.ok(adapterConfigService.findFirst());
    }

    @PostMapping
    public ResponseEntity<AdapterConfig> save(@RequestBody AdapterConfig config) {
        return ResponseEntity.ok(adapterConfigService.save(config));
    }
}
