# HIS 接口规范 — 病历质控规则引擎对接标准

> 版本: v1.0  
> 更新日期: 2026-05-04  
> 适用范围: 规则引擎与医院 HIS/EMR 系统之间的数据交换

---

## 1. 概述

本规范定义规则引擎在执行病历内涵质控规则时，从 HIS 系统获取患者病历数据的标准接口。

**设计原则:**
- REST JSON over HTTP
- 字段命名采用驼峰式（camelCase），与规则引擎数据元 `camelName` 完全对齐
- 一次性返回患者全量病历数据，减少接口往返
- 支持时间窗过滤（医嘱、病程记录）
- 支持跨病历查询（上一份住院）

---

## 2. 基础信息

| 项目 | 说明 |
|------|------|
| 协议 | HTTPS（生产环境）/ HTTP（测试环境） |
| 数据格式 | JSON（UTF-8） |
| 认证方式 | Bearer Token（推荐）或 API Key |
| 时间格式 | `yyyy-MM-dd HH:mm:ss` |
| 日期格式 | `yyyy-MM-dd` |

### 2.1 认证方式

**Bearer Token:**
```http
Authorization: Bearer <token>
```

**API Key:**
```http
X-API-Key: <api-key>
```

### 2.2 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误响应:
```json
{
  "code": 404,
  "message": "患者不存在",
  "data": null
}
```

---

## 3. 接口清单

### 3.1 获取当前住院信息

获取患者当前（或最近一次）住院档案，包含基本信息和入院信息。

```
GET /api/v1/patient/{patientId}/current-admission
```

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| patientId | string | 是 | 患者唯一标识（门诊号/住院号/身份证） |

**响应字段 (data):**

| 字段 | 类型 | 说明 | 对应规则引擎数据元 |
|------|------|------|-------------------|
| admissionId | string | 本次住院唯一标识 | — |
| patientId | string | 患者标识 | — |
| patientName | string | 患者姓名 | patientName |
| healthArchivesNo | string | 健康档案编号 | healthArchivesNo |
| insurClassCode | string | 医保类别代码 | insurClassCode |
| birthDate | string | 出生日期 yyyy-MM-dd | birthDate |
| age | int | 年龄 | age |
| sexCode | string | 性别代码 M/F/U | sexCode |
| sexName | string | 性别名称 | sexName |
| nationality | string | 国籍 | nationality |
| nationCode | string | 民族代码 | nationCode |
| native | string | 籍贯 | native |
| occupName | string | 职业名称 | occupName |
| maritalStatusCode | string | 婚姻状况代码 | maritalStatusCode |
| caseNo | string | 病案号 | caseNo |
| department | string | 当前科室 | department |
| admissionTime | string | 入院时间 yyyy-MM-dd HH:mm:ss | admissionTime |
| dischargeTime | string | 出院时间（未出院为 null） | dischargeTime |
| onsetTime | string | 发病时间 | onsetTime |
| admissionReason | string | 入院原因 | admissionReason |
| visitTime | string | 就诊时间 | visitTime |
| outptNo | string | 门诊号 | outptNo |
| patientTypeCode | string | 患者类型代码 | patientTypeCode |
| respPhysicianName | string | 责任医师姓名 | respPhysicianName |

---

### 3.2 获取病历各章节文本

获取指定住院号的病历文书各章节内容。

```
GET /api/v1/admission/{admissionId}/emr-sections
```

**响应字段 (data):**

| 字段 | 类型 | 说明 | 对应规则引擎数据元 |
|------|------|------|-------------------|
| chiefComplaint | string | 主诉 | chiefComplaint |
| presentIllness | string | 现病史 | presentIllness |
| pastHistory | string | 既往史 | pastHistory |
| personalHistory | string | 个人史 | personalHistory |
| familyHistory | string | 家族史 | familyHistory |
| allergyHistory | string | 过敏史 | allergyHistory |
| physicalExam | string | 体格检查 | physicalExam |
| auxiliaryExam | string | 辅助检查 | auxiliaryExam |
| initialDiagnosis | string[] | 初步诊断列表 | initialDiagnosis |
| admissionDiagnosis | string[] | 入院诊断列表 | admissionDiagnosis |
| dischargeDiagnosis | string[] | 出院诊断列表 | dischargeDiagnosis |
| diagWmDisCode | string[] | 西医诊断编码列表(ICD10) | diagWmDisCode |
| tcmDisCode | string[] | 中医病名代码列表 | tcmDisCode |
| tcmSydPtnCode | string[] | 中医证候代码列表 | tcmSydPtnCode |
| firstCourseRecord | string | 首次病程记录全文 | firstCourseRecord |
| dailyCourseRecords | string[] | 日常病程记录列表（按时间序） | dailyCourseRecords |
| superiorVisitRecords | string[] | 上级医师查房记录列表 | superiorVisitRecords |
| preOpDiscussion | string | 术前讨论 | preOpDiscussion |
| difficultCaseDiscussion | string | 疑难病例讨论 | difficultCaseDiscussion |
| handoverRecord | string | 交班记录 | handoverRecord |
| takeoverRecord | string | 接班记录 | takeoverRecord |
| transferOutRecord | string | 转出记录 | transferOutRecord |
| transferInRecord | string | 转入记录 | transferInRecord |
| deathDiscussion | string | 死亡讨论 | deathDiscussion |
| bloodTransfusionRecord | string | 输血记录 | bloodTransfusionRecord |
| criticalValueRecords | string[] | 危急值记录列表 | criticalValueRecords |

