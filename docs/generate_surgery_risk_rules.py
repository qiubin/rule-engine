#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
手术高风险规则自动生成脚本 v2
支持多词诊断条件的 OR 节点拆分
"""

import openpyxl
import json
import re
import sys
import os

# 加载手术字典映射（名称 -> 编码）
SURGERY_DICT_MAP = {}
SURGERY_DICT_PATH = os.path.join(os.path.dirname(__file__), 'icd9_cm3_surgery_map.json')
if os.path.exists(SURGERY_DICT_PATH):
    with open(SURGERY_DICT_PATH, 'r', encoding='utf-8') as f:
        SURGERY_DICT_MAP = json.load(f)
    print(f"已加载手术字典映射: {len(SURGERY_DICT_MAP)} 条")
else:
    print(f"警告: 未找到手术字典映射文件 {SURGERY_DICT_PATH}")

LAB_FIELD_MAP = {
    "LVEF": "lvefValue",
    "PT": "ptValue",
    "APTT": "apttValue",
    "血小板计数": "plateletCount",
    "BUN": "bunValue",
    "血尿素氮": "bunValue",
    "血肌酐": "creatinineValue",
    "Cr": "creatinineValue",
    "GFR": "gfrValue",
    "eGFR": "gfrValue",
    "血肾小球滤过率": "gfrValue",
    "内生肌酐清除率": "creatinineClearance",
    "凝血酶原时间": "ptValue",
}

EXAM_FIELD_MAP = {
    "LVEF": "lvefValue",
}

ASSESSMENT_FIELD_MAP = {
    "ECOG体力状态": "ecogScore",
    "Wells评分": "wellsScore",
    "简化肺栓塞wells评分": "wellsScore",
}


def parse_operator_value(val_str):
    """解析条件值字符串，提取操作符和阈值"""
    if not val_str:
        return None, None
    val_str = str(val_str).strip()
    
    patterns = [
        (r'大于等于([\d.]+)', '>=', 1),
        (r'大于([\d.]+)', '>', 1),
        (r'小于等于([\d.]+)', '<=', 1),
        (r'小于([\d.]+)', '<', 1),
        (r'等于([\d.]+)', '==', 1),
        (r'≥([\d.]+)', '>=', 1),
        (r'>([\d.]+)', '>', 1),
        (r'≤([\d.]+)', '<=', 1),
        (r'<([\d.]+)', '<', 1),
        (r'=([\d.]+)', '==', 1),
    ]
    
    for pattern, op, group in patterns:
        match = re.search(pattern, val_str)
        if match:
            return op, match.group(group)
    
    match = re.match(r'(\d+)分', val_str)
    if match:
        return '>=', match.group(1)
    
    match = re.search(r'([\d.]+)%', val_str)
    if match:
        if '小于' in val_str:
            return '<', match.group(1)
        if '大于' in val_str:
            return '>', match.group(1)
        return '<', match.group(1)
    
    return None, val_str


def get_field_for_condition(cat, content):
    if cat == "手术":
        return "surgeryOrder"
    if cat == "年龄":
        return "patientAge"
    if cat == "性别":
        return "patientGender"
    if cat == "诊断":
        return "diagnosisList"
    if cat == "用药史":
        return "drugNames"
    if cat == "过敏史" or cat == "个人史":
        return "pastHistory"

    content_str = str(content) if content else ""

    def find_field(field_map):
        # 按关键词长度降序，避免短关键词误匹配（如 PT 匹配到 APTT）
        for key, field in sorted(field_map.items(), key=lambda x: -len(x[0])):
            if key in content_str:
                return field
        return None

    if cat == "检验细项":
        field = find_field(LAB_FIELD_MAP)
        return field if field else "labResultValue"

    if cat == "检查结果值":
        field = find_field(EXAM_FIELD_MAP)
        if field:
            return field
        field = find_field(LAB_FIELD_MAP)
        if field:
            return field
        return "lvefValue"

    if cat == "评估量表":
        field = find_field(ASSESSMENT_FIELD_MAP)
        return field if field else "ecogScore"

    return "emrContent"


def get_operator_for_condition(cat, val_str):
    if cat == "诊断":
        return "arrayContains"
    if cat in ["手术", "用药史", "过敏史", "个人史"]:
        return "contains"
    if cat == "性别":
        return "=="
    
    op, val = parse_operator_value(val_str)
    if op:
        return op
    
    if cat in ["检验细项", "检查结果值", "评估量表", "年龄"]:
        return ">"
    return "contains"


def build_canvas_data(rule_name, flat_conditions, result_content, forbidden_level):
    """
    flat_conditions: [(field, operator, value, cat, content), ...]
    每个条件是一个独立节点，多词诊断已被拆分
    """
    nodes = []
    edges = []
    node_id_counter = [1]
    
    def next_id(prefix):
        nid = f"{prefix}_{node_id_counter[0]}"
        node_id_counter[0] += 1
        return nid
    
    # Start 节点
    start_id = next_id("start")
    nodes.append({
        "id": start_id,
        "type": "start",
        "position": {"x": 250, "y": 50},
        "data": {"label": "开始"}
    })
    
    # 构建条件链
    prev_id = start_id
    y_pos = 150
    
    for field, operator, value, cat, content in flat_conditions:
        cond_id = next_id("cond")
        
        node_label = f"{cat}:{content}" if content else f"{cat}"
        if len(node_label) > 30:
            node_label = node_label[:27] + "..."
        
        nodes.append({
            "id": cond_id,
            "type": "condition",
            "position": {"x": 250, "y": y_pos},
            "data": {
                "label": node_label,
                "conditionConfig": {
                    "field": field,
                    "operator": operator,
                    "value": str(value) if value is not None else "",
                    "valueSource": "ADAPTER",
                    "dictAttr": "itemName"
                }
            }
        })
        
        edges.append({
            "id": f"e_{prev_id}_{cond_id}",
            "source": prev_id,
            "target": cond_id,
            "label": ""
        })
        
        prev_id = cond_id
        y_pos += 100
    
    # Result 节点
    result_id = next_id("result")
    result_type = "QC_VETO" if forbidden_level == "绝对禁忌" else "QC_DEFECT"
    result_value = forbidden_level if forbidden_level else "提醒"
    
    nodes.append({
        "id": result_id,
        "type": "result",
        "position": {"x": 250, "y": y_pos},
        "data": {
            "label": result_value,
            "resultConfig": {
                "resultType": result_type,
                "resultValue": result_value,
                "content": result_content or "",
                "priority": 10 if result_type == "QC_VETO" else 2
            }
        }
    })
    
    edges.append({
        "id": f"e_{prev_id}_{result_id}",
        "source": prev_id,
        "target": result_id,
        "sourceHandle": "true",
        "label": "是"
    })
    
    return {"nodes": nodes, "edges": edges}


def build_canvas_with_or(rule_name, or_groups, result_content, forbidden_level):
    """
    or_groups: [ [(field, op, val, cat, content), ...], [(field, op, val, cat, content), ...], ... ]
    每个子列表是一个OR组，组内条件用OR节点连接
    各OR组之间用AND连接（串联）
    """
    nodes = []
    edges = []
    node_id_counter = [1]
    
    def next_id(prefix):
        nid = f"{prefix}_{node_id_counter[0]}"
        node_id_counter[0] += 1
        return nid
    
    start_id = next_id("start")
    nodes.append({
        "id": start_id, "type": "start",
        "position": {"x": 250, "y": 50},
        "data": {"label": "开始"}
    })
    
    prev_id = start_id
    y_pos = 150
    
    for group in or_groups:
        if len(group) == 1:
            # 单条件，直接连接
            field, operator, value, cat, content = group[0]
            cond_id = next_id("cond")
            node_label = f"{cat}:{content}" if content else cat
            if len(node_label) > 30:
                node_label = node_label[:27] + "..."
            
            cond_config = {
                "field": field, "operator": operator,
                "value": str(value) if value is not None else "",
                "valueSource": "ADAPTER", "dictAttr": "itemName"
            }
            if cat == "手术" and str(value) in SURGERY_DICT_MAP:
                cond_config["dictCode"] = "ICD9_CM3_SURGERY"
                cond_config["dictItemCode"] = SURGERY_DICT_MAP[str(value)]

            nodes.append({
                "id": cond_id, "type": "condition",
                "position": {"x": 250, "y": y_pos},
                "data": {
                    "label": node_label,
                    "conditionConfig": cond_config
                }
            })
            edges.append({
                "id": f"e_{prev_id}_{cond_id}",
                "source": prev_id, "target": cond_id, "label": ""
            })
            prev_id = cond_id
            y_pos += 100
        else:
            # 多条件，用OR节点
            or_id = next_id("or")
            nodes.append({
                "id": or_id, "type": "or",
                "position": {"x": 250, "y": y_pos},
                "data": {"label": "或"}
            })
            edges.append({
                "id": f"e_{prev_id}_{or_id}",
                "source": prev_id, "target": or_id, "label": ""
            })
            
            cond_ids = []
            for i, (field, operator, value, cat, content) in enumerate(group):
                cond_id = next_id("cond")
                cond_ids.append(cond_id)
                node_label = f"{cat}:{content}" if content else cat
                if len(node_label) > 30:
                    node_label = node_label[:27] + "..."
                
                cond_config = {
                    "field": field, "operator": operator,
                    "value": str(value) if value is not None else "",
                    "valueSource": "ADAPTER", "dictAttr": "itemName"
                }
                if cat == "手术" and str(value) in SURGERY_DICT_MAP:
                    cond_config["dictCode"] = "ICD9_CM3_SURGERY"
                    cond_config["dictItemCode"] = SURGERY_DICT_MAP[str(value)]

                nodes.append({
                    "id": cond_id, "type": "condition",
                    "position": {"x": 100 + i * 300, "y": y_pos + 80},
                    "data": {
                        "label": node_label,
                        "conditionConfig": cond_config
                    }
                })
                edges.append({
                    "id": f"e_{cond_id}_{or_id}",
                    "source": cond_id, "target": or_id, "label": ""
                })
            
            prev_id = or_id
            y_pos += 160
    
    # Result 节点
    result_id = next_id("result")
    result_type = "QC_VETO" if forbidden_level == "绝对禁忌" else "QC_DEFECT"
    result_value = forbidden_level if forbidden_level else "提醒"
    
    nodes.append({
        "id": result_id, "type": "result",
        "position": {"x": 250, "y": y_pos},
        "data": {
            "label": result_value,
            "resultConfig": {
                "resultType": result_type,
                "resultValue": result_value,
                "content": result_content or "",
                "priority": 10 if result_type == "QC_VETO" else 2
            }
        }
    })
    edges.append({
        "id": f"e_{prev_id}_{result_id}",
        "source": prev_id, "target": result_id,
        "sourceHandle": "true", "label": "是"
    })
    
    return {"nodes": nodes, "edges": edges}


def sanitize_for_sql(text):
    if text is None:
        return ""
    return str(text).replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ")


def generate_sql(rules, limit=None):
    sql_lines = []
    sql_lines.append("-- 手术高风险规则自动生成 SQL")
    sql_lines.append("-- 生成时间: 2026-05-14")
    sql_lines.append("")
    sql_lines.append("-- 先确保规则类型存在")
    sql_lines.append("INSERT IGNORE INTO rule_type (code, name, description, sort_order) VALUES ('SURGERY_RISK', '手术高风险', '手术高风险规则（禁忌/预警）', 6);")
    sql_lines.append("")
    sql_lines.append("SET @rule_type_id = (SELECT id FROM rule_type WHERE code = 'SURGERY_RISK');")
    sql_lines.append("")
    sql_lines.append("DELETE FROM rule WHERE rule_type_id = @rule_type_id;")
    sql_lines.append("")

    count = 0
    for i, rule in enumerate(rules):
        if limit and count >= limit:
            break

        rule_cat = rule[0] or ""
        cat1 = rule[1] or ""
        content1 = rule[2] or ""
        val1 = rule[3] or ""
        cat2 = rule[4] or ""
        content2 = rule[5] or ""
        val2 = rule[6] or ""
        cat3 = rule[7] or ""
        content3 = rule[8] or ""
        val3 = rule[9] or ""

        reminder = rule[16] or ""
        forbidden_level = rule[17] or ""

        # 构建线性条件列表（所有条件之间都是 AND 串联）
        flat_conditions = []

        for cat, content, val in [(cat1, content1, val1), (cat2, content2, val2), (cat3, content3, val3)]:
            if not cat:
                continue

            field = get_field_for_condition(cat, content)
            operator = get_operator_for_condition(cat, val)

            if cat == "诊断" and content:
                # 多词诊断用逗号拼接，在一个条件节点中用 arrayContains 匹配
                diag_items = [d.strip() for d in str(content).split('/') if d.strip()]
                combined_value = ",".join(diag_items[:10])  # 最多10个
                flat_conditions.append((field, operator, combined_value, cat, content))
            elif cat == "手术" and content:
                # 手术条件：取第一个手术名
                surgery_name = str(content).split(',')[0].strip()
                flat_conditions.append((field, operator, surgery_name, cat, surgery_name))
            else:
                op_parsed, val_parsed = parse_operator_value(val)
                value = val_parsed if val_parsed else val
                flat_conditions.append((field, operator, value, cat, content))

        if len(flat_conditions) < 1:
            continue

        # 规则名称
        surgery_name = ""
        for cat, content, val in [(cat1, content1, val1), (cat2, content2, val2), (cat3, content3, val3)]:
            if cat == "手术" and content:
                surgery_name = str(content).split(',')[0].strip()
                break

        rule_name = surgery_name[:40] if surgery_name else f"手术风险规则_{i+1}"
        rule_code = f"SR_{i+1:05d}"

        # 构建画布（纯线性串联，无 OR 节点）
        canvas = build_canvas_data(rule_name, flat_conditions, reminder, forbidden_level)
        canvas_json = json.dumps(canvas, ensure_ascii=False)

        sql = (
            f"INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl, remark, created_at, updated_at) "
            f"VALUES ('{sanitize_for_sql(rule_code)}', '{sanitize_for_sql(rule_name)}', '1.0.0', 'DRAFT', @rule_type_id, "
            f"'{sanitize_for_sql(canvas_json)}', NULL, '{sanitize_for_sql(forbidden_level)}', NOW(), NOW());"
        )
        sql_lines.append(sql)
        count += 1

        if count % 500 == 0:
            sql_lines.append("")

    sql_lines.append("")
    sql_lines.append(f"-- 共生成 {count} 条规则")

    return "\n".join(sql_lines)


def main():
    excel_path = "docs/手术高风险规则.xlsx"
    limit = None
    if len(sys.argv) > 1:
        excel_path = sys.argv[1]
    if len(sys.argv) > 2:
        limit = int(sys.argv[2])
    
    print(f"读取 Excel: {excel_path}")
    wb = openpyxl.load_workbook(excel_path)
    ws = wb["新-高风险手术规则"]
    
    rules = []
    for i, row in enumerate(ws.iter_rows(values_only=True), 1):
        if i == 1:
            continue
        if row[0]:
            rules.append(row)
    
    print(f"读取到 {len(rules)} 条规则")
    if limit:
        print(f"限制生成前 {limit} 条")
    
    sql = generate_sql(rules, limit)
    
    output_path = "docs/surgery_risk_rules.sql"
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(sql)
    
    print(f"SQL 已生成: {output_path}")
    print(f"文件大小: {len(sql) / 1024 / 1024:.2f} MB")
    
    stats = {"诊断": 0, "检验细项": 0, "检查结果值": 0, "评估量表": 0, "手术": 0, "年龄": 0, "性别": 0}
    for r in rules[:limit] if limit else rules:
        for cat in [r[1], r[4], r[7]]:
            if cat in stats:
                stats[cat] += 1
    
    print("\n条件类型统计:")
    for k, v in stats.items():
        if v > 0:
            print(f"  {k}: {v} 条")


if __name__ == "__main__":
    main()
