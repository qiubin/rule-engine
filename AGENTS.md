# AGENTS.md — 基于 Drools 的可视化规则引擎

> 本文件面向 AI 编码助手。项目的主要自然语言为 **中文**（注释、文档、UI 文本均为中文）。

---

## 1. 项目概述

本项目是一个面向医疗质控场景的可视化规则引擎系统，采用前后端分离的全栈架构：

- **后端** (`rule-engine-server/`): Spring Boot 2.7 + Drools 7 规则引擎，提供 REST API 与动态规则编译执行能力
- **前端** (`rule-engine-ui/`): React 18 + ReactFlow 可视化画布 + Ant Design 5，用于规则的可视化编排

核心工作流：用户在 ReactFlow 画布上拖拽节点（开始、条件、结果、AND、OR、结束）并连线 → 前端提交 `nodes` + `edges` JSON → 后端 `DrlCompiler` 自动生成 Drools DRL 规则文本 → 发布时通过 `KieHelper` 动态编译缓存 → 执行时 `KieSession.fireAllRules()` 触发规则并返回结果。

---

## 2. 技术栈与版本

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 | Java 8 (OpenJDK 1.8) |
| 规则引擎 | Drools | 7.74.1.Final | 动态 DRL 编译与执行 |
| ORM | Spring Data JPA | — | Hibernate 5.6, `ddl-auto: update` |
| 数据库 | MySQL 8 | — | 仅支持 MySQL |
| 构建工具 | Maven | 3.x | `mvnw` wrapper 可用 |
| 前端框架 | React | 18.2 | **Legacy 模式** (`ReactDOM.render`) |
| 构建工具 | Vite | 5.x | 开发端口 3000，预览端口 5173 |
| UI 组件 | Ant Design | 5.12 | 部分组件因浏览器兼容性被禁用 |
| 画布引擎 | ReactFlow | 11.10 | 规则可视化编排核心 |
| HTTP 客户端 | axios | 1.6 | REST API 通信 |
| 工具库 | Lombok | 1.18.30 | 后端大量使用 |

**关键兼容性约束**：项目必须兼容 **360Chrome**（一款基于 Chromium 的中国浏览器）。这导致了以下特殊处理：
- React 使用 `ReactDOM.render` Legacy 模式（`createRoot` 会崩溃）
- 不使用 Ant Design `Menu` 组件，导航使用原生 `<div>` 实现
- 生产构建后使用 `vite preview` 托管，不使用 Vite dev server
- `index.html` 内嵌 MutationObserver 脚本阻止浏览器扩展注入脚本/iframe 破坏 React DOM

---

## 3. 项目结构

```
rule-engine/
├── rule-engine-server/          # 后端服务
│   ├── pom.xml                  # Maven 配置
│   ├── src/main/java/com/ruleengine/
│   │   ├── RuleEngineApplication.java
│   │   ├── config/              # DataInitializer, GlobalExceptionHandler
│   │   ├── controller/          # REST API 入口（9 个 Controller）
│   │   ├── service/             # 业务逻辑层
│   │   ├── repository/          # Spring Data JPA 接口
│   │   ├── domain/              # JPA 实体类
│   │   │   └── enums/           # 枚举：DataType, NodeType, RuleStatus, ValueSource 等
│   │   ├── drools/
│   │   │   ├── compiler/        # DrlCompiler：画布 JSON → DRL 文本
│   │   │   ├── runtime/         # RuleExecutor：KieHelper 编译 + 执行
│   │   │   ├── adapter/         # DataAdapter 接口及实现（ParamAdapter, SqlAdapter）
│   │   │   └── config/          # DroolsConfig：KieServices / KieContainer Bean
│   │   └── dto/                 # ApiResponse 统一响应封装
│   └── src/main/resources/
│       └── application.yml      # 数据库配置（MySQL）
├── rule-engine-ui/              # 前端应用
│   ├── package.json             # npm 配置
│   ├── vite.config.js           # Vite 配置（含 /api 代理到 localhost:8081）
│   ├── index.html               # 含 MutationObserver 防护脚本
│   └── src/
│       ├── main.jsx             # React Legacy 模式入口 + ErrorBoundary
│       ├── App.jsx              # 页面路由切换（基于 URL ?page=xxx）
│       ├── components/
│       │   ├── Nodes/           # 6 种节点组件（开始/条件/结果/AND/OR/结束）
│       │   ├── ConfigPanel/     # 节点配置面板（条件/结果配置）
│       │   ├── ToolBar/         # 左侧节点拖拽工具栏
│       │   └── Canvas/          # （空目录，未使用）
│       ├── pages/
│       │   ├── RuleTypeMgr/     # 规则类型管理（首页）
│       │   ├── RuleEditor/      # 规则画布编辑器（核心页面）
│       │   ├── DataElementMgr/  # 数据集 + 数据元管理
│       │   ├── ConditionModelMgr/   # 条件分类 + 条件管理
│       │   ├── DictionaryMgr/   # 标准字典管理
│       │   ├── RuleExecute/     # 规则执行测试
│       │   └── SystemMenu/      # 系统管理入口页
│       ├── hooks/               # （空目录）
│       ├── stores/              # （空目录）
│       └── utils/               # （空目录）
├── start.sh                     # 一键启动脚本（backend / frontend / all）
├── README.md                    # 项目说明文档
├── DESIGN.md                    # 详细设计与开发规范
└── AGENTS.md                    # 本文件
```

