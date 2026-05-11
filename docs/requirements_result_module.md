# 结果管理模块需求规格说明书

## 一、规则画布 — 结果节点配置

### 1.1 功能概述
画布中的结果节点用于定义规则匹配后返回的结果信息。结果节点与【结果管理】中预配置的结果属性绑定，支持基础结果信息和扩展属性（字典项）的配置。

### 1.2 需求清单

#### R1.1 结果节点选择
- **触发方式**：画布中拖拽或双击结果节点，打开配置抽屉
- **配置项**：
  - 节点名称（label）：可自定义输入
  - 结果配置（resultConfigId）：下拉选择，支持下拉搜索

#### R1.2 结果配置下拉展示
- 下拉列表展示【结果管理】中所有已配置的结果属性
- 每条选项显示：`[结果类型] 结果名称 (优先级:N)`
- 若结果配置含扩展属性，右侧显示绿色标签 `扩展M项`

#### R1.3 选中后详情展示
- 选择结果配置后，Select 下方自动展开绿色详情卡片：
  - 结果类型（Tag 蓝色）
  - 结果名称
  - 优先级
  - 若含扩展属性：展示字典名称及扩展项列表（`代码 - 名称`）
- 结果内容（content）自动回填该结果配置的默认文案，允许用户修改

#### R1.4 保存数据结构
结果节点保存时，向画布 JSON 写入以下字段：

```json
{
  "resultConfig": {
    "resultConfigId": 1,
    "resultType": "推荐类型",
    "resultValue": "诊断推荐",
    "priority": 0,
    "content": "默认结果提示文案",
    "metadata": "{\"hasExtension\":true,\"dictCode\":\"ICD10\",\"items\":[{\"code\":\"A00\",\"name\":\"霍乱\"}]}"
  }
}
```

#### R1.5 规则编译（DRL 生成）
- DrlCompiler 编译结果节点时，读取 `resultConfig` 中的 `metadata`
- 若 `metadata` 不为空，将转义后的 JSON 字符串写入 DRL `then` 子句：
  ```java
  r.put("metadata", "{\"hasExtension\":true,...}");
  ```
- 执行结果返回时，`metadata` 自动包含在 `results` 数组的匹配记录中

---

## 二、结果管理 — 结果属性配置

### 2.1 功能概述
结果管理是规则引擎的独立配置模块，用于预定义规则执行后可返回的各种结果属性。结果属性支持基础信息配置和扩展属性（关联字典项）配置。

### 2.2 页面布局
- **左侧树**：按 `resultType`（结果类型编码）动态分组
  - 根节点："全部"
  - 子节点：从所有结果配置中提取唯一的 `resultType` 值（如"禁忌类型"、"消息类型"、"推荐类型"）
- **右侧表格**：展示选中类型下的结果配置列表

### 2.3 需求清单

#### R2.1 结果配置 CRUD

**新增结果配置**
- 点击"新增结果配置"按钮打开弹窗
- 表单字段：
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | resultType | AutoComplete | 是 | 结果类型编码，支持选择已有类型或输入新类型 |
  | resultName | Input | 是 | 结果内容/名称，如"绝对禁忌" |
  | priority | InputNumber | 是 | 优先级，数字越大优先级越高，默认 0 |
  | content | TextArea | 否 | 默认结果提示文案，可在画布中修改 |
  | description | TextArea | 否 | 备注说明 |
- 若左侧树已选中某个类型，自动带入 `resultType`

**编辑结果配置**
- 表格行内点击编辑按钮，弹窗回填当前数据
- 支持修改所有字段

**删除结果配置**
- 表格行内点击删除按钮，二次确认后删除
- 删除后左侧树若该类型下无数据，该类型节点自动消失

#### R2.2 表格展示

