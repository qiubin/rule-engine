-- 病历质控规则名称与备注规范化更新
-- 规则名称 = 质控指标
-- 备注 = 实现方法关键词 + 对应计算符 + 配置要点

UPDATE rule SET
    name = '主诉无持续时间描述',
    remark = '正则匹配 | regex_not_match | 正则=.*(时|月|周|天|年|日).*'
WHERE code = 'QC_CHIEF_DURATION';

UPDATE rule SET
    name = '缺现病史',
    remark = '为空校验 | isBlank | 字段=presentIllnessHistory'
WHERE code = 'QC_MISSING_PRESENT_ILLNESS';

UPDATE rule SET
    name = '体格检查脉搏与心率矛盾',
    remark = '数值比较 | fieldCompare | 比对类型=NUMERIC_DIFF, 操作符!=, 字段A=pulse, 字段B=heartRate, 阈值=0'
WHERE code = 'QC_PULSE_HEARTRATE';

UPDATE rule SET
    name = '入院时间与病史采集时间差',
    remark = '时间差 | fieldCompare | 比对类型=TIME_DIFF_HOUR, 操作符>, 字段A=admissionTime, 字段B=medicalHistoryTime, 阈值=2'
WHERE code = 'QC_ADMISSION_TIME_DIFF';
