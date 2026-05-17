package com.ruleengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RuleEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleEngineApplication.class, args);
    }
}
// 这段程序是规则引擎应用的启动入口，它是一个基于Spring Boot框架开发的Java应用程序
// 通过@SpringBootApplication注解开启自动配置、组件扫描等Spring Boot核心功能
// main方法中调用SpringApplication.run来启动整个Spring应用上下文，加载所有的配置和组件
