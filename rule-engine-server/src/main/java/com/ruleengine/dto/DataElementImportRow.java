package com.ruleengine.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 数据元批量导入 Excel 行映射（按用户提供的标准格式）
 */
@Data
public class DataElementImportRow {

    @ExcelProperty({"一级分类II", "一级分类ID"})
    private String catL1Code;

    @ExcelProperty("数据集一级分类")
    private String catL1Name;

    @ExcelProperty({"二级分类I", "二级分类ID"})
    private String catL2Code;

    @ExcelProperty("数据集二级分类")
    private String catL2Name;

    @ExcelProperty({"三级分类I", "三级分类ID"})
    private String catL3Code;

    @ExcelProperty("数据集三级分类")
    private String catL3Name;

    @ExcelProperty("数据集英文名")
    private String datasetEnglishName;

    @ExcelProperty("数据集排序")
    private Integer datasetSortOrder;

    @ExcelProperty("数据元标准编码")
    private String standardCode;

    @ExcelProperty("数据元英文名")
    private String englishName;

    @ExcelProperty("数据元驼峰名称")
    private String camelName;

    @ExcelProperty("数据元名称")
    private String name;

    @ExcelProperty("数据元定义")
    private String definition;

    @ExcelProperty("数据元排序")
    private Integer sortOrder;

    @ExcelProperty("敏感性")
    private String sensitivity;

    /** 可选列：如为空，默认 STRING */
    @ExcelProperty("数据类型")
    private String dataType;
}