---

## 4. 构建与运行

### 4.1 环境要求

- Java 8（OpenJDK 1.8）
- Maven 3.x（或直接使用 `./mvnw`）
- Node.js 18+
- MySQL 8

### 4.2 后端启动

```bash
cd rule-engine-server
# 使用 Maven wrapper 构建并运行
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

- 服务端口：**8081**
- Actuator：`http://localhost:8081/actuator/health`

### 4.3 前端启动

```bash
cd rule-engine-ui
npm install

# 开发模式（Vite dev server，端口 3000）
npm run dev

# 生产模式构建 + 预览（兼容 360Chrome，端口 5173）
npm run build
npx vite preview --port 5173 --host
```

**注意**：360Chrome 下必须使用生产构建 + `vite preview`，不能使用 `npm run dev`。

### 4.4 一键启动

```bash
# 启动后端 + 前端
./start.sh all

# 仅启动后端
./start.sh backend

# 仅启动前端
./start.sh frontend
```

---

## 5. 数据库与数据初始化

### 5.1 数据库配置

使用 MySQL 数据库，连接信息通过环境变量配置：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `ruleengine` | 数据库名 |
| `MYSQL_USER` | `root` | 用户名 |
| `MYSQL_PASSWORD` | `qiubin78` | 密码 |

### 5.2 自动建表

JPA `hibernate.ddl-auto: update` 自动建表。但有一个已知问题：

> `Rule` 实体中 `canvas_data` 和 `drools_drl` 字段使用 `@Lob @Column(columnDefinition = "CLOB")`，**MySQL 不支持 `CLOB` 类型**，导致 Hibernate 自动建表时 `rule` 表创建失败（日志报 WARN，不影响其他表）。
>
> 解决方式：`DataInitializer`（`CommandLineRunner`）启动时会检测 `rule` 表是否存在，若不存在则手动执行兼容 MySQL 的 `CREATE TABLE`（使用 `LONGTEXT` 替代 `CLOB`）。

### 5.3 数据初始化

`DataInitializer` 在应用启动时自动按顺序初始化以下基础数据（仅当表为空时）：

1. **字典** (`dictionary` / `dictionary_item`): 性别、ICD10 诊断、药品、护理分级、禁忌类别
2. **数据集** (`data_set`): 通用数据集
3. **数据元** (`data_element`): 患者年龄、性别、诊断编码、医嘱药品、检验结果、病历内容、血压、体温
4. **规则类型** (`rule_type`): 合理性质控、病历质控、医保稽核、护理决策、VTE防治
5. **条件分类** (`condition_model_category`): 患者信息、医嘱信息、诊断信息、病历文书、生命体征
6. **条件模型** (`condition_model`): 8 个条件，关联对应数据元与分类

同时包含旧数据修复逻辑：
- 为缺失 `dataset_id` 的数据元补充默认值
- 为缺失 `category_id` 的条件补充分类（通过 `code` 匹配）
- 反向迁移：从旧版关联表 `condition_model_data_elements` 恢复数据到 `condition_model.data_element_id`

---

## 6. 后端架构详解

### 6.1 分层规范

