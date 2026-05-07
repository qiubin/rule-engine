package com.ruleengine.domain.enums;

public enum DataType {
    DICTIONARY,      // 字典值
    DICTIONARY_SET,  // 字典集合
    NUMERIC,         // 数值型
    STRING,          // 字符型
    DATE_TIME,       // 时间型
    LONG_TEXT,       // 长文本型（病程、讨论等大文本）
    BOOLEAN,         // 布尔型
    STRING_LIST,     // 字符串集合（症状列表、医嘱列表等）
    SCRIPT           // 脚本型
}
