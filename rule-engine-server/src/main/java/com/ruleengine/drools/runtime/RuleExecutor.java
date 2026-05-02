package com.ruleengine.drools.runtime;

import com.ruleengine.script.DictScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleExecutor {

    private final KieServices kieServices;
    private final DictScriptService dictScriptService;
    private final Map<String, KieContainer> ruleContainerCache = new ConcurrentHashMap<>();
    private final AtomicInteger versionCounter = new AtomicInteger(0);

    public void compileAndCache(String ruleCode, String drl) {
        if (drl == null || drl.trim().isEmpty()) {
            throw new RuntimeException("规则 DRL 不能为空");
        }
        
        try {
            int version = versionCounter.incrementAndGet();
            ReleaseId releaseId = kieServices.newReleaseId("com.ruleengine", sanitize(ruleCode), "1.0." + version);
            
            KieFileSystem kfs = kieServices.newKieFileSystem();
            kfs.generateAndWritePomXML(releaseId);
            
            String path = "src/main/resources/rules/" + sanitize(ruleCode) + ".drl";
            kfs.write(path, ResourceFactory.newByteArrayResource(drl.getBytes()));
            
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
            kieBuilder.buildAll();
            
            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                String errors = kieBuilder.getResults().getMessages().toString();
                log.error("规则 [{}] 编译错误: {}", ruleCode, errors);
                throw new RuntimeException("规则编译失败: " + errors);
            }
            
            KieContainer container = kieServices.newKieContainer(releaseId);
            ruleContainerCache.put(ruleCode, container);
            log.info("规则 [{}] 已编译并缓存 (版本: {})", ruleCode, version);
        } catch (Exception e) {
            log.error("规则 [{}] 编译异常", ruleCode, e);
            throw new RuntimeException("规则编译异常: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> execute(String ruleCode, String drl, Map<String, Object> parameters) {
        KieContainer container = ruleContainerCache.get(ruleCode);
        if (container == null) {
            compileAndCache(ruleCode, drl);
            container = ruleContainerCache.get(ruleCode);
        }

        KieSession kieSession = null;
        try {
            kieSession = container.newKieSession();
            
            // 设置全局变量 result 和 dictUtils
            Map<String, Object> result = new HashMap<>();
            kieSession.setGlobal("result", result);
            kieSession.setGlobal("dictUtils", dictScriptService);
            
            // 插入参数
            kieSession.insert(parameters);
            
            // 执行规则
            int firedRules = kieSession.fireAllRules();
            log.info("规则 [{}] 执行完成，触发 {} 条规则", ruleCode, firedRules);
            
            Map<String, Object> response = new HashMap<>();
            response.put("ruleCode", ruleCode);
            response.put("firedRules", firedRules);
            response.put("matched", firedRules > 0);
            response.put("results", result.values());
            response.put("parameters", parameters);
            
            return response;
        } catch (Exception e) {
            log.error("规则 [{}] 执行异常", ruleCode, e);
            throw new RuntimeException("规则执行异常: " + e.getMessage(), e);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }
    }

    private String sanitize(String code) {
        String sanitized = code.replaceAll("[^a-zA-Z0-9]", "_");
        if (sanitized.length() > 0 && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "r_" + sanitized;
        }
        return sanitized;
    }
}
