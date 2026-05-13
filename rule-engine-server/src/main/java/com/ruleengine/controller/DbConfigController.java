package com.ruleengine.controller;

import com.ruleengine.domain.DbConfig;
import com.ruleengine.service.DbConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/db-config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DbConfigController {

    private final DbConfigService dbConfigService;

    @GetMapping
    public ResponseEntity<DbConfig> get() {
        return ResponseEntity.ok(dbConfigService.findFirst());
    }

    @PostMapping
    public ResponseEntity<DbConfig> save(@RequestBody DbConfig config) {
        return ResponseEntity.ok(dbConfigService.save(config));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody DbConfig config) {
        boolean success = dbConfigService.testConnection(config);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "连接成功" : "连接失败，请检查地址、端口、用户名和密码");
        return ResponseEntity.ok(result);
    }
}
