# 规则引擎系统 — 设计与开发规范

## 1. 项目概述

基于 Drools 的可视化规则引擎，面向医疗质控场景。用户通过 ReactFlow 画布编排规则逻辑（条件节点 + 结果节点），后端自动将画布转换为 Drools DRL 规则文本并编译执行。

**运行环境：**
- Java 8（OpenJDK 1.8）
- Spring Boot 2.7.18
- Node.js 24 + Vite 5
- MySQL 8

**浏览器兼容：** 360Chrome（已做降级兼容处理）

---

## 2. 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | React 18 (Legacy 模式) | `ReactDOM.render` 兼容 360Chrome |
| 前端构建 | Vite 5.4 | 生产构建后 `vite preview --port 5173` 托管 |
| UI 组件 | Ant Design 5 | 生产构建模式下正常使用 |
| 画布引擎 | ReactFlow | 规则可视化编排 |
| HTTP 请求 | axios | REST API 通信 |
| 后端框架 | Spring Boot 2.7 | RESTful API + JPA |
| 规则引擎 | Drools 7.x | 动态 DRL 编译与执行 |
| 数据库 | MySQL 8 | 环境变量配置 |
| ORM | Spring Data JPA | `ddl-auto: update` 自动建表 |

---

## 3. 数据库设计

### 3.1 实体关系

```
RuleType (规则类型) 1 ---- n Rule (规则)
                                     |
                                     v
DataSet (数据集) 1 ---- n DataElement (数据元)
                                     |
                                     v
ConditionModelCategory 1 ---- n ConditionModel (条件)
                                     |
                                     v
                               Rule (画布 JSON)
```

### 3.2 核心表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `rule_type` | 规则类型 | `code`, `name`, `description`, `status` |
| `rule` | 规则 | `code`, `name`, `rule_type_id`, `version`, `canvas_json`, `drl_text`, `status` |
| `dataset` | 数据集 | `code`, `name`, `description`, `status` |
| `data_element` | 数据元 | `code`, `name`, `data_type`, `dataset_id`, `dict_code` |
| `condition_model_category` | 条件分类 | `code`, `name`, `description`, `sort_order`, `status` |
| `condition_model` | 条件模型 | `code`, `name`, `data_type`, `operators`, `node_usage`, `data_element_id`, `category_id` |
| `dictionary` / `dictionary_item` | 标准字典 | `code`, `name`, `dict_code`, `item_value` |

### 3.3 关键约束

- `data_element.dataset_id` NOT NULL：数据元必须属于某个数据集
- `condition_model.data_element_id`：可为 NULL（结果节点可不选数据元）
- `condition_model.category_id`：关联条件分类
- **删除保护**：
  - 数据集下有数据元 → 禁止删除
  - 条件分类下有条件 → 禁止删除
  - 规则类型下有规则 → 禁止删除

---

## 4. 后端设计

### 4.1 分层结构

```
controller/    — REST API 入口
service/       — 业务逻辑 + 删除保护
repository/    — Spring Data JPA 接口
domain/        — JPA 实体
drools/        — DrlCompiler + RuleExecutor
config/        — DataInitializer + GlobalExceptionHandler
```

### 4.2 核心服务

#### RuleTypeService
- `findAll()` / `findById(id)` / `findByCode(code)` / `save(entity)`
- `deleteById(id)` — 删除前检查 `ruleRepository.countByRuleTypeId(id) > 0`

#### DataSetService
- `deleteById(id)` — 删除前检查 `dataElementRepository.countByDatasetId(id) > 0`

#### ConditionModelCategoryService
- `deleteById(id)` — 删除前检查 `conditionModelRepository.countByCategoryId(id) > 0`

#### DrlCompiler
- 输入：ReactFlow `nodes[]` + `edges[]`
- 输出：Drools DRL 文本
- 节点映射：
  - 条件节点 → `when` 条件表达式（`$param.get("field") operator value`）
  - 结果节点 → `then` 结果 Map 组装
  - AND/OR 节点 → 逻辑组合

#### RuleExecutor
- 接收 DRL 文本 → `KieHelper` 动态编译 → `KieSession.fireAllRules()`
- 传入参数为 `Map<String, Object>`
- 收集所有匹配结果返回

### 4.3 全局异常处理