```
controller/    → REST API 入口，统一路径前缀 /api/v1/xxx，@CrossOrigin(origins = "*")
service/       → 业务逻辑，删除前检查子数据关联
repository/    → Spring Data JPA 接口，继承 JpaRepository
domain/        → JPA 实体 + 枚举
drools/        → 规则引擎核心（编译器 + 执行器 + 适配器）
config/        → 全局配置（异常处理、数据初始化）
dto/           → ApiResponse<T> 统一响应体
```

### 6.2 核心实体关系

```
RuleType (1) ----< (N) Rule
    
DataSet (1) ----< (N) DataElement

ConditionModelCategory (1) ----< (N) ConditionModel
    ↑ 关联 data_element_id（可为 NULL，结果节点不强制关联）
```

### 6.3 删除保护

以下实体删除前会检查子数据，若存在则抛 `RuntimeException` → 前端收到 HTTP 400：

- `RuleType` → 检查是否有关联 `Rule`
- `DataSet` → 检查是否有关联 `DataElement`
- `ConditionModelCategory` → 检查是否有关联 `ConditionModel`

### 6.4 核心服务说明

#### DrlCompiler
- 输入：`ruleCode` + ReactFlow 画布 JSON (`nodes[]`, `edges[]`)
- 输出：Drools DRL 文本
- 节点映射逻辑：
  - `start` → 透传下游
  - `condition` → 构建 `when` 表达式（`$param.get("field") operator value`）
  - `result` → 构建 `then` 结果 Map（含 `ruleCode`, `resultType`, `resultValue`, `matched`）
  - `and` / `or` → 预计算上游条件组合，用 `&&` / `||` 连接
- 支持条件边 label：`true`/`是` 走真分支，`false`/`否` 走假分支（条件取反）
- 递归深度限制：50 层，防止循环图死循环

#### RuleExecutor
- `compileAndCache(ruleCode, drl)`: 使用 `KieServices` + `KieFileSystem` + `KieBuilder` 动态编译 DRL，成功后缓存 `KieContainer`
- `execute(ruleCode, drl, parameters)`: 从缓存获取 `KieContainer`，创建 `KieSession`，设置全局变量 `result`（Map），插入参数 Map，执行 `fireAllRules()`
- 传入参数格式：`Map<String, Object>`
- 返回结果包含：`ruleCode`, `firedRules`, `matched`, `results`, `parameters`

#### RuleService
- `saveCanvas()`: 保存画布 JSON，同时调用 `DrlCompiler` 生成并存储 DRL
- `publish()`: 检查 DRL 非空，调用 `RuleExecutor.compileAndCache()` 编译缓存，状态改为 `PUBLISHED`
- `execute()`: 仅允许 `PUBLISHED` 状态规则执行
- `testExecute()`: 无视状态，实时重新编译画布并执行（用于草稿测试）
- `batchTestExecute()`: 批量测试多条规则，单条失败不影响其他，返回汇总统计

### 6.5 异常处理

`GlobalExceptionHandler` (`@RestControllerAdvice`)：
- `RuntimeException` → HTTP 400 + `ApiResponse.error(400, message)`
- 其他 `Exception` → HTTP 500 + `ApiResponse.error(500, "系统内部错误: " + message)`

前端通过 `e.response.data.message` 读取错误信息。

---

## 7. 前端架构详解

### 7.1 路由机制

不使用 React Router。路由通过 `App.jsx` 监听 URL 查询参数 `?page=xxx` 实现：

| page 参数 | 页面组件 | 说明 |
|-----------|----------|------|
| (无) / `types` | `RuleTypeMgr` | 默认首页 |
| `editor` | `RuleEditor` | 规则画布编辑器（需 `type` 和可选 `ruleId`） |
| `dataElements` | `DataElementMgr` | 数据集 + 数据元管理 |
| `dictionaries` | `DictionaryMgr` | 字典管理 |
| `models` | `ConditionModelMgr` | 条件分类 + 条件管理 |
| `execute` | `RuleExecute` | 规则执行测试 |
| `system` | `SystemMenu` | 系统管理菜单 |

导航栏点击后会调用 `window.history.replaceState({}, '', '/')` 清除 URL 参数。

### 7.2 画布节点类型

定义在 `components/Nodes/index.jsx`：

