package com.ruleengine.drools.adapter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdapterFactory {

    private final Map<String, DataAdapter> adapterMap = new ConcurrentHashMap<>();

    public AdapterFactory(List<DataAdapter> adapters) {
        for (DataAdapter adapter : adapters) {
            adapterMap.put(adapter.getType(), adapter);
        }
    }

    public DataAdapter getAdapter(String type) {
        DataAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            throw new RuntimeException("不支持的数据适配器类型: " + type);
        }
        return adapter;
    }
}
