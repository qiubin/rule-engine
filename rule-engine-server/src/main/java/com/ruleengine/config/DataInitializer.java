package com.ruleengine.config;

import com.ruleengine.domain.*;
import com.ruleengine.domain.enums.*;
import com.ruleengine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RuleTypeRepository ruleTypeRepository;
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryItemRepository dictionaryItemRepository;
    private final DataElementRepository dataElementRepository;
    private final ConditionModelRepository conditionModelRepository;
    private final ConditionModelCategoryRepository categoryRepository;
    private final DataSetRepository dataSetRepository;
    private final JdbcTemplate jdbcTemplate;

    private void ensureTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rule", Integer.class);
        } catch (Exception e) {
            log.info("Creating missing 'rule' table...");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS rule (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  code VARCHAR(64) NOT NULL UNIQUE," +
                "  name VARCHAR(128) NOT NULL," +
                "  version VARCHAR(16) DEFAULT '1.0.0'," +
                "  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'," +
                "  rule_type_id BIGINT NOT NULL," +
                "  canvas_data LONGTEXT," +
                "  drools_drl LONGTEXT," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("'rule' table created.");
        }
    }

    private void migrateDataElementIds() {
        // 反向迁移：把 condition_model_data_elements 中的数据复制回 condition_model.data_element_id
        try {
            List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT condition_model_id, data_element_id FROM condition_model_data_elements"
            );
            for (java.util.Map<String, Object> row : rows) {
                Long modelId = ((Number) row.get("condition_model_id")).longValue();
                Long deId = ((Number) row.get("data_element_id")).longValue();
                jdbcTemplate.update(
                    "UPDATE condition_model SET data_element_id = ? WHERE id = ? AND (data_element_id IS NULL OR data_element_id = 0)",
                    deId, modelId
                );
            }
            log.info("Reverse migration: {} rows restored.", rows.size());
        } catch (Exception e) {
            log.warn("Reverse migration skipped: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureTableExists();
        migrateDataElementIds();
        log.info("开始检查并初始化基础数据...");

        initDictionaries();
        initDataSets();
        initDataElements();
        initRuleTypes();
        initConditionModelCategories();
        initConditionModels();

        log.info("基础数据初始化完成");
    }

    private void initDictionaries() {
        if (dictionaryRepository.count() > 0) {
            log.info("字典数据已存在，跳过");
            return;
        }
        saveDictionary("GENDER", "性别", new Object[][]{{"M", "男", "1"}, {"F", "女", "2"}, {"U", "未知", "9"}});
        saveDictionary("ICD10", "ICD10诊断编码", new Object[][]{{"A01", "伤寒", "A01"}, {"J18", "肺炎", "J18"}, {"I10", "高血压", "I10"}, {"E11", "2型糖尿病", "E11"}});
        saveDictionary("DRUG", "药品", new Object[][]{{"PENICILLIN", "青霉素", "PENICILLIN"}, {"CEFTRIAXONE", "头孢曲松", "CEFTRIAXONE"}, {"METFORMIN", "二甲双胍", "METFORMIN"}});
        saveDictionary("NURSING_LEVEL", "护理分级", new Object[][]{{"LEVEL_1", "特级护理", "1"}, {"LEVEL_2", "一级护理", "2"}, {"LEVEL_3", "二级护理", "3"}, {"LEVEL_4", "三级护理", "4"}});
        saveDictionary("FORBIDDEN_TYPE", "禁忌类别", new Object[][]{{"ABSOLUTE", "绝对禁忌", "ABSOLUTE"}, {"RELATIVE", "相对禁忌", "RELATIVE"}, {"WARNING", "预警提醒", "WARNING"}, {"MESSAGE", "消息提醒", "MESSAGE"}});
    }

    private void saveDictionary(String code, String name, Object[][] items) {
        Dictionary dict = new Dictionary();
        dict.setCode(code);
        dict.setName(name);
        dictionaryRepository.save(dict);
        int sort = 1;
        for (Object[] item : items) {
            DictionaryItem di = new DictionaryItem();
            di.setDictCode(code);
            di.setItemCode((String) item[0]);
            di.setItemName((String) item[1]);
            di.setItemValue((String) item[2]);
            di.setSortOrder(sort++);
            di.setDictionary(dict);
            dictionaryItemRepository.save(di);
        }
    }

    private void initDataSets() {
        if (dataSetRepository.count() > 0) {
            log.info("数据集已存在，跳过");
            return;
        }
        DataSet ds = new DataSet();
        ds.setCode("GENERAL");
        ds.setName("通用数据集");
        ds.setDescription("默认通用数据集");
        dataSetRepository.save(ds);
        log.info("通用数据集已创建");
    }

    private void initDataElements() {
        DataSet generalSet = dataSetRepository.findByCode("GENERAL").orElse(null);
        if (dataElementRepository.count() > 0) {
            // 为现有数据元补充 datasetId
            if (generalSet != null) {
                boolean needFix = dataElementRepository.findAll().stream()
                        .anyMatch(de -> de.getDatasetId() == null);
                if (needFix) {
                    log.info("为现有数据元补充数据集...");
                    for (DataElement de : dataElementRepository.findAll()) {
                        if (de.getDatasetId() == null) {
                            de.setDatasetId(generalSet.getId());
                            dataElementRepository.save(de);
                        }
                    }
                }
            }
            log.info("数据元已存在，跳过");
            return;
        }
        if (generalSet == null) {
            log.warn("通用数据集不存在，跳过数据元初始化");
            return;
        }
        Long datasetId = generalSet.getId();
        saveDataElement("PATIENT_AGE", "患者年龄", DataType.NUMERIC, null, "患者年龄，单位为岁", datasetId);
        saveDataElement("PATIENT_GENDER", "患者性别", DataType.DICTIONARY, "GENDER", null, datasetId);
        saveDataElement("DIAGNOSIS_CODE", "诊断编码", DataType.DICTIONARY_SET, "ICD10", null, datasetId);
        saveDataElement("DRUG_ORDER", "医嘱药品", DataType.DICTIONARY_SET, "DRUG", null, datasetId);
        saveDataElement("LAB_RESULT_VALUE", "检验结果值", DataType.NUMERIC, null, null, datasetId);
        saveDataElement("EMR_CONTENT", "病历文书内容", DataType.STRING, null, null, datasetId);
        saveDataElement("VITAL_BP", "血压", DataType.NUMERIC, null, "单位：mmHg", datasetId);
        saveDataElement("VITAL_TEMP", "体温", DataType.NUMERIC, null, "单位：℃", datasetId);
    }

    private void saveDataElement(String code, String name, DataType dataType, String dictCode, String description, Long datasetId) {
        DataElement de = new DataElement();
        de.setCode(code);
        de.setName(name);
        de.setDataType(dataType);
        de.setDictCode(dictCode);
        de.setDescription(description);
        de.setDatasetId(datasetId);
        dataElementRepository.save(de);
    }

    private void initRuleTypes() {
        if (ruleTypeRepository.count() > 0) {
            log.info("规则类型已存在，跳过");
            return;
        }
        saveRuleType("QUALITY_CTRL", "合理性质控", "医疗合理性质控规则");
        saveRuleType("MEDICAL_RECORD_QC", "病历质控", "病历内涵质控规则");
        saveRuleType("MEDICARE_AUDIT", "医保稽核", "医保审核规则");
        saveRuleType("NURSING_DECISION", "护理决策", "护理计划与评估规则");
        saveRuleType("VTE_PREVENTION", "VTE防治", "静脉血栓栓塞防治规则");
    }

    private void saveRuleType(String code, String name, String description) {
        RuleType rt = new RuleType();
        rt.setCode(code);
        rt.setName(name);
        rt.setDescription(description);
        ruleTypeRepository.save(rt);
    }

    private void initConditionModelCategories() {
        if (categoryRepository.count() > 0) {
            log.info("条件分类已存在，跳过");
            return;
        }
        saveCategory("PATIENT_INFO", "患者信息", "患者基本信息相关", 1);
        saveCategory("ORDERS", "医嘱信息", "医嘱相关", 2);
        saveCategory("DIAGNOSIS", "诊断信息", "诊断相关", 3);
        saveCategory("MEDICAL_RECORD", "病历文书", "病历文书相关", 4);
        saveCategory("VITAL_SIGNS", "生命体征", "生命体征相关", 5);
    }

    private void saveCategory(String code, String name, String description, int sort) {
        ConditionModelCategory cat = new ConditionModelCategory();
        cat.setCode(code);
        cat.setName(name);
        cat.setDescription(description);
        cat.setSortOrder(sort);
        categoryRepository.save(cat);
    }

    private void initConditionModels() {
        if (conditionModelRepository.count() > 0) {
            boolean needFixCategory = conditionModelRepository.findAll().stream()
                    .anyMatch(cm -> cm.getCategoryId() == null);
            if (needFixCategory) {
                log.info("条件缺少分类，开始修复...");
                fixConditionModelCategories();
            } else {
                log.info("条件已存在，跳过");
            }
            return;
        }

        ConditionModelCategory patientCat = categoryRepository.findByCode("PATIENT_INFO")
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        ConditionModelCategory ordersCat = categoryRepository.findByCode("ORDERS")
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        ConditionModelCategory diagnosisCat = categoryRepository.findByCode("DIAGNOSIS")
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        ConditionModelCategory emrCat = categoryRepository.findByCode("MEDICAL_RECORD")
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        ConditionModelCategory vitalCat = categoryRepository.findByCode("VITAL_SIGNS")
                .orElseThrow(() -> new RuntimeException("分类不存在"));

        DataElement age = findDe("PATIENT_AGE");
        DataElement gender = findDe("PATIENT_GENDER");
        DataElement diagnosis = findDe("DIAGNOSIS_CODE");
        DataElement drug = findDe("DRUG_ORDER");
        DataElement emr = findDe("EMR_CONTENT");
        DataElement bp = findDe("VITAL_BP");
        DataElement temp = findDe("VITAL_TEMP");

        saveConditionModel(age.getId(), patientCat.getId(), Arrays.asList("==", "!=", ">", "<"), ValueSource.PARAM, NodeUsage.CONDITION);
        saveConditionModel(gender.getId(), patientCat.getId(), Collections.singletonList("=="), ValueSource.PARAM, NodeUsage.CONDITION);
        saveConditionModel(diagnosis.getId(), diagnosisCat.getId(), Collections.singletonList("IN_SET"), ValueSource.PARAM, NodeUsage.CONDITION);
        saveConditionModel(drug.getId(), ordersCat.getId(), Collections.singletonList("IN_SET"), ValueSource.PARAM, NodeUsage.CONDITION);
        saveConditionModel(null, null, Collections.singletonList("=="), ValueSource.PARAM, NodeUsage.RESULT);
        saveConditionModel(emr.getId(), emrCat.getId(), Arrays.asList("==", "contains", "regex_match"), ValueSource.ADAPTER, NodeUsage.CONDITION);
        saveConditionModel(bp.getId(), vitalCat.getId(), Arrays.asList("==", "!=", ">", "<"), ValueSource.PARAM, NodeUsage.BOTH);
        saveConditionModel(temp.getId(), vitalCat.getId(), Arrays.asList("==", "!=", ">", "<"), ValueSource.PARAM, NodeUsage.BOTH);
        saveConditionModel(null, null, Collections.singletonList("=="), ValueSource.PARAM, NodeUsage.RESULT);
    }

    private void fixConditionModelCategories() {
        ConditionModelCategory patientCat = categoryRepository.findByCode("PATIENT_INFO").orElse(null);
        ConditionModelCategory ordersCat = categoryRepository.findByCode("ORDERS").orElse(null);
        ConditionModelCategory diagnosisCat = categoryRepository.findByCode("DIAGNOSIS").orElse(null);
        ConditionModelCategory emrCat = categoryRepository.findByCode("MEDICAL_RECORD").orElse(null);
        ConditionModelCategory vitalCat = categoryRepository.findByCode("VITAL_SIGNS").orElse(null);

        List<ConditionModel> models = conditionModelRepository.findAll();
        for (ConditionModel cm : models) {
            if (cm.getCategoryId() != null) continue;
            String code = cm.getCode();
            if (code == null) continue;
            if (code.equals("PATIENT_AGE") || code.equals("PATIENT_GENDER")) {
                cm.setCategoryId(patientCat != null ? patientCat.getId() : null);
            } else if (code.equals("DIAGNOSIS_CODE")) {
                cm.setCategoryId(diagnosisCat != null ? diagnosisCat.getId() : null);
            } else if (code.equals("DRUG_ORDER")) {
                cm.setCategoryId(ordersCat != null ? ordersCat.getId() : null);
            } else if (code.equals("EMR_CONTENT")) {
                cm.setCategoryId(emrCat != null ? emrCat.getId() : null);
            } else if (code.equals("VITAL_BP") || code.equals("VITAL_TEMP")) {
                cm.setCategoryId(vitalCat != null ? vitalCat.getId() : null);
            }
            conditionModelRepository.save(cm);
            log.info("修复条件模型 [{}] 的分类为 {}", code, cm.getCategoryId());
        }
    }

    private DataElement findDe(String code) {
        return dataElementRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("数据元不存在: " + code));
    }

    private void saveConditionModel(Long dataElementId, Long categoryId, java.util.List<String> operators,
                                     ValueSource valueSource, NodeUsage nodeUsage) {
        ConditionModel model = new ConditionModel();
        model.setDataElementId(dataElementId);
        model.setCategoryId(categoryId);
        model.setOperators(operators);
        model.setValueSource(valueSource);
        model.setNodeUsage(nodeUsage);

        if (dataElementId != null) {
            DataElement de = dataElementRepository.findById(dataElementId)
                    .orElseThrow(() -> new RuntimeException("数据元不存在: " + dataElementId));
            model.setCode(de.getCode());
            model.setName(de.getName());
            model.setDataType(de.getDataType());
        } else {
            // 结果节点，没有关联数据元，使用默认编码
            model.setCode("RESULT_" + System.currentTimeMillis());
            model.setName("结果条件");
            model.setDataType(DataType.DICTIONARY);
        }

        conditionModelRepository.save(model);
    }
}