| 类型 | 组件 | 说明 |
|------|------|------|
| `start` | `StartNode` | 流程起点，右侧单出口 |
| `condition` | `ConditionNode` | 条件节点，右侧双出口（true: 30% 高度 / false: 70% 高度） |
| `result` | `ResultNode` | 结果节点，配置 resultType + resultValue |
| `and` | `AndNode` | 逻辑与，多入口单出口 |
| `or` | `OrNode` | 逻辑或，多入口单出口 |
| `end` | `EndNode` | 流程终点，左侧单入口 |

### 7.3 页面合并设计

- **数据元页**：上方数据集表格（增删改查），下方数据元表格（按选中数据集筛选）
- **条件管理页**：上方条件分类表格（增删改查），下方条件表格（按选中分类筛选）

### 7.4 API 调用

前端使用 axios，API 基础路径硬编码为 `const API_BASE = '/api/v1'`。
Vite 配置中 `/api` 代理到 `http://localhost:8081`。

---

## 8. 核心 API 清单

所有 API 前缀为 `/api/v1`，响应格式为 `ApiResponse<T>` 或直接返回实体。

### 规则类型 (`/rule-types`)
```
GET    /rule-types
GET    /rule-types/{id}
POST   /rule-types
PUT    /rule-types/{id}
DELETE /rule-types/{id}    # 有规则时返回 400
```

### 规则 (`/rules`)
```
GET    /rules?ruleTypeId={id}
GET    /rules/{id}
POST   /rules
PUT    /rules/{id}
PUT    /rules/{id}/canvas      # 保存画布 JSON，自动生成 DRL
POST   /rules/{id}/publish     # 发布规则（编译缓存）
POST   /rules/{id}/execute     # 执行已发布规则
POST   /rules/{id}/test-execute    # 测试执行（草稿也可）
POST   /rules/batch-test-execute   # 批量测试执行
POST   /rules/execute?ruleCode=xxx # 通过编码执行
DELETE /rules/{id}
```

### 数据集 (`/datasets`)
```
GET    /datasets
POST   /datasets
PUT    /datasets/{id}
DELETE /datasets/{id}      # 有数据元时返回 400
```

### 数据元 (`/data-elements`)
```
GET    /data-elements?datasetId={id}
POST   /data-elements
PUT    /data-elements/{id}
DELETE /data-elements/{id}
```

### 条件分类 (`/condition-model-categories`)
```
GET    /condition-model-categories
POST   /condition-model-categories
PUT    /condition-model-categories/{id}
DELETE /condition-model-categories/{id}  # 有条件时返回 400
```

### 条件 (`/condition-models`)
```
GET    /condition-models?categoryId={id}
POST   /condition-models
PUT    /condition-models/{id}
DELETE /condition-models/{id}
```

### 字典 (`/dictionaries`, `/dictionary-items`)
```
GET    /dictionaries
POST   /dictionaries
PUT    /dictionaries/{id}
DELETE /dictionaries/{id}
GET    /dictionary-items?dictCode={code}
```

---

## 9. 代码风格与开发规范

### 9.1 Java 后端