| 列名 | 说明 |
|------|------|
| ID | 主键 |
| 结果类型(编码) | Tag 蓝色展示 |
| 结果内容(名称) | |
| 优先级 | |
| 结果内容 | 默认文案，过长时省略 |
| 备注 | 过长时省略 |
| 扩展信息 | 含扩展属性时展示字典名、代码-名称列表；无扩展属性时显示 `-` |
| 操作 | 编辑、删除 |

#### R2.3 扩展属性配置

**开关控制**
- 表单中提供 Switch 开关"扩展属性配置"
- 开关状态：关闭（默认）/ 开启
- 关闭时：隐藏扩展属性区域，保存时 `metadata = null`
- 开启时：下方展开绿色扩展属性区域

**扩展属性区域 — 选择字典**
- 下拉选择系统中已有的任意字典（ICD10、DRUG、性别等）
- 展示格式：`字典名称 (N项)`

**扩展属性区域 — 从字典选择（多选）**
- 点击"从字典选择"按钮，打开多选弹窗
- 弹窗展示选中字典的所有字典项表格：
  - 列：编码、名称
  - 支持 Checkbox 多选
  - 支持顶部搜索框按编码/名称过滤
  - 底部显示"已选 X 项 / 共 Y 项"
- 点击确定后，选中的字典项追加到已选列表（自动去重）

**扩展属性区域 — 字典分发**
- 点击"字典分发（全选N项）"按钮，一键将该字典所有项加入已选列表
- 分发后提示：`已分发 N 项`

**扩展属性区域 — 已选列表**
- 小表格展示已选扩展项：
  - 列：代码、名称、操作（删除）
  - 无数据时提示："暂无扩展项，请从字典选择或分发"
- 支持"清空全部"按钮一键清空

**扩展属性存储格式**
```json
{
  "hasExtension": true,
  "dictCode": "ICD10",
  "items": [
    {"code": "A00", "name": "霍乱"},
    {"code": "A01", "name": "伤寒"}
  ]
}
```

#### R2.4 兼容旧数据
- 旧格式 `metadata`（含 `extCode`/`extName` 单字段）自动转换为新 `items` 数组格式
- 编辑旧数据时正常展示，保存后按新格式存储

---

## 三、数据模型

### 3.1 result_config 表

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键 |
| result_type | VARCHAR(64) | 结果类型编码 |
| result_name | VARCHAR(128) | 结果内容名称 |
| result_category | VARCHAR(64) | 结果分类（预留） |
| content | TEXT | 默认结果提示文案 |
| description | VARCHAR(512) | 备注 |
| priority | INT | 优先级，默认 0 |
| condition_model_id | BIGINT | 关联条件模型ID（历史兼容，前端不再使用） |
| metadata | TEXT | 扩展属性 JSON |
| created_at | DATETIME | 创建时间 |

### 3.2 画布节点数据（conditionConfig / resultConfig）

**条件节点**
```json
{
  "field": "patientAge",
  "operator": ">",
  "value": "65",
  "dataType": "NUMERIC",
  "conditionModelId": 123,
  "datasetId": 5
}
```

**结果节点**
```json
{
  "resultConfigId": 1,
  "resultType": "推荐类型",
  "resultValue": "诊断推荐",
  "priority": 0,
  "content": "提示文案",
  "metadata": "{...}"
}
```

---

## 四、接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/result-configs | 查询所有结果配置 |
| GET | /api/v1/result-configs/{id} | 按ID查询 |
| GET | /api/v1/result-configs/by-condition/{conditionModelId} | 按条件模型ID查询（历史兼容） |
| POST | /api/v1/result-configs | 创建 |
| PUT | /api/v1/result-configs/{id} | 更新 |
| DELETE | /api/v1/result-configs/{id} | 删除 |
| GET | /api/v1/dictionaries | 查询所有字典（扩展属性用） |
| GET | /api/v1/data-sets | 查询所有数据集（条件节点用） |
| GET | /api/v1/data-elements | 查询所有数据元（条件节点用） |
| GET | /api/v1/condition-models | 查询所有条件模型（条件节点用） |
