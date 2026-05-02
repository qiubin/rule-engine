# 基于 Drools 的可视化规则引擎

> 企业级医疗规则引擎系统，支持可视化画布编排、动态规则编译与执行。

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端层 (React 18)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  ReactFlow   │  │  Ant Design  │  │   Zustand    │      │
│  │  可视化画布   │  │   UI 组件    │  │   状态管理   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────┬────────────────────────────────────┘
                         │ REST API
┌────────────────────────▼────────────────────────────────────┐
│                     后端层 (Spring Boot 3)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Controller  │  │   Service    │  │  Repository  │      │
│  │   REST API   │  │  业务逻辑    │  │   JPA 数据   │      │
│  └──────────────┘  └──────┬───────┘  └──────────────┘      │
│                           │                                 │
│  ┌────────────────────────▼────────────────────────┐       │
│  │              Drools 规则引擎核心                  │       │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐  │       │
│  │  │ DrlCompiler│ │RuleExecutor│ │DataAdapter │  │       │
│  │  │ 画布→DRL   │ │ 规则执行   │ │ 数据适配   │  │       │
│  │  └────────────┘ └────────────┘ └────────────┘  │       │
│  └─────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

## 项目结构

```
rule-engine/
├── rule-engine-server/          # 后端服务（Spring Boot + Drools）
│   ├── src/main/java/com/ruleengine/
│   │   ├── domain/              # 领域实体
│   │   ├── repository/          # JPA 数据访问
│   │   ├── service/             # 业务逻辑
│   │   ├── controller/          # REST API
│   │   ├── drools/              # Drools 集成
│   │   │   ├── compiler/        # 动态 DRL 编译器
│   │   │   ├── runtime/         # 规则执行运行时
│   │   │   └── adapter/         # 数据适配器（参数/SQL/适配器）
│   │   └── config/              # 全局配置
│   └── src/main/resources/
│       ├── db/                  # 数据库初始化脚本
│       └── application.yml
├── rule-engine-ui/              # 前端应用（React + ReactFlow）
│   ├── src/
│   │   ├── components/
│   │   │   ├── Canvas/          # 画布核心
│   │   │   ├── Nodes/           # 节点类型组件
│   │   │   ├── ConfigPanel/     # 节点配置面板
│   │   │   └── ToolBar/         # 左侧工具栏
│   │   └── pages/
│   │       └── RuleEditor/      # 规则编辑页
│   └── package.json
└── README.md
```

## 核心概念

| 术语 | 说明 |
|------|------|
| **规则类型** | 按业务区分：合理性质控、病历质控、医保稽核、护理决策、VTE防治 |
| **条件模型** | 规则类型下的可配置条件来源：患者信息、医嘱、诊断、检验/检查结果、病历文书 |
| **条件** | IF 部分：字段 + 计算符 + 条件值 |
| **条件值** | 来源：参数传参 / SQL查询 / 适配器 |
| **计算符** | 根据数据类型变化：=, !=, >, <, contains, regex_match, IN_SET |
| **结果配置** | THEN 部分：禁忌类别、药品禁忌级别、护理分级等 |
| **画布节点** | 可视化编排单元：开始、条件、结果、AND、OR、结束 |

## 数据类型与计算符映射

| 条件性质 | 计算符 |
|---------|--------|
| 字典值 | =（等于） |
| 字典集合 | IN_SET（在集合中） |
| 数值型 | =, !=, >, < |
| 字符型 | =, contains, regex_match |
| 时间型 | =, before, after, between |
| 通用 | AI_AGENT |

## 快速启动

### 环境要求

- Java 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.0

### 1. 启动后端

```bash
cd rule-engine-server
mvn clean install
mvn spring-boot:run
```

后端服务启动在 `http://localhost:8080`

- API 文档：通过 Controller 代码查看

### 2. 启动前端

```bash
cd rule-engine-ui
npm install
npm run dev
```

前端服务启动在 `http://localhost:3000`

### 3. 初始化数据

后端启动时会自动执行 `db/schema.sql` 和 `db/data.sql`，预置：
- 5 种规则类型
- 8 个条件模型（患者年龄、性别、诊断、检验、药品、病历、血压、体温）
- 对应计算符和结果配置

## 核心 API