---

### 3.3 获取医嘱数据

获取指定住院号在指定时间范围内的医嘱。

```
GET /api/v1/admission/{admissionId}/orders
```

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startTime | string | 否 | 开始时间 yyyy-MM-dd HH:mm:ss，不传则取全部 |
| endTime | string | 否 | 结束时间，不传则取全部 |
| orderType | string | 否 | 医嘱类型: LONG_TERM(长期)/TEMP(临时)/ALL(全部)，默认ALL |

**响应字段 (data):**

| 字段 | 类型 | 说明 |
|------|------|------|
| orders | object[] | 医嘱列表 |

orders 数组元素:

| 字段 | 类型 | 说明 |
|------|------|------|
| orderId | string | 医嘱唯一标识 |
| orderTime | string | 医嘱开立时间 |
| startTime | string | 开始执行时间 |
| orderName | string | 医嘱名称（药品/项目名称） |
| orderType | string | LONG_TERM / TEMP |
| category | string | 类别: DRUG(药品)/EXAM(检查)/SURGERY(手术)/NURSING(护理)/OTHER |
| dosage | string | 剂量 |
| frequency | string | 频次 |
| route | string | 给药途径 |
| status | string | 状态: EXECUTED(已执行)/STOPPED(已停止)/PENDING(待执行) |
| stopTime | string | 停止时间（长期医嘱） |

---

### 3.4 获取抢救记录

```
GET /api/v1/admission/{admissionId}/rescue-records
```

**响应字段 (data):**

| 字段 | 类型 | 说明 |
|------|------|------|
| hasRescue | boolean | 是否存在抢救记录 |
| rescueRecords | object[] | 抢救记录列表 |

rescueRecords 元素:

| 字段 | 类型 | 说明 |
|------|------|------|
| rescueTime | string | 抢救时间 |
| rescueContent | string | 抢救记录内容 |

---

### 3.5 获取上一份住院病历

```
GET /api/v1/patient/{patientId}/previous-admission
```

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| currentAdmissionId | string | 否 | 当前住院号（用于排除当前记录） |

**响应:** 同 `3.1 获取当前住院信息`，同时包含 `emrSections` 对象（同 3.2）。

---

### 3.6 获取病程记录列表

```
GET /api/v1/admission/{admissionId}/course-records
```

**响应字段 (data):**

| 字段 | 类型 | 说明 |
|------|------|------|
| courseRecords | object[] | 病程记录列表 |

courseRecords 元素:

| 字段 | 类型 | 说明 |
|------|------|------|
| recordId | string | 记录标识 |
| recordType | string | 类型: FIRST_COURSE/DAILY_COURSE/SUPERIOR_VISIT/POST_OP/CRITICAL/OTHER |
| recordTime | string | 记录时间 |
| title | string | 标题 |
| content | string | 内容全文 |
| physicianName | string | 记录医师 |

---

## 4. 规则引擎侧配置

规则引擎通过 `application.yml` 配置 HIS 连接信息:

```yaml
his:
  enabled: true
  base-url: http://his.hospital.com/api
  auth-type: bearer        # bearer / apikey / none
  auth-token: ""
  api-key: ""
  connect-timeout-ms: 5000
  read-timeout-ms: 10000
  # 字段映射：HIS 返回字段名 -> 规则引擎数据元 camelName
  # 若 HIS 字段名与 camelName 一致，无需配置
  field-mappings:
    # 例: hisFieldName: camelName
```

---

## 5. 调用时序

```
规则执行请求
    │ POST /api/v1/rules/{id}/execute
    │ body: { "patientId": "P12345" }
    ▼
规则引擎
    │ 1. 解析 patientId
    │ 2. 调用 HIS 接口获取患者数据
    │    GET /patient/{patientId}/current-admission
    │    GET /admission/{admissionId}/emr-sections
    │    GET /admission/{admissionId}/orders?...
    │    GET /admission/{admissionId}/course-records
    │    GET /patient/{patientId}/previous-admission
    ▼
数据聚合
    │ 将所有 HIS 返回字段按 camelName 写入 $param Map
    ▼
Drools 执行
    │ KieSession.fireAllRules($param)
    ▼
返回质控结果
```

---

## 6. 数据元与 HIS 字段对照速查表

| 规则引擎数据元(camelName) | HIS 接口 | HIS 字段名 |
|--------------------------|---------|-----------|
| admissionTime | 3.1 | admissionTime |
| chiefComplaint | 3.2 | chiefComplaint |
| presentIllness | 3.2 | presentIllness |
| pastHistory | 3.2 | pastHistory |
| physicalExam | 3.2 | physicalExam |
| firstCourseRecord | 3.2 | firstCourseRecord |
| dailyCourseRecords | 3.2 | dailyCourseRecords |
| orders | 3.3 | orders (数组) |
| department | 3.1 | department |
| previousAdmission | 3.5 | (完整 admission + emrSections) |

> 提示: 规则引擎执行时，所有 HIS 数据按 `camelName` 平铺到 `$param` Map 中，DRL 条件直接通过 `$param.get("camelName")` 访问。
