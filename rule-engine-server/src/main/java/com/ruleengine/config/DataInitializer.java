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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    private void ensureExecutionLogTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rule_execution_log", Integer.class);
        } catch (Exception e) {
            log.info("Creating missing 'rule_execution_log' table...");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS rule_execution_log (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  rule_id BIGINT NOT NULL," +
                "  rule_code VARCHAR(64)," +
                "  rule_version VARCHAR(16)," +
                "  params_json LONGTEXT," +
                "  output_json LONGTEXT," +
                "  hit_node_ids LONGTEXT," +
                "  fired_count INT," +
                "  duration_ms BIGINT," +
                "  status VARCHAR(16) NOT NULL," +
                "  error_message LONGTEXT," +
                "  executed_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_rule_id (rule_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("'rule_execution_log' table created.");
        }
    }

    private void ensureRuleVersionTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rule_version", Integer.class);
        } catch (Exception e) {
            log.info("Creating missing 'rule_version' table...");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS rule_version (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  rule_id BIGINT NOT NULL," +
                "  version INT NOT NULL," +
                "  canvas_data LONGTEXT," +
                "  drools_drl LONGTEXT," +
                "  change_note VARCHAR(255)," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_rule_id (rule_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            log.info("'rule_version' table created.");
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
        ensureExecutionLogTableExists();
        ensureRuleVersionTableExists();
        migrateDataElementIds();
        log.info("开始检查并初始化基础数据...");

        initDictionaries();
        initDataSets();
        initDataElements();
        initRuleTypes();
        initConditionModelCategories();
        initConditionModels();
        syncResultCategory();
        initForbiddenTypeCondition();

        log.info("基础数据初始化完成");
    }

    private void initDictionaries() {
        if (dictionaryRepository.count() > 0) {
            log.info("字典数据已存在，跳过");
            return;
        }
        saveDictionary("GENDER", "性别", new Object[][]{{"M", "男", "1"}, {"F", "女", "2"}, {"U", "未知", "9"}});
        initIcd10Dictionary();
        initMedicalDictionaries();
        saveDictionary("NURSING_LEVEL", "护理分级", new Object[][]{{"LEVEL_1", "特级护理", "1"}, {"LEVEL_2", "一级护理", "2"}, {"LEVEL_3", "二级护理", "3"}, {"LEVEL_4", "三级护理", "4"}});
        saveDictionary("FORBIDDEN_TYPE", "禁忌类别", new Object[][]{{"ABSOLUTE", "绝对禁忌", "ABSOLUTE"}, {"RELATIVE", "相对禁忌", "RELATIVE"}, {"WARNING", "预警提醒", "WARNING"}, {"MESSAGE", "消息提醒", "MESSAGE"}});
    }

    private void initMedicalDictionaries() {
        // 阳性症状
        saveDictionary("SYMPTOM_POSITIVE", "阳性症状",
                new Object[][]{
                        {"FEVER", "发热", "发热"},
                        {"HEADACHE", "头痛", "头痛"},
                        {"CHEST_PAIN", "胸痛", "胸痛"},
                        {"DYSPNEA", "呼吸困难", "呼吸困难"},
                        {"COUGH", "咳嗽", "咳嗽"},
                        {"HEMATEMESIS", "呕血", "呕血"},
                        {"HEMATOCHEZIA", "便血", "便血"},
                        {"ABDOMINAL_PAIN", "腹痛", "腹痛"},
                        {"NAUSEA", "恶心", "恶心"},
                        {"VOMITING", "呕吐", "呕吐"},
                        {"DIARRHEA", "腹泻", "腹泻"},
                        {"CONSTIPATION", "便秘", "便秘"},
                        {"EDEMA", "水肿", "水肿"},
                        {"PALPITATION", "心悸", "心悸"},
                        {"DIZZINESS", "头晕", "头晕"},
                        {"FATIGUE", "乏力", "乏力"},
                        {"WEIGHT_LOSS", "消瘦", "消瘦"},
                        {"CHILLS", "寒战", "寒战"},
                        {"SWEATING", "出汗", "出汗"},
                        {"FEVER_NIGHT", "夜间发热", "夜间发热"}
                });

        // 阴性症状
        saveDictionary("SYMPTOM_NEGATIVE", "阴性症状",
                new Object[][]{
                        {"NO_FEVER", "无发热", "无发热"},
                        {"NO_HEADACHE", "无头痛", "无头痛"},
                        {"NO_CHEST_PAIN", "无胸痛", "无胸痛"},
                        {"NO_DYSPNEA", "无呼吸困难", "无呼吸困难"},
                        {"NO_COUGH", "无咳嗽", "无咳嗽"},
                        {"NO_ABDOMINAL_PAIN", "无腹痛", "无腹痛"},
                        {"NO_NAUSEA", "无恶心", "无恶心"},
                        {"NO_VOMITING", "无呕吐", "无呕吐"}
                });

        // 阳性体征
        saveDictionary("SIGN_POSITIVE", "阳性体征",
                new Object[][]{
                        {"TENDERNESS", "压痛", "压痛"},
                        {"REBOUND_TENDERNESS", "反跳痛", "反跳痛"},
                        {"MURPHY_SIGN", "墨菲氏征阳性", "墨菲氏征阳性"},
                        {"MCBURNEY_TENDERNESS", "麦氏点压痛", "麦氏点压痛"},
                        {"HEPATOMEGALY", "肝大", "肝大"},
                        {"SPLENOMEGALY", "脾大", "脾大"},
                        {"LYMPHADENOPATHY", "淋巴结肿大", "淋巴结肿大"},
                        {"EDEMA_LOWER", "双下肢水肿", "双下肢水肿"},
                        {"RALES", "湿啰音", "湿啰音"},
                        {"WHEEZING", "哮鸣音", "哮鸣音"},
                        {"JAUNDICE", "黄疸", "黄疸"},
                        {"HEART_MURMUR", "心脏杂音", "心脏杂音"}
                });

        // 阴性体征
        saveDictionary("SIGN_NEGATIVE", "阴性体征",
                new Object[][]{
                        {"NO_TENDERNESS", "无压痛", "无压痛"},
                        {"NO_REBOUND", "无反跳痛", "无反跳痛"},
                        {"NO_EDEMA", "无水肿", "无水肿"},
                        {"NO_LYMPHADENOPATHY", "无淋巴结肿大", "无淋巴结肿大"},
                        {"NO_WHEEZING", "无哮鸣音", "无哮鸣音"}
                });

        // 否定词
        saveDictionary("NEGATION_WORDS", "否定词",
                new Object[][]{
                        {"NO", "无", "无"},
                        {"DENY", "否认", "否认"},
                        {"NOT_FOUND", "未发现", "未发现"},
                        {"NOT_VISIBLE", "未见", "未见"},
                        {"NOT_HAVE", "没有", "没有"},
                        {"NOT_CLEAR", "不清", "不清"},
                        {"NOT_EXACT", "不明确", "不明确"},
                        {"EXCLUDE", "排除", "排除"},
                        {"NOT_SEE", "未查见", "未查见"},
                        {"WITHOUT", "不伴", "不伴"},
                        {"NEGATIVE", "阴性", "阴性"}
                });

        // 手术
        saveDictionary("SURGERY", "手术",
                new Object[][]{
                        {"APPENDECTOMY", "阑尾切除术", "阑尾切除术"},
                        {"CHOLECYSTECTOMY", "胆囊切除术", "胆囊切除术"},
                        {"THYROIDECTOMY", "甲状腺切除术", "甲状腺切除术"},
                        {"MASTECTOMY", "乳房切除术", "乳房切除术"},
                        {"HYSTERECTOMY", "子宫切除术", "子宫切除术"},
                        {"CABG", "冠状动脉搭桥术", "冠状动脉搭桥术"},
                        {"PCI", "经皮冠状动脉介入治疗", "经皮冠状动脉介入治疗"},
                        {"APPENDECTOMY_LAP", "腹腔镜阑尾切除术", "腹腔镜阑尾切除术"},
                        {"CHOLECYSTECTOMY_LAP", "腹腔镜胆囊切除术", "腹腔镜胆囊切除术"},
                        {"COLECTOMY", "结肠切除术", "结肠切除术"}
                });

        // 检查
        saveDictionary("EXAM", "检查",
                new Object[][]{
                        {"CT", "CT", "CT"},
                        {"MRI", "核磁共振", "核磁共振"},
                        {"X_RAY", "X线", "X线"},
                        {"ULTRASOUND", "超声", "超声"},
                        {"ECG", "心电图", "心电图"},
                        {"EEG", "脑电图", "脑电图"},
                        {"GASTROSCOPY", "胃镜", "胃镜"},
                        {"COLONOSCOPY", "肠镜", "肠镜"},
                        {"BRONCHOSCOPY", "支气管镜", "支气管镜"},
                        {"BLOOD_TEST", "血常规", "血常规"},
                        {"URINE_TEST", "尿常规", "尿常规"},
                        {"BIOCHEMISTRY", "生化全套", "生化全套"},
                        {"TUMOR_MARKER", "肿瘤标志物", "肿瘤标志物"},
                        {"BMP", "B超", "B超"},
                        {"COLOR_DOPPLER", "彩超", "彩超"}
                });

        // 药品（扩展，覆盖常见质控场景）
        saveDictionary("DRUG", "药品",
                new Object[][]{
                        {"PENICILLIN", "青霉素", "PENICILLIN"},
                        {"CEFTRIAXONE", "头孢曲松", "CEFTRIAXONE"},
                        {"METFORMIN", "二甲双胍", "METFORMIN"},
                        {"ASPIRIN", "阿司匹林", "阿司匹林"},
                        {"CLOPIDOGREL", "氯吡格雷", "氯吡格雷"},
                        {"ATORVASTATIN", "阿托伐他汀", "阿托伐他汀"},
                        {"AMLODIPINE", "氨氯地平", "氨氯地平"},
                        {"CAPTOPRIL", "卡托普利", "卡托普利"},
                        {"FUROSEMIDE", "呋塞米", "呋塞米"},
                        {"DIGOXIN", "地高辛", "地高辛"},
                        {"INSULIN", "胰岛素", "胰岛素"},
                        {"GLIPIZIDE", "格列吡嗪", "格列吡嗪"},
                        {"OMEPRAZOLE", "奥美拉唑", "奥美拉唑"},
                        {"PANTOPRAZOLE", "泮托拉唑", "泮托拉唑"},
                        {"MORPHINE", "吗啡", "吗啡"},
                        {"TRAMADOL", "曲马多", "曲马多"},
                        {"PARACETAMOL", "对乙酰氨基酚", "对乙酰氨基酚"},
                        {"CEFAZOLIN", "头孢唑林", "头孢唑林"},
                        {"VANCOMYCIN", "万古霉素", "万古霉素"},
                        {"MEROPENEM", "美罗培南", "美罗培南"}
                });
    }

    private void initIcd10Dictionary() {
        Dictionary dict = new Dictionary();
        dict.setCode("ICD10");
        dict.setName("ICD10诊断编码");
        dictionaryRepository.save(dict);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/data/icd10.txt"), StandardCharsets.UTF_8))) {
            String line;
            int sort = 1;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) continue;
                DictionaryItem di = new DictionaryItem();
                di.setDictCode("ICD10");
                di.setItemCode(parts[0]);
                di.setItemName(parts[1]);
                di.setItemValue(parts[0]);
                di.setSortOrder(sort++);
                di.setDictionary(dict);
                dictionaryItemRepository.save(di);
            }
            log.info("ICD10字典已初始化，共 {} 条", sort - 1);
        } catch (Exception e) {
            log.error("ICD10字典初始化失败", e);
        }
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
        // NLP 衍生数据元
        saveDataElement("POSITIVE_SYMPTOMS", "阳性症状", DataType.STRING_LIST, null, "NLP提取的阳性症状列表", datasetId);
        saveDataElement("NEGATIVE_SYMPTOMS", "阴性症状", DataType.STRING_LIST, null, "NLP提取的阴性症状列表", datasetId);
        saveDataElement("POSITIVE_SIGNS", "阳性体征", DataType.STRING_LIST, null, "NLP提取的阳性体征列表", datasetId);
        saveDataElement("NEGATIVE_SIGNS", "阴性体征", DataType.STRING_LIST, null, "NLP提取的阴性体征列表", datasetId);
        saveDataElement("POSITIVE_DRUGS", "阳性药品", DataType.STRING_LIST, null, "NLP提取的阳性药品列表", datasetId);
        saveDataElement("POSITIVE_EXAMS", "阳性检查", DataType.STRING_LIST, null, "NLP提取的阳性检查列表", datasetId);
        saveDataElement("POSITIVE_SURGERIES", "阳性手术", DataType.STRING_LIST, null, "NLP提取的阳性手术列表", datasetId);
        saveDataElement("POSITIVE_DISEASES", "阳性疾病", DataType.STRING_LIST, null, "NLP提取的阳性疾病列表", datasetId);
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
        saveCategoryIfNotExists("PATIENT_INFO", "患者信息", "患者基本信息相关", 1);
        saveCategoryIfNotExists("ORDERS", "医嘱信息", "医嘱相关", 2);
        saveCategoryIfNotExists("DIAGNOSIS", "诊断信息", "诊断相关", 3);
        saveCategoryIfNotExists("MEDICAL_RECORD", "病历文书", "病历文书相关", 4);
        saveCategoryIfNotExists("VITAL_SIGNS", "生命体征", "生命体征相关", 5);
        saveCategoryIfNotExists("RESULT_CONDITION", "结果条件", "规则执行结果相关条件", 6);
    }

    private void saveCategoryIfNotExists(String code, String name, String description, int sort) {
        if (categoryRepository.findByCode(code).isPresent()) {
            return;
        }
        ConditionModelCategory cat = new ConditionModelCategory();
        cat.setCode(code);
        cat.setName(name);
        cat.setDescription(description);
        cat.setSortOrder(sort);
        categoryRepository.save(cat);
        log.info("创建条件分类: {}", code);
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
        DataElement posSymptoms = findDe("POSITIVE_SYMPTOMS");
        DataElement negSymptoms = findDe("NEGATIVE_SYMPTOMS");
        DataElement posSigns = findDe("POSITIVE_SIGNS");
        DataElement negSigns = findDe("NEGATIVE_SIGNS");

        if (age != null) saveConditionModel(age.getId(), patientCat.getId(), Arrays.asList("==", "!=", ">", "<"), ValueSource.PARAM, NodeUsage.CONDITION);
        if (gender != null) saveConditionModel(gender.getId(), patientCat.getId(), Collections.singletonList("=="), ValueSource.PARAM, NodeUsage.CONDITION);
        if (diagnosis != null) saveConditionModel(diagnosis.getId(), diagnosisCat.getId(), Collections.singletonList("IN_SET"), ValueSource.PARAM, NodeUsage.CONDITION);
        if (drug != null) saveConditionModel(drug.getId(), ordersCat.getId(), Collections.singletonList("IN_SET"), ValueSource.PARAM, NodeUsage.CONDITION);
        saveConditionModel(null, null, Collections.singletonList("=="), ValueSource.PARAM, NodeUsage.RESULT);
        if (emr != null) saveConditionModel(emr.getId(), emrCat.getId(), Arrays.asList("==", "contains", "regex_match", "regexMatch", "existenceConflict", "whitelistMatch", "dictMatch", "medicalNer", "negationCheck", "tokenSimilarity", "allNegated"), ValueSource.ADAPTER, NodeUsage.CONDITION);
        if (bp != null) saveConditionModel(bp.getId(), vitalCat.getId(), Arrays.asList("==", "!=", ">", "<"), ValueSource.PARAM, NodeUsage.BOTH);
        if (temp != null) saveConditionModel(temp.getId(), vitalCat.getId(), Arrays.asList("==", "!=", ">", "<"), ValueSource.PARAM, NodeUsage.BOTH);
        // NLP 衍生数据元的条件模型
        if (posSymptoms != null) saveConditionModel(posSymptoms.getId(), emrCat.getId(), Arrays.asList("contains", "arrayLength", "arrayIntersect"), ValueSource.ADAPTER, NodeUsage.CONDITION);
        if (negSymptoms != null) saveConditionModel(negSymptoms.getId(), emrCat.getId(), Arrays.asList("contains", "arrayLength", "arrayIntersect"), ValueSource.ADAPTER, NodeUsage.CONDITION);
        if (posSigns != null) saveConditionModel(posSigns.getId(), emrCat.getId(), Arrays.asList("contains", "arrayLength", "arrayIntersect"), ValueSource.ADAPTER, NodeUsage.CONDITION);
        if (negSigns != null) saveConditionModel(negSigns.getId(), emrCat.getId(), Arrays.asList("contains", "arrayLength", "arrayIntersect"), ValueSource.ADAPTER, NodeUsage.CONDITION);
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
            } else if (code.equals("POSITIVE_SYMPTOMS") || code.equals("NEGATIVE_SYMPTOMS") ||
                       code.equals("POSITIVE_SIGNS") || code.equals("NEGATIVE_SIGNS") ||
                       code.equals("POSITIVE_DRUGS") || code.equals("POSITIVE_EXAMS") ||
                       code.equals("POSITIVE_SURGERIES") || code.equals("POSITIVE_DISEASES")) {
                cm.setCategoryId(emrCat != null ? emrCat.getId() : null);
            } else if (code.equals("VITAL_BP") || code.equals("VITAL_TEMP")) {
                cm.setCategoryId(vitalCat != null ? vitalCat.getId() : null);
            }
            conditionModelRepository.save(cm);
            log.info("修复条件模型 [{}] 的分类为 {}", code, cm.getCategoryId());
        }
    }

    private DataElement findDe(String code) {
        return dataElementRepository.findByCode(code).orElse(null);
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
            // 优先使用驼峰名作为运行时字段名，兼容导入的数据元
            String fieldCode = (de.getCamelName() != null && !de.getCamelName().isEmpty())
                    ? de.getCamelName() : de.getCode();
            model.setCode(fieldCode);
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

    private void syncResultCategory() {
        categoryRepository.findByCode("RESULT_CONDITION").ifPresent(cat -> {
            boolean changed = false;
            if (!"结果类型".equals(cat.getName())) {
                cat.setName("结果类型");
                changed = true;
            }
            if (!"规则结果相关条件".equals(cat.getDescription())) {
                cat.setDescription("规则结果相关条件");
                changed = true;
            }
            if (changed) {
                categoryRepository.save(cat);
                log.info("更新条件分类 [{}] 为: 结果类型", cat.getCode());
            }
        });
    }

    private void initForbiddenTypeCondition() {
        DataSet generalSet = dataSetRepository.findByCode("GENERAL").orElse(null);
        if (generalSet == null) {
            generalSet = new DataSet();
            generalSet.setCode("GENERAL");
            generalSet.setName("通用数据集");
            generalSet.setDescription("默认通用数据集");
            dataSetRepository.save(generalSet);
            log.info("创建通用数据集");
        }

        DataElement de = dataElementRepository.findByCode("FORBIDDEN_TYPE").orElse(null);
        if (de == null) {
            de = new DataElement();
            de.setCode("FORBIDDEN_TYPE");
            de.setName("禁忌类别");
            de.setDataType(DataType.DICTIONARY);
            de.setDictCode("FORBIDDEN_TYPE");
            de.setDatasetId(generalSet.getId());
            dataElementRepository.save(de);
            log.info("创建数据元: FORBIDDEN_TYPE");
        }

        ConditionModelCategory resultCat = categoryRepository.findByCode("RESULT_CONDITION").orElse(null);
        if (resultCat == null) {
            log.warn("结果类型分类不存在，跳过禁忌类别条件初始化");
            return;
        }

        // 创建/获取“禁忌类别”二级分类
        ConditionModelCategory subCat = categoryRepository.findByCode("FORBIDDEN_TYPE_CAT").orElse(null);
        if (subCat == null) {
            subCat = new ConditionModelCategory();
            subCat.setCode("FORBIDDEN_TYPE_CAT");
            subCat.setName("禁忌类别");
            subCat.setDescription("禁忌类别二级分类");
            subCat.setParentId(resultCat.getId());
            subCat.setSortOrder(1);
            categoryRepository.save(subCat);
            log.info("创建二级分类: 禁忌类别 (parent={})", resultCat.getId());
        } else if (subCat.getParentId() == null) {
            subCat.setParentId(resultCat.getId());
            categoryRepository.save(subCat);
        }

        String fieldCode = (de.getCamelName() != null && !de.getCamelName().isEmpty())
                ? de.getCamelName() : de.getCode();

        // 查找或创建条件模型，categoryId 必须指向二级分类
        ConditionModel existingModel = null;
        List<ConditionModel> allModels = conditionModelRepository.findAll();
        for (ConditionModel m : allModels) {
            if (fieldCode.equals(m.getCode())) {
                existingModel = m;
                break;
            }
        }

        if (existingModel == null) {
            ConditionModel model = new ConditionModel();
            model.setDataElementId(de.getId());
            model.setCategoryId(subCat.getId());
            model.setOperators(Collections.singletonList("=="));
            model.setValueSource(ValueSource.PARAM);
            model.setNodeUsage(NodeUsage.CONDITION);
            model.setCode(fieldCode);
            model.setName(de.getName());
            model.setDataType(de.getDataType());
            conditionModelRepository.save(model);
            log.info("创建条件模型: {}", fieldCode);
        } else if (!subCat.getId().equals(existingModel.getCategoryId())) {
            existingModel.setCategoryId(subCat.getId());
            conditionModelRepository.save(existingModel);
            log.info("更新条件模型 {} 的分类为二级分类: {}", fieldCode, subCat.getId());
        }
    }
}
