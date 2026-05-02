package com.ruleengine.drools.adapter;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 参数适配器：从传入的参数中获取值
 */
@Component
public class ParamAdapter implements DataAdapter {

    @Override
    public String getType() {
        return "PARAM";
    }

    @Override
    public Object fetch(Map<String, Object> config, Map<String, Object> context) {
        String paramName = config != null ? (String) config.get("paramName") : null;
        if (paramName == null) {
            return null;
        }
        return context != null ? context.get(paramName) : null;
    }
}
