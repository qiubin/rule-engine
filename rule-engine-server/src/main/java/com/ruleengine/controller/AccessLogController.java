package com.ruleengine.controller;

import com.ruleengine.domain.AccessLog;
import com.ruleengine.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/access-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccessLogController {

    private final AccessLogService service;

    @PostMapping
    public ResponseEntity<AccessLog> create(@RequestBody AccessLog accessLog, HttpServletRequest request) {
        if (accessLog.getClientIp() == null || accessLog.getClientIp().isEmpty()) {
            accessLog.setClientIp(getClientIp(request));
        }
        if (accessLog.getRequestPath() == null || accessLog.getRequestPath().isEmpty()) {
            accessLog.setRequestPath(request.getRequestURI());
        }
        if (accessLog.getRequestMethod() == null || accessLog.getRequestMethod().isEmpty()) {
            accessLog.setRequestMethod(request.getMethod());
        }
        if (accessLog.getUserAgent() == null || accessLog.getUserAgent().isEmpty()) {
            accessLog.setUserAgent(request.getHeader("User-Agent"));
        }
        return ResponseEntity.ok(service.save(accessLog));
    }

    @GetMapping
    public ResponseEntity<Page<AccessLog>> search(
            @RequestParam(required = false) String pageName,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("accessTime").descending());
        return ResponseEntity.ok(service.search(pageName, clientIp, requestPath, startTime, endTime, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