- **Lombok**: 实体类使用 `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`；服务类使用 `@RequiredArgsConstructor` 构造器注入；日志使用 `@Slf4j`
- **注入方式**: 统一使用构造器注入（`@RequiredArgsConstructor` + `final` 字段），不推荐使用 `@Autowired` 字段注入
- **异常**: 业务异常统一抛 `RuntimeException`，由 `GlobalExceptionHandler` 捕获转换为 HTTP 400
- **JPA 注解**: 实体使用 `@Entity`, `@Table`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`；枚举字段使用 `@Enumerated(EnumType.STRING)`；大文本使用 `@Lob`
- **事务**: Service 类标注 `@Transactional(readOnly = true)`，写操作方法上再标 `@Transactional`

### 9.2 React 前端

- **组件风格**: 函数组件为主，使用 React Hooks（`useState`, `useEffect`, `useCallback`, `useRef`）
- **状态管理**: 无全局状态管理库（Zustand 目录存在但未使用），各页面自管状态
- **样式**: 混合使用 Ant Design 组件内联样式、CSS 文件（`style.css`, `node-styles.css`）
- **事件处理**: 使用 `useCallback` 包裹事件处理器
- **兼容性**: **严禁**使用 `ReactDOM.createRoot`，必须使用 `ReactDOM.render`；**严禁**在导航使用 Ant Design `Menu` 组件

### 9.3 数据类型与计算符映射

| 数据类型 | 可用计算符 |
|----------|-----------|
| `DICTIONARY`（字典值） | `==` |
| `DICTIONARY_SET`（字典集合） | `IN_SET` |
| `NUMERIC`（数值型） | `==`, `!=`, `>`, `<`, `>=`, `<=` |
| `STRING`（字符型） | `==`, `contains` |
| 通用 | `AI_AGENT`（占位扩展） |

**注意**: `DrlCompiler.buildConditionExpression()` 目前实际支持的操作符为：`==`/`equals`, `!=`/`notEquals`, `>`/`greaterThan`, `<`/`lessThan`, `>=`, `<=`, `contains`。`regex_match` 和 `IN_SET` 在条件模型配置中存在，但 DRL 生成器中可能未完全实现或需扩展。

---

## 10. 测试策略

**当前项目中无任何测试代码。**

`rule-engine-server/src/test/` 目录不存在。Maven 构建使用 `-DskipTests` 跳过测试。

若需添加测试，建议方向：
- **后端**: `DrlCompiler` 的单元测试（给定画布 JSON，验证生成的 DRL 文本）
- **后端**: `RuleExecutor` 的集成测试（编译并执行简单规则，验证返回结果）
- **后端**: Service 层的事务与删除保护测试
- **前端**: 画布节点渲染与连线交互的组件测试

---

## 11. 安全与部署注意事项

### 11.1 已知问题

1. **MySQL CLOB 兼容性**: `Rule` 实体的 `@Column(columnDefinition = "CLOB")` 在 MySQL 下不兼容。虽然 `DataInitializer` 有兜底建表逻辑，但 Hibernate `ddl-auto: update` 启动时会报 WARN。建议长期修复为使用 `@Column(columnDefinition = "LONGTEXT")` 或移除 `columnDefinition` 让 Hibernate 自动推断。
2. **CORS**: 后端所有 Controller 使用 `@CrossOrigin(origins = "*")`，生产环境应根据实际域名限制。
3. **SQL 注入**: `DrlCompiler` 中的条件值直接拼接到 DRL 字符串中，虽然 DRL 不是直接 SQL，但仍需确保输入值经过适当转义（目前仅做了 `"` 和 `\` 的转义）。

### 11.2 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `ruleengine` | MySQL 数据库名 |
| `MYSQL_USER` | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | `qiubin78` | MySQL 密码 |

### 11.3 日志

- 后端日志级别：`com.ruleengine` 为 DEBUG，`org.drools` 为 INFO
- SQL 语句打印已开启（`show-sql: true`, `format_sql: true`）

---

## 12. 扩展开发指引

### 12.1 添加新条件模型

在后端通过 `ConditionModelService` 创建：
```java
ConditionModel model = new ConditionModel();
model.setCode("NEW_FIELD");
model.setName("新字段");
model.setDataType(DataType.NUMERIC);
model.setOperators(Arrays.asList("==", "!=", ">", "<"));
model.setValueSource(ValueSource.PARAM);
model.setNodeUsage(NodeUsage.CONDITION);
model.setDataElementId(dataElementId);
model.setCategoryId(categoryId);
conditionModelService.save(model);
```

### 12.2 添加新计算符

修改 `DrlCompiler.buildConditionExpression()` 方法，增加新的 `operator` 分支，生成对应的 Drools 条件表达式。

### 12.3 添加新数据适配器

实现 `DataAdapter` 接口，注册到 `AdapterFactory`：
```java
@Component
public class MyAdapter implements DataAdapter {
    @Override
    public String getType() { return "MY_TYPE"; }
    @Override
    public Object adapt(String config, Map<String, Object> context) { /* ... */ }
}
```

---

## 13. 重要文件速查

| 文件 | 作用 |
|------|------|
| `rule-engine-server/pom.xml` | Maven 依赖与构建配置 |
| `rule-engine-server/src/main/resources/application.yml` | 数据库与服务器配置 |
| `rule-engine-ui/vite.config.js` | Vite 开发服务器与代理配置 |
| `rule-engine-ui/package.json` | npm 依赖与脚本 |
| `rule-engine-ui/index.html` | HTML 入口，含 DOM 防护 MutationObserver |
| `rule-engine-ui/src/main.jsx` | React Legacy 渲染入口 |
| `rule-engine-ui/src/App.jsx` | 页面路由与导航栏 |
| `start.sh` | 一键启动脚本 |
