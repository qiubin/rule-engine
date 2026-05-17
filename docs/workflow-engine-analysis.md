# 规则引擎 → 流程引擎 改造可行性分析（更新版）

## Context

当前项目是基于 **Drools 7 + ReactFlow** 的医疗质控规则引擎，面向"单次评估"场景。用户希望改造为能支持"**自动编排流**"的流程引擎，**不需要人工审批/待办**，**可适度改造画布和操作符**，资源为 **1人、1-2个月**。

---

## 一、核心问题：规则 vs 流程 的本质差异

| 维度 | 当前（规则引擎） | 目标（自动编排引擎） |
|------|-----------------|---------------------|
| **执行模型** | `fireAllRules()` 一次性评估所有路径 | Step-by-Step：走完一步再决定下一步 |
| **有状态？** | 无状态，入参→出参 | 轻量状态（当前执行到哪个节点、中间变量） |
| **图结构** | 树状（START→多路径CONDITION→RESULT） | 有向无环图 DAG（可串行、并行、条件分支） |
| **节点语义** | 条件/结果/逻辑门 | 服务调用/条件判断/数据转换/子流程 |
| **数据流** | 全局 Map 输入→每个条件各自取值 | 节点间变量传递（上一步输出是下一步输入的一部分） |

**结论**：这不是"把规则引擎改掉"，而是在它**旁边加一层编排引擎**。规则引擎仍是条件评估的最佳工具，编排引擎负责"走哪一步"。

---

## 二、可行性结论：可行，且 1-2 个月可控

### 为什么可行

1. **没有人工任务**，省去了最复杂的部分（待办、时限、权限、暂停/恢复）
2. **画布可以适度改造**，现有 ReactFlow 基础设施可复用（节点拖拽、连线、配置面板）
3. **ReactFlow 本质上就是 DAG 编辑器**，画布上已有 START → CONDITION(,true/false) → RESULT 的模式，扩展语义即可
4. **数据适配器层已存在**（`ParamAdapter`、`SqlAdapter`、`EmrAdapter`），可直接用于 ServiceTask 调用
5. **Drools 可继续用于条件评估**，只是调用方式从 `fireAllRules()` 改为按需单条件评估

### 关键约束

1. **360Chrome 兼容**：ReactFlow 已在用、已验证，改造后继续兼容
2. **Java 8**：所有依赖必须兼容 Java 8
3. **MySQL 8**：新增流程实例表需与此共存

---

## 三、推荐方案：DAG 编排引擎（基于现有架构扩展）

### 架构概览

```
                    ┌──────────────────────────────┐
                    │   ReactFlow 画布（改造后）      │
                    │  START → ServiceTask → CONDITION → ... → END │
                    └──────────┬───────────────────┘
                               │ PUT /api/v1/processes/{id}/def
                               ▼
┌───────────────────────────────────────────────────────┐
│              ProcessDefinition (流程定义)               │
│  nodes[] + edges[] + nodeConfig (type, script, params) │
└───────────────────────────────────────────────────────┘
                               │ POST /api/v1/processes/{defId}/start
                               ▼
┌───────────────────────────────────────────────────────┐
│            ProcessInstance (流程实例运行时)              │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐          │
│  │ Step 1   │ → │ Step 2   │ → │ Step 3   │ ...      │
│  │ Service  │   │ Condition │   │ Service  │          │
│  └──────────┘   └────┬─────┘   └──────────┘          │
│                       │ true/false                     │
│                       ▼                                │
│                 ┌──────────┐                           │
│                 │ Step 2a  │                           │
│                 │ Service  │                           │
│                 └──────────┘                           │
└───────────────────────────────────────────────────────┘
                               │ 条件评估复用 Drools
                               ▼
┌───────────────────────────────────────────────────────┐
│  Drools (按需调用)    RuleScriptUtils / DictScriptService │
│  30+ 操作符 / NLP / 字典脚本                             │
└───────────────────────────────────────────────────────┘
```

### Step 1：改造画布节点类型（1 周）

保留现有的 ReactFlow 基础设施，**调整节点语义**：

| 新节点类型 | 对应旧类型 | 改造内容 |
|-----------|-----------|---------|
| **Start** | START | 不变，流程入口，配置入参定义 |
| **End** | END | 不变，流程出口，配置出参/结果 |
| **Condition** | CONDITION | **保留**，继续使用 Drools 评估 true/false |
| **Service** | RESULT → 升级 | 🔑 **核心改造**：从"写死 result"变为可调用 Spring Bean、SQL 适配器、HTTP API、脚本 |
| **AND** | AND | 不变，并行分支同步等待 |
| **OR** | OR | 不变，条件分支汇聚 |
| **Delay** | 🔺 **新增** | 等待指定时间后继续（睡眠/调度） |
| **Script** | 🔺 **新增** | 执行 Groovy/SpEL 脚本做数据转换或变量赋值 |

**前端改动点：**
- [ToolBar/index.jsx](../rule-engine-ui/src/components/ToolBar/index.jsx)：增加 Delay、Script 节点拖拽
- [Nodes/index.jsx](../rule-engine-ui/src/components/Nodes/index.jsx)：新增 DelayNode、ScriptNode 组件
- [ConfigPanel/index.jsx](../rule-engine-ui/src/components/ConfigPanel/index.jsx)：新增 ServiceTask 配置面板（选择 bean/method + 参数映射）、Script 配置面板

