#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Skill: 病历内涵质控规则自动生成器
用法: python3 docs/generate_qc_rules_skill.py [Excel路径] [规则数量]
示例: python3 docs/generate_qc_rules_skill.py ./uploads/病历内涵质控.xlsx 10
"""

import json
import sys
import os

def load_excel(path):
    """读取Excel文件，返回质控指标列表"""
    try:
        import pandas as pd
        df = pd.read_excel(path)
        records = []
        for _, row in df.iterrows():
            template = str(row.iloc[0]) if pd.notna(row.iloc[0]) else ""
            indicator = str(row.iloc[1]) if pd.notna(row.iloc[1]) else ""
            method = str(row.iloc[2]) if pd.notna(row.iloc[2]) else ""
            if indicator and method and method != "nan":
                records.append({"template": template, "indicator": indicator, "method": method})
        return records
    except ImportError:
        print("错误: 需要安装 pandas 和 openpyxl: pip install pandas openpyxl")
        sys.exit(1)

def generate_rule_code(index):
    return f"MR_QC_{index:03d}"

def build_canvas(rule_name, field, operator, value, extra_value1="", extra_value2=""):
    """构建规则画布JSON"""
    cond_id = f"cond-{rule_name}"
    res_pass_id = f"res-pass-{rule_name}"
    res_fail_id = f"res-fail-{rule_name}"
    start_id = f"start-{rule_name}"

    condition_config = {
        "field": field,
        "operator": operator,
        "value": value,
        "valueSource": "PARAM"
    }
    if extra_value1:
        condition_config["extraValue1"] = extra_value1
    if extra_value2:
        condition_config["extraValue2"] = extra_value2

    nodes = [
        {"id": start_id, "type": "start", "position": {"x": 100, "y": 200}, "data": {"label": "开始"}},
        {"id": cond_id, "type": "condition", "position": {"x": 300, "y": 200},
         "data": {"label": rule_name, "conditionConfig": condition_config}},
        {"id": res_pass_id, "type": "result", "position": {"x": 600, "y": 150},
         "data": {"label": "通过", "resultConfig": {"resultType": "MESSAGE", "resultValue": f"{rule_name}: 通过"}}},
        {"id": res_fail_id, "type": "result", "position": {"x": 600, "y": 250},
         "data": {"label": "质控不通过", "resultConfig": {"resultType": "WARNING", "resultValue": f"{rule_name}: {rule_name}"}}}
    ]

    edges = [
        {"id": f"e-{rule_name}-1", "source": start_id, "target": cond_id},
        {"id": f"e-{rule_name}-2", "source": cond_id, "target": res_pass_id, "sourceHandle": "true", "label": "是"},
        {"id": f"e-{rule_name}-3", "source": cond_id, "target": res_fail_id, "sourceHandle": "false", "label": "否"}
    ]

    return {"nodes": nodes, "edges": edges}

def generate_sql(rules):
    """生成SQL INSERT语句"""
    lines = [
        "-- 自动生成病历内涵质控规则",
        "-- 规则类型: MEDICAL_RECORD_QC",
        "SET @rule_type_id = (SELECT id FROM rule_type WHERE code = 'MEDICAL_RECORD_QC');",
        "DELETE FROM rule WHERE code LIKE 'MR_QC_%';",
        ""
    ]

    for rule in rules:
        canvas_json = json.dumps(rule["canvas"], ensure_ascii=False)
        canvas_sql = canvas_json.replace("'", "''")
        code = rule["code"]
        name = rule["name"]
        lines.append(f"INSERT INTO rule (code, name, version, status, rule_type_id, canvas_data, drools_drl) VALUES (")
        lines.append(f"  '{code}', '{name}', '1.0.0', 'DRAFT', @rule_type_id,")
        lines.append(f"  '{canvas_sql}', NULL")
        lines.append(f");")
        lines.append("")

    lines.append("SELECT id, code, name, status FROM rule WHERE code LIKE 'MR_QC_%' ORDER BY code;")
    return "\n".join(lines)

def main():
    excel_path = sys.argv[1] if len(sys.argv) > 1 else "./uploads/病历内涵质控.xlsx"
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 10

    if not os.path.exists(excel_path):
        print(f"错误: 找不到Excel文件: {excel_path}")
        sys.exit(1)

    records = load_excel(excel_path)
    print(f"读取到 {len(records)} 条质控指标")

    # 规则模板映射表：将质控指标映射为系统可实现的规则
    rule_templates = [
        # (关键词匹配, 字段, 操作符, 值, extra1, extra2, 规则名称)
        ("持续时间", "主诉", "regex_match", ".*[时月周天年日].*", "", "", "主诉无持续时间描述"),
        ("现病史.*空|缺现病史", "现病史", "isBlank", "", "", "", "缺现病史"),
        ("体格检查.*空", "体格检查", "isBlank", "", "", "", "体格检查为空"),
        ("辅助检查.*时间", "辅助检查", "regex_match", ".*(\\d{4}[-._]\\d{1,2}[-._]\\d{1,2}|年.*月.*日|\\d{1,4}[-._]\\d{1,2}).*", "", "", "辅助检查缺时间描述"),
        ("诊断依据.*空|诊断依据.*缺陷", "诊断依据", "isBlank", "", "", "", "首次病程诊断依据为空"),
        ("诊疗计划.*空|诊疗计划.*缺陷", "诊疗计划", "isBlank", "", "", "", "诊疗计划为空"),
        ("输血.*效果", "输血效果及后续记录", "isBlank", "", "", "", "输血记录缺输血效果记录"),
        ("一般情况|二便|精神|饮食|睡眠", "现病史", "regex_match", ".*(二便|一般状况|精神|饮食|睡眠|大便|小便|脉搏|呼吸|体温|未诉不适|一般情况|生命体征|神志).*", "", "", "现病史缺一般情况描述"),
        ("阴性体征.*小于|阴性体征.*不足|阴性体征.*少于", "NEGATIVE_SIGNS", "arrayLength", "<", "5", "", "阴性体征数量不足"),
        ("症状.*不一致|主诉.*现病史.*不一致", "主诉阳性症状", "arrayIntersect", "现病史阳性症状", "==", "0", "主诉与现病史症状不一致"),
        ("起病时间", "现病史", "regex_match", ".*(\\d{4}[-_]\\d{1,2}[-_]\\d{1,2}|入院前.*[天小时周年月分钟]|患者\\d{4}|患者，年|患者，月|患者，日|患者，天).*", "", "", "现病史起病时间描述不准确"),
        ("发病诱因", "现病史", "regex_match", ".*(予以|给|行|治疗|口服|注射|检查|服用|体检|未予.*处理|心电图|B超|彩超|CT|核磁|MR|造影|体检.*发现|检查.*发现|未.*治疗|未.*诊疗|未.*诊治|就诊.*当地|就诊.*院|当地.*就诊|院.*就诊|就医|治疗|诊疗|诊治).*", "", "", "现病史发病诱因描述不清"),
        ("症状性质", "现病史", "regex_match", ".*(时|程度|性质|部位|颜色|诱因|表现|状态).*", "", "", "现病史缺症状性质描述"),
        ("症状程度", "现病史", "regex_match", ".*(程度|性质|范围|发作频率|发热|呼吸困难|咳嗽|咳痰|水肿|轻度|重度|中度).*", "", "", "现病史缺症状程度描述"),
        ("鉴别诊断.*阴性", "NEGATIVE_SIGNS", "arrayLength", "<", "5", "", "缺有鉴别诊断意义的阴性体征"),
        ("疾病发展变化", "现病史", "regex_match", ".*(量|质|范围|诱发|缓解因素|出现|消失|新增|加重|缓解|影响变化).*", "", "", "现病史缺疾病发展变化描述"),
        ("发病后诊治", "现病史", "regex_match", ".*(进一步|当地医院|至|就诊|我院|上级医院|至本院|未系统诊治|自行口服).*", "", "", "现病史缺发病后诊治情况描述"),
        ("伴随病情|伴随症状", "POSITIVE_SYMPTOMS", "arrayLength", "==", "0", "", "缺伴随病情症状与体征描述"),
        ("外院检查.*医院", "辅助检查", "regex_match", ".*(医院|实验室|中心|院|所|科).*", "", "", "外院检查未注明医院名称"),
        ("检查.*结果|辅助检查.*结果", "辅助检查", "regex_match", ".*[考虑可能：:\(（结果示].*", "", "", "辅助检查缺主要检查结果"),
        ("首次病程.*拷贝|病程.*雷同|相似度", "首次病程", "similarity", "首次病程2", "0.995", "", "首次病程记录涉嫌拷贝"),
        ("病程.*相似度|雷同", "当日病程", "similarity", "既往病程", "0.99", "", "二次以上病程记录完全相同"),
        ("病危.*告知|危重.*家属", "病程记录", "regex_match", ".*[告知报明].*[危重患者家属].*", "", "", "危重患者未记录告知情况"),
        ("术后.*注意事项", "术后首次病程", "isBlank", "", "", "", "术后注意事项未记录"),
        ("查房.*补充|查房.*新发现", "查房记录", "regex_match", ".*(追问病史|无补充|确认病史|查体|无新发现|补充诊断|确定诊断|询问病史|病史无补充).*", "", "", "查房记录无补充查体新发现"),
        ("疑难.*主持人", "主持人小结", "lengthCheck", "<", "10", "", "疑难病例讨论无主持人小结"),
        ("病情摘要", "病情摘要", "lengthCheck", "<", "30", "", "疑难病例讨论无病情摘要"),
        ("交班.*接班.*雷同", "交班记录", "similarity", "接班记录", "0.99", "", "交班与接班记录雷同"),
        ("转出.*转入.*雷同", "转出记录", "similarity", "转入记录", "0.99", "", "转出和转入记录雷同"),
        ("危急值.*报告时间", "危急值记录", "regex_match", ".*\\d{4}[-._]\\d{1,2}[-._]\\d{1,2}.*|.*年.*月.*日.*|.*\\d{1,2}:\\d{1,2}.*", "", "", "危急值记录无报告时间"),
        ("术前讨论.*姓名|术前讨论.*职务", "术前讨论记录", "regex_match", ".*姓名.*职务.*|.*职务.*姓名.*", "", "", "术前讨论记录人员信息不全"),
        ("意外.*防范", "手术知情同意书", "isBlank", "", "", "", "术前讨论缺意外及防范措施"),
    ]

    generated = []
    used_indices = set()

    # 按模板匹配生成规则
    rule_idx = 1
    for template in rule_templates:
        if rule_idx > count:
            break
        keywords, field, operator, value, extra1, extra2, name = template

        # 查找匹配的Excel记录
        for i, record in enumerate(records):
            if i in used_indices:
                continue
            indicator = record["indicator"]
            method = record["method"]

            # 简单关键词匹配
            matched = False
            for kw in keywords.split("|"):
                if kw in indicator or kw in method:
                    matched = True
                    break

            if matched:
                code = generate_rule_code(rule_idx)
                canvas = build_canvas(code, field, operator, value, extra1, extra2)
                generated.append({"code": code, "name": name, "canvas": canvas,
                                  "source": indicator, "method": method})
                used_indices.add(i)
                rule_idx += 1
                break

    if not generated:
        print("警告: 未匹配到任何规则，请检查Excel内容或模板配置")
        sys.exit(0)

    sql = generate_sql(generated)

    output_path = "docs/medical_record_qc_rules.sql"
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"\n成功生成 {len(generated)} 条规则，SQL已保存至: {output_path}")
    print("\n规则列表:")
    for r in generated:
        print(f"  {r['code']}: {r['name']}")

    print(f"\n导入命令:")
    print(f"  mysql -h localhost -u root -pqiubin78 ruleengine < {output_path}")
    print(f"\n导入后需在前端规则管理页面逐个发布规则。")

if __name__ == "__main__":
    main()
