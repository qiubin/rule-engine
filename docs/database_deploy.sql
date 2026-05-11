-- ============================================================
-- 规则引擎数据库部署脚本
-- 数据库: ruleengine
-- 字符集: utf8mb4
-- 生成时间: 2026-05-11
-- ============================================================

-- 创建数据库（如不存在）
CREATE DATABASE IF NOT EXISTS ruleengine
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ruleengine;

-- ============================================================
-- 1. 字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS dictionary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dictionary_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_code VARCHAR(64) NOT NULL,
  item_code VARCHAR(64) NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  item_value VARCHAR(128),
  sort_order INT DEFAULT 0,
  status VARCHAR(16) DEFAULT 'ENABLED',
  dict_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_dict_code (dict_code),
  INDEX idx_dict_id (dict_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. 数据集与数据元
-- ============================================================
CREATE TABLE IF NOT EXISTS data_set (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  english_name VARCHAR(128),
  cat_l1_code VARCHAR(32),
  cat_l1_name VARCHAR(128),
  cat_l2_code VARCHAR(32),
  cat_l2_name VARCHAR(128),
  cat_l3_code VARCHAR(32),
  cat_l3_name VARCHAR(128),
  sort_order INT,
  description VARCHAR(512),
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_element (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  data_type VARCHAR(32) NOT NULL,
  dict_code VARCHAR(64),
  standard_code VARCHAR(64),
  english_name VARCHAR(128),
  camel_name VARCHAR(128),
  definition TEXT,
  sort_order INT,
  sensitivity VARCHAR(16),
  description VARCHAR(512),
  metadata TEXT,
  dataset_id BIGINT,
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_dataset_id (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 规则类型
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_type (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. 规则主表（手动创建，避免 JPA CLOB 映射问题）
-- ============================================================
CREATE TABLE IF NOT EXISTS rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  version VARCHAR(16) DEFAULT '1.0.0',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  rule_type_id BIGINT NOT NULL,
  canvas_data LONGTEXT,
  drools_drl LONGTEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_rule_type_id (rule_type_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 规则版本
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  version INT NOT NULL,
  canvas_data LONGTEXT,
  drools_drl LONGTEXT,
  change_note VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rule_id (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. 规则执行日志
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_execution_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  rule_code VARCHAR(64),
  rule_version VARCHAR(16),
  params_json LONGTEXT,
  output_json LONGTEXT,
  hit_node_ids LONGTEXT,
  fired_count INT,
  duration_ms BIGINT,
  status VARCHAR(16) NOT NULL,
  error_message LONGTEXT,
  executed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rule_id (rule_id),
  INDEX idx_executed_at (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. 条件分类
-- ============================================================
CREATE TABLE IF NOT EXISTS condition_model_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  sort_order INT DEFAULT 0,
  parent_id BIGINT,
  status VARCHAR(16) DEFAULT 'ENABLED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8. 条件模型
-- ============================================================
CREATE TABLE IF NOT EXISTS condition_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  data_type VARCHAR(32),
  value_source VARCHAR(16),
  metadata TEXT,
  data_element_id BIGINT,
  category_id BIGINT,
  node_usage VARCHAR(16) DEFAULT 'BOTH',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_code (code),
  INDEX idx_category_id (category_id),
  INDEX idx_data_element_id (data_element_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 条件模型支持的操作符（ElementCollection 表）
CREATE TABLE IF NOT EXISTS condition_model_operators (
  condition_model_id BIGINT NOT NULL,
  operator VARCHAR(255),
  INDEX idx_condition_model_id (condition_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 9. 结果配置
-- ============================================================
CREATE TABLE IF NOT EXISTS result_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  result_type VARCHAR(64) NOT NULL,
  result_name VARCHAR(128) NOT NULL,
  result_category VARCHAR(64),
  content TEXT,
  description VARCHAR(512),
  priority INT NOT NULL DEFAULT 0,
  condition_model_id BIGINT,
  metadata TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_result_type (result_type),
  INDEX idx_condition_model_id (condition_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 初始化数据
-- ============================================================

-- ---------- 字典 ----------
INSERT INTO dictionary (code, name, description, status) VALUES
('GENDER', '性别', NULL, 'ENABLED'),
('ICD10', 'ICD10诊断编码', NULL, 'ENABLED'),
('SYMPTOM_POSITIVE', '阳性症状', NULL, 'ENABLED'),
('SYMPTOM_NEGATIVE', '阴性症状', NULL, 'ENABLED'),
('SIGN_POSITIVE', '阳性体征', NULL, 'ENABLED'),
('SIGN_NEGATIVE', '阴性体征', NULL, 'ENABLED'),
('NEGATION_WORDS', '否定词', NULL, 'ENABLED'),
('SURGERY', '手术', NULL, 'ENABLED'),
('EXAM', '检查', NULL, 'ENABLED'),
('DRUG', '药品', NULL, 'ENABLED'),
('NURSING_LEVEL', '护理分级', NULL, 'ENABLED'),
('FORBIDDEN_TYPE', '禁忌类别', NULL, 'ENABLED')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 性别字典项
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('GENDER', 'M', '男', '1', 1, 'ENABLED'),
('GENDER', 'F', '女', '2', 2, 'ENABLED'),
('GENDER', 'U', '未知', '9', 3, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 护理分级
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('NURSING_LEVEL', 'LEVEL_1', '特级护理', '1', 1, 'ENABLED'),
('NURSING_LEVEL', 'LEVEL_2', '一级护理', '2', 2, 'ENABLED'),
('NURSING_LEVEL', 'LEVEL_3', '二级护理', '3', 3, 'ENABLED'),
('NURSING_LEVEL', 'LEVEL_4', '三级护理', '4', 4, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 禁忌类别
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('FORBIDDEN_TYPE', 'ABSOLUTE', '绝对禁忌', 'ABSOLUTE', 1, 'ENABLED'),
('FORBIDDEN_TYPE', 'RELATIVE', '相对禁忌', 'RELATIVE', 2, 'ENABLED'),
('FORBIDDEN_TYPE', 'WARNING', '预警提醒', 'WARNING', 3, 'ENABLED'),
('FORBIDDEN_TYPE', 'MESSAGE', '消息提醒', 'MESSAGE', 4, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 阳性症状
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('SYMPTOM_POSITIVE', 'FEVER', '发热', '发热', 1, 'ENABLED'),
('SYMPTOM_POSITIVE', 'HEADACHE', '头痛', '头痛', 2, 'ENABLED'),
('SYMPTOM_POSITIVE', 'CHEST_PAIN', '胸痛', '胸痛', 3, 'ENABLED'),
('SYMPTOM_POSITIVE', 'DYSPNEA', '呼吸困难', '呼吸困难', 4, 'ENABLED'),
('SYMPTOM_POSITIVE', 'COUGH', '咳嗽', '咳嗽', 5, 'ENABLED'),
('SYMPTOM_POSITIVE', 'HEMATEMESIS', '呕血', '呕血', 6, 'ENABLED'),
('SYMPTOM_POSITIVE', 'HEMATOCHEZIA', '便血', '便血', 7, 'ENABLED'),
('SYMPTOM_POSITIVE', 'ABDOMINAL_PAIN', '腹痛', '腹痛', 8, 'ENABLED'),
('SYMPTOM_POSITIVE', 'NAUSEA', '恶心', '恶心', 9, 'ENABLED'),
('SYMPTOM_POSITIVE', 'VOMITING', '呕吐', '呕吐', 10, 'ENABLED'),
('SYMPTOM_POSITIVE', 'DIARRHEA', '腹泻', '腹泻', 11, 'ENABLED'),
('SYMPTOM_POSITIVE', 'CONSTIPATION', '便秘', '便秘', 12, 'ENABLED'),
('SYMPTOM_POSITIVE', 'EDEMA', '水肿', '水肿', 13, 'ENABLED'),
('SYMPTOM_POSITIVE', 'PALPITATION', '心悸', '心悸', 14, 'ENABLED'),
('SYMPTOM_POSITIVE', 'DIZZINESS', '头晕', '头晕', 15, 'ENABLED'),
('SYMPTOM_POSITIVE', 'FATIGUE', '乏力', '乏力', 16, 'ENABLED'),
('SYMPTOM_POSITIVE', 'WEIGHT_LOSS', '消瘦', '消瘦', 17, 'ENABLED'),
('SYMPTOM_POSITIVE', 'CHILLS', '寒战', '寒战', 18, 'ENABLED'),
('SYMPTOM_POSITIVE', 'SWEATING', '出汗', '出汗', 19, 'ENABLED'),
('SYMPTOM_POSITIVE', 'FEVER_NIGHT', '夜间发热', '夜间发热', 20, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 阴性症状
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('SYMPTOM_NEGATIVE', 'NO_FEVER', '无发热', '无发热', 1, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_HEADACHE', '无头痛', '无头痛', 2, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_CHEST_PAIN', '无胸痛', '无胸痛', 3, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_DYSPNEA', '无呼吸困难', '无呼吸困难', 4, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_COUGH', '无咳嗽', '无咳嗽', 5, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_ABDOMINAL_PAIN', '无腹痛', '无腹痛', 6, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_NAUSEA', '无恶心', '无恶心', 7, 'ENABLED'),
('SYMPTOM_NEGATIVE', 'NO_VOMITING', '无呕吐', '无呕吐', 8, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 阳性体征
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('SIGN_POSITIVE', 'TENDERNESS', '压痛', '压痛', 1, 'ENABLED'),
('SIGN_POSITIVE', 'REBOUND_TENDERNESS', '反跳痛', '反跳痛', 2, 'ENABLED'),
('SIGN_POSITIVE', 'MURPHY_SIGN', '墨菲氏征阳性', '墨菲氏征阳性', 3, 'ENABLED'),
('SIGN_POSITIVE', 'MCBURNEY_TENDERNESS', '麦氏点压痛', '麦氏点压痛', 4, 'ENABLED'),
('SIGN_POSITIVE', 'HEPATOMEGALY', '肝大', '肝大', 5, 'ENABLED'),
('SIGN_POSITIVE', 'SPLENOMEGALY', '脾大', '脾大', 6, 'ENABLED'),
('SIGN_POSITIVE', 'LYMPHADENOPATHY', '淋巴结肿大', '淋巴结肿大', 7, 'ENABLED'),
('SIGN_POSITIVE', 'EDEMA_LOWER', '双下肢水肿', '双下肢水肿', 8, 'ENABLED'),
('SIGN_POSITIVE', 'RALES', '湿啰音', '湿啰音', 9, 'ENABLED'),
('SIGN_POSITIVE', 'WHEEZING', '哮鸣音', '哮鸣音', 10, 'ENABLED'),
('SIGN_POSITIVE', 'JAUNDICE', '黄疸', '黄疸', 11, 'ENABLED'),
('SIGN_POSITIVE', 'HEART_MURMUR', '心脏杂音', '心脏杂音', 12, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 阴性体征
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('SIGN_NEGATIVE', 'NO_TENDERNESS', '无压痛', '无压痛', 1, 'ENABLED'),
('SIGN_NEGATIVE', 'NO_REBOUND', '无反跳痛', '无反跳痛', 2, 'ENABLED'),
('SIGN_NEGATIVE', 'NO_EDEMA', '无水肿', '无水肿', 3, 'ENABLED'),
('SIGN_NEGATIVE', 'NO_LYMPHADENOPATHY', '无淋巴结肿大', '无淋巴结肿大', 4, 'ENABLED'),
('SIGN_NEGATIVE', 'NO_WHEEZING', '无哮鸣音', '无哮鸣音', 5, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 否定词
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('NEGATION_WORDS', 'NO', '无', '无', 1, 'ENABLED'),
('NEGATION_WORDS', 'DENY', '否认', '否认', 2, 'ENABLED'),
('NEGATION_WORDS', 'NOT_FOUND', '未发现', '未发现', 3, 'ENABLED'),
('NEGATION_WORDS', 'NOT_VISIBLE', '未见', '未见', 4, 'ENABLED'),
('NEGATION_WORDS', 'NOT_HAVE', '没有', '没有', 5, 'ENABLED'),
('NEGATION_WORDS', 'NOT_CLEAR', '不清', '不清', 6, 'ENABLED'),
('NEGATION_WORDS', 'NOT_EXACT', '不明确', '不明确', 7, 'ENABLED'),
('NEGATION_WORDS', 'EXCLUDE', '排除', '排除', 8, 'ENABLED'),
('NEGATION_WORDS', 'NOT_SEE', '未查见', '未查见', 9, 'ENABLED'),
('NEGATION_WORDS', 'WITHOUT', '不伴', '不伴', 10, 'ENABLED'),
('NEGATION_WORDS', 'NEGATIVE', '阴性', '阴性', 11, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 手术
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('SURGERY', 'APPENDECTOMY', '阑尾切除术', '阑尾切除术', 1, 'ENABLED'),
('SURGERY', 'CHOLECYSTECTOMY', '胆囊切除术', '胆囊切除术', 2, 'ENABLED'),
('SURGERY', 'THYROIDECTOMY', '甲状腺切除术', '甲状腺切除术', 3, 'ENABLED'),
('SURGERY', 'MASTECTOMY', '乳房切除术', '乳房切除术', 4, 'ENABLED'),
('SURGERY', 'HYSTERECTOMY', '子宫切除术', '子宫切除术', 5, 'ENABLED'),
('SURGERY', 'CABG', '冠状动脉搭桥术', '冠状动脉搭桥术', 6, 'ENABLED'),
('SURGERY', 'PCI', '经皮冠状动脉介入治疗', '经皮冠状动脉介入治疗', 7, 'ENABLED'),
('SURGERY', 'APPENDECTOMY_LAP', '腹腔镜阑尾切除术', '腹腔镜阑尾切除术', 8, 'ENABLED'),
('SURGERY', 'CHOLECYSTECTOMY_LAP', '腹腔镜胆囊切除术', '腹腔镜胆囊切除术', 9, 'ENABLED'),
('SURGERY', 'COLECTOMY', '结肠切除术', '结肠切除术', 10, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 检查
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('EXAM', 'CT', 'CT', 'CT', 1, 'ENABLED'),
('EXAM', 'MRI', '核磁共振', '核磁共振', 2, 'ENABLED'),
('EXAM', 'X_RAY', 'X线', 'X线', 3, 'ENABLED'),
('EXAM', 'ULTRASOUND', '超声', '超声', 4, 'ENABLED'),
('EXAM', 'ECG', '心电图', '心电图', 5, 'ENABLED'),
('EXAM', 'EEG', '脑电图', '脑电图', 6, 'ENABLED'),
('EXAM', 'GASTROSCOPY', '胃镜', '胃镜', 7, 'ENABLED'),
('EXAM', 'COLONOSCOPY', '肠镜', '肠镜', 8, 'ENABLED'),
('EXAM', 'BRONCHOSCOPY', '支气管镜', '支气管镜', 9, 'ENABLED'),
('EXAM', 'BLOOD_TEST', '血常规', '血常规', 10, 'ENABLED'),
('EXAM', 'URINE_TEST', '尿常规', '尿常规', 11, 'ENABLED'),
('EXAM', 'BIOCHEMISTRY', '生化全套', '生化全套', 12, 'ENABLED'),
('EXAM', 'TUMOR_MARKER', '肿瘤标志物', '肿瘤标志物', 13, 'ENABLED'),
('EXAM', 'BMP', 'B超', 'B超', 14, 'ENABLED'),
('EXAM', 'COLOR_DOPPLER', '彩超', '彩超', 15, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- 药品
INSERT INTO dictionary_item (dict_code, item_code, item_name, item_value, sort_order, status) VALUES
('DRUG', 'PENICILLIN', '青霉素', 'PENICILLIN', 1, 'ENABLED'),
('DRUG', 'CEFTRIAXONE', '头孢曲松', 'CEFTRIAXONE', 2, 'ENABLED'),
('DRUG', 'METFORMIN', '二甲双胍', 'METFORMIN', 3, 'ENABLED'),
('DRUG', 'ASPIRIN', '阿司匹林', 'ASPIRIN', 4, 'ENABLED'),
('DRUG', 'CLOPIDOGREL', '氯吡格雷', 'CLOPIDOGREL', 5, 'ENABLED'),
('DRUG', 'ATORVASTATIN', '阿托伐他汀', 'ATORVASTATIN', 6, 'ENABLED'),
('DRUG', 'AMLODIPINE', '氨氯地平', 'AMLODIPINE', 7, 'ENABLED'),
('DRUG', 'CAPTOPRIL', '卡托普利', 'CAPTOPRIL', 8, 'ENABLED'),
('DRUG', 'FUROSEMIDE', '呋塞米', 'FUROSEMIDE', 9, 'ENABLED'),
('DRUG', 'DIGOXIN', '地高辛', 'DIGOXIN', 10, 'ENABLED'),
('DRUG', 'INSULIN', '胰岛素', 'INSULIN', 11, 'ENABLED'),
('DRUG', 'GLIPIZIDE', '格列吡嗪', 'GLIPIZIDE', 12, 'ENABLED'),
('DRUG', 'OMEPRAZOLE', '奥美拉唑', 'OMEPRAZOLE', 13, 'ENABLED'),
('DRUG', 'PANTOPRAZOLE', '泮托拉唑', 'PANTOPRAZOLE', 14, 'ENABLED'),
('DRUG', 'MORPHINE', '吗啡', 'MORPHINE', 15, 'ENABLED'),
('DRUG', 'TRAMADOL', '曲马多', 'TRAMADOL', 16, 'ENABLED'),
('DRUG', 'PARACETAMOL', '对乙酰氨基酚', 'PARACETAMOL', 17, 'ENABLED'),
('DRUG', 'CEFAZOLIN', '头孢唑林', 'CEFAZOLIN', 18, 'ENABLED'),
('DRUG', 'VANCOMYCIN', '万古霉素', 'VANCOMYCIN', 19, 'ENABLED'),
('DRUG', 'MEROPENEM', '美罗培南', 'MEROPENEM', 20, 'ENABLED')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

-- ---------- 数据集 ----------
INSERT INTO data_set (code, name, description, status) VALUES
('GENERAL', '通用数据集', '默认通用数据集', 'ENABLED')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------- 数据元 ----------
INSERT INTO data_element (code, name, data_type, dict_code, description, dataset_id, status) VALUES
('PATIENT_AGE', '患者年龄', 'NUMERIC', NULL, '患者年龄，单位为岁', 1, 'ENABLED'),
('PATIENT_GENDER', '患者性别', 'DICTIONARY', 'GENDER', NULL, 1, 'ENABLED'),
('DIAGNOSIS_CODE', '诊断编码', 'DICTIONARY_SET', 'ICD10', NULL, 1, 'ENABLED'),
('DRUG_ORDER', '医嘱药品', 'DICTIONARY_SET', 'DRUG', NULL, 1, 'ENABLED'),
('LAB_RESULT_VALUE', '检验结果值', 'NUMERIC', NULL, NULL, 1, 'ENABLED'),
('EMR_CONTENT', '病历文书内容', 'STRING', NULL, NULL, 1, 'ENABLED'),
('VITAL_BP', '血压', 'NUMERIC', NULL, '单位：mmHg', 1, 'ENABLED'),
('VITAL_TEMP', '体温', 'NUMERIC', NULL, '单位：℃', 1, 'ENABLED'),
('POSITIVE_SYMPTOMS', '阳性症状', 'STRING_LIST', NULL, 'NLP提取的阳性症状列表', 1, 'ENABLED'),
('NEGATIVE_SYMPTOMS', '阴性症状', 'STRING_LIST', NULL, 'NLP提取的阴性症状列表', 1, 'ENABLED'),
('POSITIVE_SIGNS', '阳性体征', 'STRING_LIST', NULL, 'NLP提取的阳性体征列表', 1, 'ENABLED'),
('NEGATIVE_SIGNS', '阴性体征', 'STRING_LIST', NULL, 'NLP提取的阴性体征列表', 1, 'ENABLED'),
('POSITIVE_DRUGS', '阳性药品', 'STRING_LIST', NULL, 'NLP提取的阳性药品列表', 1, 'ENABLED'),
('POSITIVE_EXAMS', '阳性检查', 'STRING_LIST', NULL, 'NLP提取的阳性检查列表', 1, 'ENABLED'),
('POSITIVE_SURGERIES', '阳性手术', 'STRING_LIST', NULL, 'NLP提取的阳性手术列表', 1, 'ENABLED'),
('POSITIVE_DISEASES', '阳性疾病', 'STRING_LIST', NULL, 'NLP提取的阳性疾病列表', 1, 'ENABLED'),
('FORBIDDEN_TYPE', '禁忌类别', 'DICTIONARY', 'FORBIDDEN_TYPE', NULL, 1, 'ENABLED')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------- 规则类型 ----------
INSERT INTO rule_type (code, name, description, status) VALUES
('QUALITY_CTRL', '合理性质控', '医疗合理性质控规则', 'ENABLED'),
('MEDICAL_RECORD_QC', '病历质控', '病历内涵质控规则', 'ENABLED'),
('MEDICARE_AUDIT', '医保稽核', '医保审核规则', 'ENABLED'),
('NURSING_DECISION', '护理决策', '护理计划与评估规则', 'ENABLED'),
('VTE_PREVENTION', 'VTE防治', '静脉血栓栓塞防治规则', 'ENABLED')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ---------- 条件分类 ----------
INSERT INTO condition_model_category (code, name, description, sort_order, status) VALUES
('PATIENT_INFO', '患者信息', '患者基本信息相关', 1, 'ENABLED'),
('ORDERS', '医嘱信息', '医嘱相关', 2, 'ENABLED'),
('DIAGNOSIS', '诊断信息', '诊断相关', 3, 'ENABLED'),
('MEDICAL_RECORD', '病历文书', '病历文书相关', 4, 'ENABLED'),
('VITAL_SIGNS', '生命体征', '生命体征相关', 5, 'ENABLED'),
('RESULT_CONDITION', '结果条件', '规则结果相关条件', 6, 'ENABLED')
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_order = VALUES(sort_order);

-- 创建二级分类（禁忌类别）
SET @result_cat_id = (SELECT id FROM condition_model_category WHERE code = 'RESULT_CONDITION');
INSERT INTO condition_model_category (code, name, description, sort_order, parent_id, status)
SELECT 'FORBIDDEN_TYPE_CAT', '禁忌类别', '禁忌类别二级分类', 1, @result_cat_id, 'ENABLED'
FROM DUAL
WHERE @result_cat_id IS NOT NULL
ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = VALUES(parent_id);

-- ---------- 条件模型 ----------
-- 注意：条件模型由 JPA 自动生成，此处仅保留示例结构
-- 实际运行时由 DataInitializer.initConditionModels() 自动创建

-- ============================================================
-- 使用说明
-- ============================================================
-- 1. 执行方式: mysql -h localhost -u root -p ruleengine < database_deploy.sql
-- 2. 如果表已存在，使用 ON DUPLICATE KEY UPDATE 保证幂等
-- 3. ICD10 字典项较多，建议通过 DataInitializer 从 /data/icd10.txt 加载
-- 4. 启动应用后，JPA 会自动创建/更新表结构，并执行 DataInitializer 补齐数据
-- ============================================================
