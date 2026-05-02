package com.ruleengine.drools.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SQL 适配器：通过执行 SQL 获取数据
 * MVP 版本为简化实现，实际应使用 JdbcTemplate 执行真实 SQL
 */
@Slf4j
@Component
public class SqlAdapter implements DataAdapter {

    @Override
    public String getType() {
        return "SQL";
    }

    @Override
    public Object fetch(Map<String, Object> config, Map<String, Object> context) {
        String sql = config != null ? (String) config.get("sql") : null;
        if (sql == null) {
            return null;
        }
        log.debug("执行 SQL: {}", sql);
        // MVP 版本：模拟返回，实际应使用 JdbcTemplate 执行
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("sql", sql);
        result.put("mockResult", true);
        return result;
    }
}
