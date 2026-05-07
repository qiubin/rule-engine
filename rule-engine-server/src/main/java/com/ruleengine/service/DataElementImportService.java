package com.ruleengine.service;

import com.alibaba.excel.EasyExcel;
import com.ruleengine.domain.ConditionModel;
import com.ruleengine.domain.ConditionModelCategory;
import com.ruleengine.domain.DataElement;
import com.ruleengine.domain.DataSet;
import com.ruleengine.domain.enums.CommonStatus;
import com.ruleengine.domain.enums.DataType;
import com.ruleengine.domain.enums.NodeUsage;
import com.ruleengine.domain.enums.ValueSource;
import com.ruleengine.dto.DataElementImportResult;
import com.ruleengine.dto.DataElementImportRow;
import com.ruleengine.repository.ConditionModelCategoryRepository;
import com.ruleengine.repository.ConditionModelRepository;
import com.ruleengine.repository.DataElementRepository;
import com.ruleengine.repository.DataSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 数据元批量导入服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataElementImportService {

    private final DataSetRepository dataSetRepository;
    private final DataElementRepository dataElementRepository;
    private final ConditionModelRepository conditionModelRepository;
    private final ConditionModelCategoryRepository categoryRepository;

    /**
     * 从 Excel 导入数据元（按用户提供的 15 列标准格式）
     */
    @Transactional
    public DataElementImportResult importFromExcel(MultipartFile file) throws IOException {
        List<DataElementImportRow> rows = EasyExcel.read(file.getInputStream())
                .head(DataElementImportRow.class)
                .sheet()
                .headRowNumber(0)
                .doReadSync();

        DataElementImportResult result = new DataElementImportResult();
        result.setTotalRows(rows.size());

        // 正向填充（处理 Excel 合并单元格导致的空值）
        forwardFill(rows);

        // 缓存：catL3Code -> DataSet
        Map<String, DataSet> datasetCache = new HashMap<>();
        // 缓存：catL1Code -> CategoryId
        Map<String, Long> categoryCache = new HashMap<>();

        int rowNum = 1;
        for (DataElementImportRow row : rows) {
            rowNum++;
            if (!StringUtils.hasText(row.getCatL3Code()) || !StringUtils.hasText(row.getCamelName())) {
                result.getErrors().add(new DataElementImportResult.ErrorItem(
                        rowNum, row.getStandardCode(), row.getCamelName(),
                        "三级分类编码和数据元驼峰名称为必填"));
                continue;
            }

            try {
                // 1. 处理数据集
                DataSet dataSet = upsertDataSet(row, datasetCache);

                // 2. 处理数据元
                DataElement element = upsertDataElement(row, dataSet);

                // 3. 自动创建条件模型（让画布可直接使用）
                Long categoryId = resolveCategoryId(row.getCatL1Code(), row.getCatL1Name(), categoryCache);
                autoCreateConditionModel(element, categoryId);

            } catch (Exception e) {
                log.warn("导入行 {} 失败: {}", rowNum, e.getMessage());
                result.getErrors().add(new DataElementImportResult.ErrorItem(
                        rowNum, row.getStandardCode(), row.getCamelName(),
                        e.getMessage()));
            }
        }

        return result;
    }

    private void forwardFill(List<DataElementImportRow> rows) {
        DataElementImportRow prev = null;
        for (DataElementImportRow row : rows) {
            if (prev != null) {
                if (!StringUtils.hasText(row.getCatL1Code())) row.setCatL1Code(prev.getCatL1Code());
                if (!StringUtils.hasText(row.getCatL1Name())) row.setCatL1Name(prev.getCatL1Name());
                if (!StringUtils.hasText(row.getCatL2Code())) row.setCatL2Code(prev.getCatL2Code());
                if (!StringUtils.hasText(row.getCatL2Name())) row.setCatL2Name(prev.getCatL2Name());
                if (!StringUtils.hasText(row.getCatL3Code())) row.setCatL3Code(prev.getCatL3Code());
                if (!StringUtils.hasText(row.getCatL3Name())) row.setCatL3Name(prev.getCatL3Name());
                if (!StringUtils.hasText(row.getDatasetEnglishName())) row.setDatasetEnglishName(prev.getDatasetEnglishName());
                if (row.getDatasetSortOrder() == null) row.setDatasetSortOrder(prev.getDatasetSortOrder());
            }
            prev = row;
        }
    }

    private DataSet upsertDataSet(DataElementImportRow row, Map<String, DataSet> cache) {
        String catL3Code = row.getCatL3Code();
        DataSet ds = cache.get(catL3Code);
        if (ds != null) return ds;

        Optional<DataSet> existing = dataSetRepository.findByCode(catL3Code);
        if (existing.isPresent()) {
            ds = existing.get();
        } else {
            ds = new DataSet();
            ds.setCode(catL3Code);
        }
        ds.setName(StringUtils.hasText(row.getCatL3Name()) ? row.getCatL3Name() : catL3Code);
        ds.setEnglishName(row.getDatasetEnglishName());
        ds.setCatL1Code(row.getCatL1Code());
        ds.setCatL1Name(row.getCatL1Name());
        ds.setCatL2Code(row.getCatL2Code());
        ds.setCatL2Name(row.getCatL2Name());
        ds.setCatL3Code(catL3Code);
        ds.setCatL3Name(row.getCatL3Name());
        ds.setSortOrder(row.getDatasetSortOrder());
        ds.setStatus(CommonStatus.ENABLED);
        ds = dataSetRepository.save(ds);
        cache.put(catL3Code, ds);
        return ds;
    }

    private DataElement upsertDataElement(DataElementImportRow row, DataSet dataSet) {
        String camelName = row.getCamelName();
        Optional<DataElement> existing = dataElementRepository.findByCamelNameAndDatasetId(camelName, dataSet.getId());
        DataElement element;
        if (existing.isPresent()) {
            element = existing.get();
        } else {
            element = new DataElement();
            element.setCamelName(camelName);
            element.setDatasetId(dataSet.getId());
        }
        // DB 唯一标识用 "数据集编码.驼峰名"
        element.setCode(dataSet.getCode() + "." + camelName);
        element.setName(row.getName());
        element.setStandardCode(row.getStandardCode());
        element.setEnglishName(row.getEnglishName());
        element.setDefinition(row.getDefinition());
        element.setSortOrder(row.getSortOrder());
        element.setSensitivity(row.getSensitivity());
        element.setDataType(parseDataType(row.getDataType()));
        element.setStatus(CommonStatus.ENABLED);
        return dataElementRepository.save(element);
    }

    private DataType parseDataType(String dt) {
        if (!StringUtils.hasText(dt)) return DataType.STRING;
        try {
            return DataType.valueOf(dt.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 兼容中文映射
            String d = dt.trim();
            if (d.contains("字典") && d.contains("集")) return DataType.DICTIONARY_SET;
            if (d.contains("字典")) return DataType.DICTIONARY;
            if (d.contains("数值") || d.contains("数字")) return DataType.NUMERIC;
            if (d.contains("布尔")) return DataType.BOOLEAN;
            if (d.contains("列表") || d.contains("集合")) return DataType.STRING_LIST;
            if (d.contains("长文本")) return DataType.LONG_TEXT;
            if (d.contains("时间") || d.contains("日期")) return DataType.DATE_TIME;
            return DataType.STRING;
        }
    }

    private Long resolveCategoryId(String catL1Code, String catL1Name, Map<String, Long> cache) {
        if (!StringUtils.hasText(catL1Code)) return null;
        return cache.computeIfAbsent(catL1Code, k -> {
            String code = "CAT_" + k;
            String name = StringUtils.hasText(catL1Name) ? catL1Name : code;
            Optional<ConditionModelCategory> existing = categoryRepository.findByCode(code);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
            ConditionModelCategory cat = new ConditionModelCategory();
            cat.setCode(code);
            cat.setName(name);
            cat.setDescription("自动创建自数据元导入，一级分类: " + k);
            cat.setSortOrder(Integer.parseInt(k));
            cat.setStatus(CommonStatus.ENABLED);
            categoryRepository.save(cat);
            return cat.getId();
        });
    }

    private void autoCreateConditionModel(DataElement element, Long categoryId) {
        if (element.getCamelName() == null) return;

        // 幂等：已存在则不重复创建
        boolean hasModel = conditionModelRepository.existsByDataElementIdAndCode(element.getId(), element.getCamelName());
        if (hasModel) return;

        ConditionModel cm = new ConditionModel();
        cm.setCode(element.getCamelName());
        cm.setName(element.getName());
        cm.setDataType(element.getDataType());
        cm.setOperators(defaultOperators(element.getDataType()));
        cm.setValueSource(ValueSource.ADAPTER);
        cm.setNodeUsage(NodeUsage.CONDITION);
        cm.setDataElementId(element.getId());
        cm.setCategoryId(categoryId);
        conditionModelRepository.save(cm);
        log.debug("自动创建条件模型: code={}, name={}, dataType={}", cm.getCode(), cm.getName(), cm.getDataType());
    }

    private List<String> defaultOperators(DataType dt) {
        if (dt == null) return Collections.singletonList("==");
        switch (dt) {
            case STRING:
            case LONG_TEXT:
                return Arrays.asList("==", "contains", "regexMatch", "lengthCheck", "isBlank", "similarity");
            case NUMERIC:
                return Arrays.asList("==", "!=", ">", "<", ">=", "<=", "dataCheck");
            case DICTIONARY:
                return Collections.singletonList("==");
            case DICTIONARY_SET:
                return Collections.singletonList("IN_SET");
            case DATE_TIME:
                return Collections.singletonList("timeCheck");
            case BOOLEAN:
                return Arrays.asList("==", "isTrue", "isFalse");
            case STRING_LIST:
                return Arrays.asList("arrayLength", "arrayIntersect");
            default:
                return Collections.singletonList("==");
        }
    }
}