### 规则类型管理
```
GET    /api/v1/rule-types                    # 规则类型列表
GET    /api/v1/rule-types/{id}               # 规则类型详情
POST   /api/v1/rule-types                    # 创建规则类型
```

### 条件模型管理
```
GET    /api/v1/condition-models               # 条件模型列表
GET    /api/v1/condition-models/{id}          # 条件模型详情
GET    /api/v1/condition-models/by-rule-type/{ruleTypeId}  # 按类型查询
POST   /api/v1/condition-models               # 创建条件模型
```

### 规则管理（核心）
```
GET    /api/v1/rules                          # 规则列表
POST   /api/v1/rules                          # 创建规则
GET    /api/v1/rules/{id}                     # 规则详情
PUT    /api/v1/rules/{id}                     # 更新规则
PUT    /api/v1/rules/{id}/canvas              # 保存画布数据（自动生成 DRL）
POST   /api/v1/rules/{id}/publish             # 发布规则（编译缓存）
POST   /api/v1/rules/{id}/execute             # 执行规则
POST   /api/v1/rules/execute?ruleCode=xxx     # 通过编码执行
```

## 画布使用指南

### 节点类型

| 节点 | 说明 | 用途 |
|------|------|------|
| 🟢 开始 | 流程起点 | 每个规则必须有且仅有一个 |
| 🔵 条件 | IF 判断 | 配置字段、计算符、条件值 |
| 🟡 结果 | THEN 结果 | 配置结果类型和返回值 |
| 🟣 AND | 逻辑与 | 多个条件同时满足 |
| 🟣 OR | 逻辑或 | 任一条件满足 |
| 🔴 结束 | 流程终点 | 规则执行终止 |

### 编排流程

1. 从左侧工具箱拖拽节点到画布
2. 拖拽节点边缘的连接点建立连线
3. 双击节点打开配置面板
4. 条件节点配置：字段名、计算符、条件值、值来源
5. 结果节点配置：结果类型、结果值、优先级
6. 点击「保存画布」保存节点布局（自动生成 Drools DRL）
7. 点击「发布」使规则生效
8. 点击「测试执行」验证规则逻辑

## Drools DRL 生成示例

画布配置：
```
[开始] → [条件: patientAge > 65] → [结果: 老年患者预警]
```

自动生成 DRL：
```drools
package rules.age_check_001

rule "age_check_001_条件_1234567890"
when
    $param : Map($param.get("patientAge") != null && Double.parseDouble(String.valueOf($param.get("patientAge"))) > 65)
then
    Map r = new HashMap();
    r.put("ruleCode", "AGE_CHECK_001");
    r.put("nodeLabel", "条件");
    r.put("resultType", "ALERT");
    r.put("resultValue", "老年患者预警");
    r.put("matched", true);
    result.put("result_" + System.currentTimeMillis(), r);
end
```

## 数据适配器

| 适配器 | 类型标识 | 说明 |
|--------|---------|------|
| ParamAdapter | PARAM | 从请求参数直接取值 |
| SqlAdapter | SQL | 执行 SQL 查询获取数据 |

扩展适配器：实现 `DataAdapter` 接口并注册到 `AdapterFactory`。

## 扩展开发

### 添加新条件模型

```java
ConditionModel model = new ConditionModel();
model.setCode("NEW_FIELD");
model.setName("新字段");
model.setCategory(ConditionCategory.PATIENT_INFO);
model.setDataType(DataType.NUMERIC);
model.setOperators(Arrays.asList("==", "!=", ">", "<"));
model.setValueSource(ValueSource.PARAM);
conditionModelService.save(model);
```

### 添加新脚本类型

在 `DrlCompiler` 中扩展 `buildConditionExpression` 方法，支持新的计算符逻辑。

## 路线图

- [x] 后端基础架构（Spring Boot + JPA + MySQL）
- [x] Drools 动态规则编译与执行
- [x] ReactFlow 可视化画布（基础节点 + 连线）
- [x] 条件节点 / 结果节点配置面板
- [x] 规则保存、发布、执行全流程
- [ ] 病历文书数据适配器（EMR 集成）
- [ ] 脚本引擎（正则匹配、矛盾判断、时间判断）
- [ ] 规则版本管理与对比
- [ ] 执行日志与轨迹追踪
- [ ] 画布缩略图预览
- [ ] 规则模板市场

## License

MIT
