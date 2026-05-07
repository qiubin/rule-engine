package com.ruleengine.service;

import com.ruleengine.domain.ConditionModel;
import com.ruleengine.domain.ConditionModelCategory;
import com.ruleengine.domain.DataElement;
import com.ruleengine.domain.DataSet;
import com.ruleengine.domain.enums.CommonStatus;
import com.ruleengine.domain.enums.DataType;
import com.ruleengine.domain.enums.NodeUsage;
import com.ruleengine.domain.enums.ValueSource;
import com.ruleengine.repository.ConditionModelCategoryRepository;
import com.ruleengine.repository.ConditionModelRepository;
import com.ruleengine.repository.DataElementRepository;
import com.ruleengine.repository.DataSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConditionSyncService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ConditionModelRepository conditionModelRepository;
    private final ConditionModelCategoryRepository categoryRepository;
    private final DataElementRepository dataElementRepository;
    private final DataSetRepository dataSetRepository;

    @Transactional
    public SyncResult sync() {
        // 使用原生 SQL 按依赖顺序清空，绕过 JPA 级联歧义
        int deletedResults = entityManager.createNativeQuery("DELETE FROM result_config").executeUpdate();
        int deletedOperators = entityManager.createNativeQuery("DELETE FROM condition_model_operators").executeUpdate();
        int deletedModels = entityManager.createNativeQuery("DELETE FROM condition_model").executeUpdate();
        int deletedCategories = entityManager.createNativeQuery("DELETE FROM condition_model_category").executeUpdate();
        log.info("清空: result_config={}, operators={}, condition_model={}, category={}",
                deletedResults, deletedOperators, deletedModels, deletedCategories);

        // 重建分类
        Map<String, Long> categoryCache = rebuildCategories();
        log.info("重建条件分类: {} 个", categoryCache.size());

        // 重建条件
        int modelCount = rebuildConditions(categoryCache);
        log.info("重建条件: {} 个", modelCount);

        return new SyncResult(categoryCache.size(), modelCount);
    }

    private Map<String, Long> rebuildCategories() {
        List<DataSet> allDataSets = dataSetRepository.findAll();
        Map<String, String> catL1NameMap = new LinkedHashMap<>();

        for (DataSet ds : allDataSets) {
            String code = ds.getCatL1Code();
            if (StringUtils.hasText(code) && !catL1NameMap.containsKey(code)) {
                catL1NameMap.put(code, StringUtils.hasText(ds.getCatL1Name()) ? ds.getCatL1Name() : code);
            }
        }

        Map<String, Long> cache = new HashMap<>();
        for (Map.Entry<String, String> entry : catL1NameMap.entrySet()) {
            String catCode = "CAT_" + entry.getKey();
            ConditionModelCategory cat = new ConditionModelCategory();
            cat.setCode(catCode);
            cat.setName(entry.getValue());
            cat.setDescription("同步自数据集一级分类: " + entry.getKey());
            try {
                cat.setSortOrder(Integer.parseInt(entry.getKey()));
            } catch (NumberFormatException e) {
                cat.setSortOrder(0);
            }
            cat.setStatus(CommonStatus.ENABLED);
            categoryRepository.save(cat);
            cache.put(entry.getKey(), cat.getId());
        }
        return cache;
    }

    private int rebuildConditions(Map<String, Long> categoryCache) {
        List<DataElement> allElements = dataElementRepository.findAll();
        List<DataSet> allDataSets = dataSetRepository.findAll();
        Map<Long, DataSet> datasetMap = new HashMap<>();
        for (DataSet ds : allDataSets) {
            datasetMap.put(ds.getId(), ds);
        }

        int count = 0;
        for (DataElement de : allElements) {
            if (!StringUtils.hasText(de.getCamelName())) {
                continue;
            }

            ConditionModel cm = new ConditionModel();
            cm.setCode(de.getCamelName());
            cm.setName(de.getName());
            cm.setDataType(de.getDataType());
            cm.setOperators(defaultOperators(de.getDataType()));
            cm.setValueSource(ValueSource.ADAPTER);
            cm.setNodeUsage(NodeUsage.CONDITION);
            cm.setDataElementId(de.getId());

            DataSet ds = datasetMap.get(de.getDatasetId());
            if (ds != null && StringUtils.hasText(ds.getCatL1Code())) {
                cm.setCategoryId(categoryCache.get(ds.getCatL1Code()));
            }

            conditionModelRepository.save(cm);
            count++;
        }
        return count;
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

    public static class SyncResult {
        public final int categoryCount;
        public final int conditionCount;

        public SyncResult(int categoryCount, int conditionCount) {
            this.categoryCount = categoryCount;
            this.conditionCount = conditionCount;
        }

        public int getCategoryCount() { return categoryCount; }
        public int getConditionCount() { return conditionCount; }
    }
}