`GlobalExceptionHandler` 使用 `@ResponseStatus` 确保业务异常返回正确的 HTTP 状态码：
- `RuntimeException` → HTTP 400
- 其他异常 → HTTP 500

前端通过 `e.response.data.message` 读取错误信息。

### 4.4 数据初始化

`DataInitializer`（`CommandLineRunner`）启动时执行：
1. 检测 `rule` 表是否存在，不存在则自动 `CREATE TABLE`
2. 按顺序初始化：字典 → 数据集 → 数据元 → 规则类型 → 条件分类 → 条件
3. `saveIfNotExists(code)`：按 code 检查，存在则跳过
4. 修复旧数据：为缺失 `category_id` 的条件补充默认值

---

## 5. 前端设计

### 5.1 页面结构

| 页面 | 路由方式 | 说明 |
|------|----------|------|
| 规则类型（首页） | 默认 | 左侧类型卡片 + 右侧规则列表 |
| 数据元 | `?page=dataElements` | 上方数据集管理 + 下方数据元列表 |
| 标准字典 | `?page=dictionaries` | 字典管理 |
| 条件管理 | `?page=models` | 上方条件分类 + 下方条件列表 |
| 规则执行 | `?page=execute` | 规则执行测试（开发中） |
| 规则画布 | `?page=editor&type=xxx&ruleId=xxx` | **不在导航栏**，仅通过联动进入 |

### 5.2 导航设计

- 顶部原生 `div` 导航栏（**禁用 Ant Design Menu**，避免 360Chrome 兼容问题）
- 默认首页：`types`（规则类型）
- `App.jsx` 监听 URL `?page=xxx` 参数自动切换页面
- 点击导航清除 URL 参数

### 5.3 页面合并设计

#### 数据元页
- **上方**：数据集表格（编码/名称/描述/状态），支持新增/编辑/删除
- **下方**：数据元表格，通过 `selectedDsId` 筛选，仅显示当前数据集下的数据元
- 新增数据元弹窗：自动关联当前选中数据集

#### 条件管理页
- **上方**：条件分类表格（编码/名称/描述/排序），支持新增/编辑/删除
- **下方**：条件表格，通过 `selectedCatId` 筛选，仅显示当前分类下的条件
- 新增条件弹窗：自动关联当前选中分类，顶部显示所属分类名称

### 5.4 画布编辑器

- 从 URL 读取 `type`（规则类型 ID）和 `ruleId`（规则 ID）
- 节点类型：开始、条件、结果、AND、OR、结束
- 条件节点配置：
  1. 先选条件分类
  2. 再选该分类下的条件
  3. `field` 自动来自关联数据元的 `code`
- 结果节点配置：先选条件分类 → 再选结果条件（数据元可为空）
- 保存：提交 `nodes` + `edges` JSON → 后端生成 DRL
- 返回按钮：`window.location.href = '/?page=types'`

### 5.5 规则类型页

- **左侧**：规则类型卡片列表，每张卡片右上角有编辑/删除按钮
- **右侧**：选中类型的规则表格（编码/名称/版本/状态/操作）
- 操作按钮：新建规则 → 跳转到画布（携带 `type` 参数）
- 规则行操作：编辑画布 → 跳转到画布（携带 `type` + `ruleId`）
- 删除保护：有规则时后端返回 400，前端弹窗提示

### 5.6 360Chrome 兼容性处理

| 问题 | 根因 | 解决方案 |
|------|------|----------|
| 页面空白 | 浏览器注入脚本修改 DOM，React 18 `createRoot` 的 `removeChild` 报错 | 降级为 `ReactDOM.render` Legacy 模式 |
| 导航不显示 | Vite dev server 与 360Chrome 不兼容 | 生产构建 + `vite preview` |
| Menu 组件异常 | Ant Design Menu 在 360Chrome 下 DOM 操作冲突 | 移除 Menu，使用原生 `div` 导航 |
| DOM 被篡改 | 浏览器插件修改 `root` 节点 | `index.html` 添加 MutationObserver 保护脚本 + `notranslate` meta |

---

## 6. 核心业务逻辑

### 6.1 规则执行流程