### Step 2：新增流程运行时引擎（4 周）

**新增 Java 类：**

| 类名 | 职责 |
|------|------|
| `ProcessDefinition` (entity) | 流程定义：存储 canvas_data + 解析后的步骤列表 + 参数映射定义 |
| `ProcessDefinitionService` | 部署/版本管理 |
| `ProcessInstance` (entity) | 流程实例：id, defId, status (RUNNING/COMPLETED/FAILED), variables (JSON), currentNodeId, startTime, endTime |
| `ProcessInstanceRepository` | JPA 仓库 |
| `ProcessExecutionEngine` | ⭐ **核心类**：DAG 执行器，负责步骤推进 |
| `StepExecutor` (interface) | 步骤执行器 SPI |
| `ConditionStepExecutor` | 调用 Drools 评估单一条件 |
| `ServiceStepExecutor` | 调用 Spring Bean / 适配器 |
| `ScriptStepExecutor` | 执行 Groovy/SpEL |
| `DelayStepExecutor` | 定时等待 |
| `AndStepExecutor` | 并行分支管理 |

**执行流程（ProcessExecutionEngine）：**

```
execute(instanceId):
  1. 加载 ProcessInstance + ProcessDefinition
  2. 查找当前节点 currentNodeId 的所有出边
  3. 对每个后续节点:
     a. Condition → 调用 Drools 单条件评估
        - true → 执行 true 分支后续节点
        - false → 执行 false 分支后续节点
     b. Service → 调用 StepExecutor，将结果写入 instance.variables
        - 完成后自动推进到下一个节点
     c. Delay → 调度定时任务，到期后继续
     d. Script → 执行脚本，结果写入 variables
     e. AND → 等待所有前置分支到达后，进入后续节点
  4. 到达 End → 标记 COMPLETED
  5. 过程中每一步都持久化 instance 状态
```

### Step 3：条件评估改造（1 周）

**不再编译整个画布为一条 DRL**。改为条件节点**单独**生成可评估的表达式：

```java
// 新增：单条件评估
ConditionEvaluator.evaluate(String operator, String field, String value, Map<String, Object> data)

// 内部调用现有的 RuleScriptUtils / DictScriptUtils 相同方法
// 不再需要 KieSession / fireAllRules
// 直接执行：data.get("field") != null && "value".equals(...)
```

这样可以：
- 去掉 KieSession 创建开销
- 支持单条件独立评估
- 同一个 30+ 操作符代码复用

### Step 4：新增 API（1 周）

| 端点 | 用途 |
|------|------|
| `POST /api/v1/process-defs` | 创建流程定义（保存画布时） |
| `POST /api/v1/process-defs/{id}/deploy` | 部署/发布流程 |
| `POST /api/v1/process-defs/{id}/start` | 启动流程实例，传入初始变量 |
| `GET /api/v1/process-instances/{id}` | 查询实例状态 + 当前节点 + 变量 |
| `POST /api/v1/process-instances/{id}/signal` | 手动触发继续（用于 Delay 节点唤醒） |
| `GET /api/v1/process-instances?status=RUNNING` | 实例列表 |

### Step 5：保留现有能力

以下**不需要改造**，直接复用：

- ✅ `RuleScriptUtils` 所有方法（`similarity`、`timeCheck`、`regexMatch`、`isBlank` 等）
- ✅ `DictScriptService` 字典脚本
- ✅ `NlpService` NLP 实体抽取
- ✅ `HisClient` / `EmrDataService` HIS 数据获取
- ✅ `DataAdapter` 适配器体系
- ✅ `SqlAdapter` SQL 查询适配器
- ✅ 所有字典、数据元、条件模型、数据集配置表

---

## 四、方案对比总结

| 方案 | 工作量 | 风险 | 可扩展性 | 推荐场景 |
|------|--------|------|---------|---------|
| **A: DAG 编排引擎**（推荐） | ⭐ **1-2 月** | 低-中 | 高 | **纯自动编排，无人工任务，可适度改造** |
| B: 集成 Flowable | 2-3 月 | 中 | 高 | 需要人工审批/待办/BPMN 标准 |
| C: 外层封装 | 1-2 周 | 低 | 低 | 仅需要简单顺序执行 |

**推荐 A 的理由**：
1. 纯自动编排 → 不需要 Flowable 的 HumanTask/待办/时限，引入它徒增复杂度
2. 可适度改造 → 画布升级不受限，可以按需调整节点类型
3. 1-2 月一人 → A 的 scope 合理，核心是 DAG 执行引擎，Drools/适配器全部复用
4. 操作符不变 → 条件评估逻辑完全复用现有代码
5. 360Chrome 兼容 → ReactFlow 已验证，不改前端框架

---

## 五、验证方法

1. **MVP 验证（第 3 周末）**：
   - 做一个最简单的流程：START → Service (调用现有 SqlAdapter) → CONDITION (复用 Drools operator) → Service (写结果) → END
   - 验证：保存画布 → 启动流程 → 自动执行完毕 → 查看中间变量
2. **操作符兼容性验证**：
   - 用 5 个最复杂的操作符（`similarity`、`medicalNer`、`fieldCompare`、`timeCheck`、`dictMatch`）作为条件节点，走一遍完整流程
3. **并行分支验证**：
   - START → AND → [Service A, Service B] → 汇聚 → END，验证并行执行
4. **执行性能**：对比当前 `fireAllRules()` 与分步执行在同数据量下的耗时
