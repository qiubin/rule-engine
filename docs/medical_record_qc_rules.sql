-- 病历内涵质控十条规则
-- 规则类型: MEDICAL_RECORD_QC
-- 执行方式: 在 MySQL 中执行此 SQL，然后在前端规则管理页面逐个发布

SET @rule_type_id = (SELECT id FROM rule_type WHERE code = 'MEDICAL_RECORD_QC');

DELETE FROM rule WHERE code LIKE 'MR_QC_%';

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_001', '主诉无持续时间描述', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-1", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-1", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "主诉含持续时间", "conditionConfig": {"field": "主诉", "operator": "regex_match", "value": ".*[时月周天年日].*", "valueSource": "PARAM"}}}, {"id": "res-pass-1", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "主诉包含持续时间描述"}}}, {"id": "res-fail-1", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "主诉缺少持续时间描述"}}}], "edges": [{"id": "e1-1", "source": "start-1", "target": "cond-1"}, {"id": "e1-2", "source": "cond-1", "target": "res-pass-1", "sourceHandle": "true", "label": "是"}, {"id": "e1-3", "source": "cond-1", "target": "res-fail-1", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_002', '缺现病史', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-2", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-2", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "现病史为空", "conditionConfig": {"field": "现病史", "operator": "isBlank", "value": "", "valueSource": "PARAM"}}}, {"id": "res-pass-2", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "现病史已填写"}}}, {"id": "res-fail-2", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "病历缺少现病史"}}}], "edges": [{"id": "e2-1", "source": "start-2", "target": "cond-2"}, {"id": "e2-2", "source": "cond-2", "target": "res-fail-2", "sourceHandle": "true", "label": "是"}, {"id": "e2-3", "source": "cond-2", "target": "res-pass-2", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_003', '体格检查为空', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-3", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-3", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "体格检查为空", "conditionConfig": {"field": "体格检查", "operator": "isBlank", "value": "", "valueSource": "PARAM"}}}, {"id": "res-pass-3", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "体格检查已填写"}}}, {"id": "res-fail-3", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "体格检查记录有漏项"}}}], "edges": [{"id": "e3-1", "source": "start-3", "target": "cond-3"}, {"id": "e3-2", "source": "cond-3", "target": "res-fail-3", "sourceHandle": "true", "label": "是"}, {"id": "e3-3", "source": "cond-3", "target": "res-pass-3", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_004', '辅助检查缺时间描述', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-4", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-4", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "辅助检查含时间", "conditionConfig": {"field": "辅助检查", "operator": "regex_match", "value": ".*(\\d{4}[-._]\\d{1,2}[-._]\\d{1,2}|年.{1,2}月.{1,2}日|年.{1,2}月|\\d{1,4}(－|-|\\.|_)\\d{1,2}).*", "valueSource": "PARAM"}}}, {"id": "res-pass-4", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "辅助检查含时间描述"}}}, {"id": "res-fail-4", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "辅助检查缺少检查时间描述"}}}], "edges": [{"id": "e4-1", "source": "start-4", "target": "cond-4"}, {"id": "e4-2", "source": "cond-4", "target": "res-pass-4", "sourceHandle": "true", "label": "是"}, {"id": "e4-3", "source": "cond-4", "target": "res-fail-4", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_005', '首次病程诊断依据为空', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-5", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-5", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "诊断依据为空", "conditionConfig": {"field": "诊断依据", "operator": "isBlank", "value": "", "valueSource": "PARAM"}}}, {"id": "res-pass-5", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "诊断依据已填写"}}}, {"id": "res-fail-5", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "首次病程诊断依据有缺陷"}}}], "edges": [{"id": "e5-1", "source": "start-5", "target": "cond-5"}, {"id": "e5-2", "source": "cond-5", "target": "res-fail-5", "sourceHandle": "true", "label": "是"}, {"id": "e5-3", "source": "cond-5", "target": "res-pass-5", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_006', '诊疗计划为空', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-6", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-6", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "诊疗计划为空", "conditionConfig": {"field": "诊疗计划", "operator": "isBlank", "value": "", "valueSource": "PARAM"}}}, {"id": "res-pass-6", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "诊疗计划已填写"}}}, {"id": "res-fail-6", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "诊疗计划有缺陷"}}}], "edges": [{"id": "e6-1", "source": "start-6", "target": "cond-6"}, {"id": "e6-2", "source": "cond-6", "target": "res-fail-6", "sourceHandle": "true", "label": "是"}, {"id": "e6-3", "source": "cond-6", "target": "res-pass-6", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_007', '输血记录缺输血效果及后续记录', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-7", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-7", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "输血效果为空", "conditionConfig": {"field": "输血效果及后续记录", "operator": "isBlank", "value": "", "valueSource": "PARAM"}}}, {"id": "res-pass-7", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "输血效果记录已填写"}}}, {"id": "res-fail-7", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "输血记录缺少输血效果及后续计划"}}}], "edges": [{"id": "e7-1", "source": "start-7", "target": "cond-7"}, {"id": "e7-2", "source": "cond-7", "target": "res-fail-7", "sourceHandle": "true", "label": "是"}, {"id": "e7-3", "source": "cond-7", "target": "res-pass-7", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_008', '现病史缺一般情况描述', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-8", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-8", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "现病史含一般情况", "conditionConfig": {"field": "现病史", "operator": "regex_match", "value": ".*(二便|一般状况|精神|饮食|睡眠|大便|小便|脉搏|呼吸|体温|未诉不适|一般情况|生命体征|神志).*", "valueSource": "PARAM"}}}, {"id": "res-pass-8", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "现病史包含一般情况描述"}}}, {"id": "res-fail-8", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "现病史缺少一般情况描述"}}}], "edges": [{"id": "e8-1", "source": "start-8", "target": "cond-8"}, {"id": "e8-2", "source": "cond-8", "target": "res-pass-8", "sourceHandle": "true", "label": "是"}, {"id": "e8-3", "source": "cond-8", "target": "res-fail-8", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_009', '阴性体征数量不足', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-9", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-9", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "阴性体征数小于5", "conditionConfig": {"field": "NEGATIVE_SIGNS", "operator": "arrayLength", "value": "<", "extraValue1": "5", "valueSource": "PARAM"}}}, {"id": "res-pass-9", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "阴性体征记录充分"}}}, {"id": "res-fail-9", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "缺少有鉴别诊断意义的阴性体征"}}}], "edges": [{"id": "e9-1", "source": "start-9", "target": "cond-9"}, {"id": "e9-2", "source": "cond-9", "target": "res-fail-9", "sourceHandle": "true", "label": "是"}, {"id": "e9-3", "source": "cond-9", "target": "res-pass-9", "sourceHandle": "false", "label": "否"}]}', NULL
);

INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (
  'MR_QC_010', '主诉与现病史症状不一致', '1.0.0', 'DRAFT', @rule_type_id,
  '{"nodes": [{"id": "start-10", "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}}, {"id": "cond-10", "type": "condition", "position": {"x": 300, "y": 200}, "data": {"label": "症状交集为空", "conditionConfig": {"field": "主诉阳性症状", "operator": "arrayIntersect", "value": "现病史阳性症状", "extraValue1": "==", "extraValue2": "0", "valueSource": "PARAM"}}}, {"id": "res-pass-10", "type": "result", "position": {"x": 600, "y": 150}, "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": "主诉与现病史症状一致"}}}, {"id": "res-fail-10", "type": "result", "position": {"x": 600, "y": 250}, "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": "现病史主要症状与主诉描述不一致"}}}], "edges": [{"id": "e10-1", "source": "start-10", "target": "cond-10"}, {"id": "e10-2", "source": "cond-10", "target": "res-fail-10", "sourceHandle": "true", "label": "是"}, {"id": "e10-3", "source": "cond-10", "target": "res-pass-10", "sourceHandle": "false", "label": "否"}]}', NULL
);

-- 验证
SELECT id, code, name, status FROM rule WHERE code LIKE 'MR_QC_%' ORDER BY code;