```
1. 画布编排 → 保存 nodes + edges JSON
2. 后端 DrlCompiler → 生成 DRL 文本
3. 规则发布 → KieHelper 编译 → 缓存 KieContainer
4. 规则执行 → KieSession.fireAllRules($param) → 收集结果
```

### 6.2 条件 ↔ 数据元 映射

- 每个条件（`ConditionModel`）关联 **一个** 数据元（`dataElementId`）
- 条件的 `code` / `name` / `dataType` 可独立设置，但实际执行时 `field` 取自数据元 `code`
- 结果节点（`nodeUsage = RESULT`）的 `dataElementId` 可为 `null`

### 6.3 条件分类纯分类化

- `ConditionModelCategory` 仅作为条件的组织分类，**不关联数据元**
- 条件与数据元的关联通过 `ConditionModel.dataElementId` 直接建立

---

## 7. API 汇总

### 规则类型
```
GET    /api/v1/rule-types
GET    /api/v1/rule-types/{id}
POST   /api/v1/rule-types
PUT    /api/v1/rule-types/{id}
DELETE /api/v1/rule-types/{id}    # 有规则时返回 400
```

### 规则
```
GET    /api/v1/rules?ruleTypeId={id}
GET    /api/v1/rules/{id}
POST   /api/v1/rules
PUT    /api/v1/rules/{id}
DELETE /api/v1/rules/{id}
PUT    /api/v1/rules/{id}/canvas  # 保存画布 JSON，自动生成 DRL
POST   /api/v1/rules/{id}/publish
POST   /api/v1/rules/{id}/execute
```

### 数据集
```
GET    /api/v1/datasets
POST   /api/v1/datasets
PUT    /api/v1/datasets/{id}
DELETE /api/v1/datasets/{id}      # 有数据元时返回 400
```

### 数据元
```
GET    /api/v1/data-elements
GET    /api/v1/data-elements?datasetId={id}
POST   /api/v1/data-elements
PUT    /api/v1/data-elements/{id}
DELETE /api/v1/data-elements/{id}
```

### 条件分类
```
GET    /api/v1/condition-model-categories
POST   /api/v1/condition-model-categories
PUT    /api/v1/condition-model-categories/{id}
DELETE /api/v1/condition-model-categories/{id}  # 有条件时返回 400
```

### 条件
```
GET    /api/v1/condition-models
GET    /api/v1/condition-models?categoryId={id}
POST   /api/v1/condition-models
PUT    /api/v1/condition-models/{id}
DELETE /api/v1/condition-models/{id}
```

### 字典
```
GET    /api/v1/dictionaries
POST   /api/v1/dictionaries
PUT    /api/v1/dictionaries/{id}
DELETE /api/v1/dictionaries/{id}
GET    /api/v1/dictionary-items?dictCode={code}
```

---

## 8. 关键设计决策

| 决策 | 说明 |
|------|------|
| 数据库 | 仅 MySQL，环境变量配置连接信息 |
| 自动建表 + 数据初始化 | JPA `ddl-auto: update` + `DataInitializer` 启动时自动补齐，零配置启动 |
| 页面合并 | 数据集+数据元、条件分类+条件 分别合并为单页面，减少导航层级 |
| 画布不在导航栏 | 规则画布仅通过规则类型页面的"新建规则"/"编辑画布"联动进入，避免用户直接访问无上下文 |
| 删除保护 | 所有有子数据的实体删除前检查，后端抛 RuntimeException → 400，前端 catch 提示 |
| 条件与数据元一一对应 | 简化配置逻辑，条件字段自动来自数据元编码 |
| 生产构建托管 | Vite dev server 与 360Chrome 不兼容 → 统一用 `npm run build + vite preview` |
| React Legacy 模式 | 360Chrome 下 React 18 `createRoot` 崩溃 → 降级 `ReactDOM.render` |

---

## 9. 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `ruleengine` | MySQL 数据库名 |
| `MYSQL_USER` | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | `qiubin78` | MySQL 密码 |

---

## 10. 启动方式

### 后端
```bash
cd rule-engine-server
./mvnw clean package -DskipTests
java -jar target/rule-engine-server-1.0.0-SNAPSHOT.jar
```

### 前端（生产模式，兼容 360Chrome）
```bash
cd rule-engine-ui
npm run build
npx vite preview --port 5173 --host
```

访问地址：`http://localhost:5173`
