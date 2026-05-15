package com.ruleengine.config;

import com.ruleengine.domain.AccessLog;
import com.ruleengine.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {

    private final AccessLogService accessLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        // 跳过静态资源、日志接口自身、前端资源
        if (path.startsWith("/api/v1/access-logs")
                || path.startsWith("/static/")
                || path.equals("/")
                || path.endsWith(".js")
                || path.endsWith(".css")
                || path.endsWith(".html")
                || path.endsWith(".ico")
                || path.endsWith(".png")
                || path.endsWith(".jpg")) {
            return true;
        }

        try {
            AccessLog accessLog = new AccessLog();
            accessLog.setPageName(request.getHeader("X-Page-Name"));
            accessLog.setRequestPath(path);
            accessLog.setRequestMethod(request.getMethod());
            accessLog.setClientIp(getClientIp(request));
            accessLog.setUserAgent(request.getHeader("User-Agent"));
            accessLogService.save(accessLog);
        } catch (Exception e) {
            log.warn("记录访问日志失败: {}", e.getMessage());
        }
        return true;
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
