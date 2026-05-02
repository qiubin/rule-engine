package com.ruleengine.drools.adapter;

import java.util.Map;

/**
 * 数据适配器接口
 * 用于从不同来源获取条件值：参数、SQL、外部服务等
 */
public interface DataAdapter {

    /**
     * 适配器类型标识
     */
    String getType();

    /**
     * 根据配置获取数据
     *
     * @param config   适配器配置（SQL模板、服务地址等）
     * @param context  执行上下文（传入的参数等）
     * @return 获取到的值
     */
    Object fetch(Map<String, Object> config, Map<String, Object> context);
}
