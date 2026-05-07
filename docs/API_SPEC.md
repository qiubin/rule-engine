# 规则引擎 API 接口文档（含 NLP 能力）

## 基础信息

- **Base URL**: `http://localhost:8081/api/v1`
- **Content-Type**: `application/json`

---

## 一、规则管理接口

### 1.1 规则 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/rules` | 查询所有规则 |
| GET | `/rules/{id}` | 根据 ID 查询规则 |
| GET | `/rules/by-code/{code}` | 根据编码查询规则 |
| POST | `/rules` | 创建规则（body: Rule JSON）|
| PUT | `/rules/{id}` | 更新规则基本信息 |
| DELETE | `/rules/{id}` | 删除规则 |

### 1.2 规则画布与发布

#### 保存画布
```http
PUT /rules/{id}/canvas
Content-Type: application/json

{
  "canvasData": "{\"nodes\":[],\"edges\":[]}"  // ReactFlow JSON 字符串
}
```
**说明**：保存画布时会自动调用 DrlCompiler 生成 DRL，并创建一条版本记录。

#### 发布规则
```http
POST /rules/{id}/publish
```
**说明**：发布时编译 DRL 并缓存到 KieContainer。发布后规则不可编辑画布。

### 1.3 规则执行（核心接口）

#### 单规则执行（正式）
```http
POST /rules/{code}/execute
Content-Type: application/json

{
  "patientId": "P001",           // 患者ID（可选，传入后自动拉取HIS数据）
  "admissionId": "A001",         // 住院号（可选）
  "age": 65,                     // 可覆盖HIS数据的参数
  "emrContent": "患者发热3天..."  // 测试时可直接传入
}
```
**响应**：
```json
{
  "ruleCode": "RULE_001",
  "firedRules": 2,
  "matched": true,
  "results": [
    {
      "ruleCode": "RULE_001",
      "nodeLabel": "发热未描述",
      "resultType": "WARNING",
      "resultValue": "发热患者病程记录未描述退热措施",
      "matched": true,
      "resultNodeId": "node-xxx",
      "hitConditionIds": ["cond-1", "cond-2"]
    }
  ],
  "parameters": { "age": 65, "positiveSymptoms": ["发热"], ... }
}
```

#### 单规则测试执行（草稿态）
```http
POST /rules/{id}/test-execute
Content-Type: application/json

{ "patientId": "P001", "age": 65 }
```
**说明**：测试执行会重新编译 DRL，使用最新编译器逻辑，不依赖已发布状态。

#### 批量测试执行
```http
POST /rules/batch-test-execute?ruleIds=1,2,3
Content-Type: application/json

{ "patientId": "P001" }
```
**响应**：
```json
{
  "total": 3,
  "executed": 3,
  "matched": 1,
  "details": [
    { "ruleId": 1, "ruleCode": "R01", "matched": true, "firedRules": 2, ... },
    { "ruleId": 2, "ruleCode": "R02", "matched": false, ... }
  ],
  "parameters": { ... }
}
```

---

## 二、NLP 相关接口

### 2.1 NLP 医学实体提取（调试）

```http
POST /api/v1/nlp/ner
Content-Type: application/json

{
  "text": "患者无明显诱因出现发热，伴咳嗽、咳痰，无胸痛、无呼吸困难。"
}
```

**响应**：
```json
{
  "symptoms": ["发热", "咳嗽", "咳痰", "胸痛", "呼吸困难"],
  "signs": [],
  "drugs": [],
  "exams": [],
  "surgeries": [],
  "diseases": []
}
```

### 2.2 NLP 实体提取 + 否定检测（调试）

```http
POST /api/v1/nlp/ner-with-negation
Content-Type: application/json

{
  "text": "患者无明显诱因出现发热，伴咳嗽、咳痰，无胸痛、无呼吸困难。"
}
```

**响应**：
```json
{
  "symptoms_positive": ["发热", "咳嗽", "咳痰"],
  "symptoms_negative": ["胸痛", "呼吸困难"],
  "signs_positive": [],
  "signs_negative": [],
  ...
}
```

### 2.3 NLP 分词相似度（调试）

```http
POST /api/v1/nlp/similarity
Content-Type: application/json

{
  "textA": "患者发热3天，伴咳嗽咳痰",
  "textB": "患者发热三天，伴有咳嗽和咳痰",
  "threshold": 0.8
}
```

**响应**：
```json
{
  "similarity": 0.85,
  "matched": true
}
```

---

## 三、数据元管理接口

### 3.1 数据元 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/data-elements` | 查询所有数据元 |
| POST | `/data-elements` | 创建数据元 |
| PUT | `/data-elements/{id}` | 更新数据元 |
| DELETE | `/data-elements/{id}` | 删除数据元 |

### 3.2 数据元批量导入

```http
POST /data-elements/import
Content-Type: multipart/form-data

file: [Excel文件]  // 按模板格式，16列
```

**响应**：
```json
{
  "totalRows": 100,
  "createdCount": 85,
  "updatedCount": 10,
  "errors": [
    { "row": 5, "message": "数据元编码不能为空" }
  ]
}
```

### 3.3 下载导入模板

```http
GET /data-elements/import-template
```

---

## 四、字典管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dictionaries` | 查询所有字典 |
| POST | `/dictionaries` | 创建字典 |
| GET | `/dictionaries/{code}/items` | 查询字典项 |
| POST | `/dictionaries/{code}/items` | 添加字典项 |
| PUT | `/dictionaries/{code}/items/{id}` | 更新字典项 |
| DELETE | `/dictionaries/{code}/items/{id}` | 删除字典项 |

**说明**：字典数据在应用启动时会自动导出为 HanLP 自定义词典（`data/dictionary/custom/` 目录），供 NLP 实体识别使用。

---

## 五、规则版本与执行日志

### 5.1 规则版本

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/rule-versions/{ruleId}` | 查询规则的所有版本 |
| GET | `/rule-versions/{ruleId}/compare?versionA=1&versionB=2` | 对比两个版本 |

### 5.2 执行日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/rule-execution-logs?ruleId=1&page=0&size=20` | 分页查询执行日志 |
| GET | `/rule-execution-logs/{id}` | 查看单条日志详情 |
| GET | `/rule-execution-logs/{id}/hit-nodes` | 查看命中节点明细 |

---

## 六、条件模型与分类

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/condition-model-categories` | 条件分类列表 |
| GET | `/condition-models` | 条件模型列表 |
| GET | `/condition-models/by-category/{categoryId}` | 按分类查条件 |

---

## 七、HIS 数据集成说明

当传入参数包含 `patientId` 时，执行接口会自动调用 HIS 客户端拉取患者数据：

1. 当前住院信息 → `admissionId`、`deptCode` 等
2. 病历各章节 → 按 camelName 平铺
3. 医嘱 → `orders`、`drugNames`
4. 抢救记录 → `hasRescueRecord`
5. 病程记录 → `courseRecords`、`firstCourseRecord`、`dailyCourseRecords` 等
6. 上一份住院 → `previous_` 前缀字段
7. **NLP 处理** → `positiveSymptoms`、`negativeSymptoms`、`positiveSigns`、`negativeSigns` 等

**参数优先级**：用户传入的参数 > HIS 数据（同名时覆盖），方便测试时覆盖特定字段。

HIS 接口在 `application.yml` 中配置，`his.enabled: false` 时关闭数据拉取，仅使用传入参数。
