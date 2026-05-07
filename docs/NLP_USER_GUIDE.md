# 规则引擎 NLP 功能操作说明

## 一、概述

本系统基于 HanLP 实现了病历文本的医学实体提取与否定检测能力。规则画布上新增了 4 个 **NLP 计算符**，可配合病历文书内容（EMR_CONTENT）或 NLP 衍生数据元（POSITIVE_SYMPTOMS 等）进行质控判断。

**支持的医学实体类型**：
- 症状（symptoms）
- 体征（signs）
- 药品（drugs）
- 检查（exams）
- 手术（surgeries）
- 疾病（diseases）

**NLP 衍生数据元**（系统启动时自动初始化）：
- POSITIVE_SYMPTOMS / NEGATIVE_SYMPTOMS
- POSITIVE_SIGNS / NEGATIVE_SIGNS
- POSITIVE_DRUGS / POSITIVE_EXAMS
- POSITIVE_SURGERIES / POSITIVE_DISEASES

---

## 二、词典管理（NLP 基础）

### 2.1 医学词典

系统在启动时会自动将数据库中的字典表导出为 HanLP 自定义词典。需要识别的医学实体应维护在以下字典中：

| 字典编码 | 用途 | HanLP 词性 |
|----------|------|-----------|
| SYMPTOM_POSITIVE | 阳性症状 | nsym |
| SIGN_POSITIVE | 阳性体征 | nsig |
| DRUG | 药品 | ndrg |
| EXAM | 检查 | nexm |
| SURGERY | 手术 | nsur |
| DISEASE / ICD10 | 疾病 | ndis |
| NEGATION_WORDS | 否定词 | nneg |

**操作路径**：系统管理 → 字典管理 → 选择字典 → 添加/编辑字典项

**说明**：
- 字典项的 `itemName` 会被导出为 HanLP 词典词条
- 新增/修改字典后，**重启应用**即可重新导出词典
- 词典文件生成在应用运行目录的 `data/dictionary/custom/` 下

---

## 三、画布配置 — NLP 计算符

在规则画布的条件节点选择 **"NLP计算符"** 分组下的操作符。

### 3.1 医学实体提取（medicalNer）

**用途**：判断病历文本中是否包含某类医学实体（或特定实体）。

**配置示例 1**：检查病程记录中是否提到任何症状
- 条件字段：`emrContent`（或 `courseRecord`）
- 计算符：`medicalNer`
- 实体类型：`symptoms`
- 特定实体：（留空）

**配置示例 2**：检查主诉中是否提到"发热"
- 条件字段：`chiefComplaint`
- 计算符：`medicalNer`
- 实体类型：`symptoms`
- 特定实体：`发热`

**应用场景**：
- R9：主诉为"发热"但入院记录中未提及 → `medicalNer` 检测 `symptoms` 是否含"发热"
- R17：阴性症状少于 5 个 → 使用 `arrayLength` 检测 `negativeSymptoms` 长度

### 3.2 否定检测（negationCheck）

**用途**：判断文本中某实体是否被否定（如"无发热"中的"发热"）。

**配置示例**：
- 条件字段：`chiefComplaint`
- 计算符：`negationCheck`
- 实体名称：`发热`

**命中条件**：文本中包含"无发热"、"否认发热"等否定表达时，返回 true。

**应用场景**：
- R29：主诉中提到症状但被全部否定（"无发热、无咳嗽"），需要进一步确认
- 结合 `allNegated` 使用效果更佳

### 3.3 分词相似度（tokenSimilarity）

**用途**：基于 HanLP 分词的 Jaccard 系数判断两段文本相似度，比字符级相似度更语义化。

**配置示例**（病程记录雷同判断）：
- 条件字段：`firstCourseRecord`
- 计算符：`tokenSimilarity`
- 对比字段名：`latestDailyCourseRecord`
- 相似度阈值：`0.95`

**与 similarity 的区别**：
- `similarity`：字符二元组 Jaccard（速度快，对近义改写敏感）
- `tokenSimilarity`：HanLP 分词后 Jaccard（识别"发热"与"发烧"为不同词，但对句式变化更鲁棒）

**建议**：病程记录雷同检测优先使用 `tokenSimilarity`，阈值建议 0.90~0.95。

### 3.4 全否定检测（allNegated）

**用途**：检查文本中某类实体是否全部被否定。

**配置示例**：
- 条件字段：`chiefComplaint`
- 计算符：`allNegated`
- 实体类型：`symptoms`

**命中条件**：文本中提取到的所有症状实体都被否定时返回 true。例如"无发热、无咳嗽、无头痛" → true；"无发热，有咳嗽" → false。

**应用场景**：
- R29/R30：主诉中提到的症状全被否定，提示主诉描述不规范

---

## 四、与 HIS 数据联用

当规则执行时传入 `patientId`，系统会自动：

1. 从 HIS 拉取患者病历数据
2. 对病程记录文本进行 NLP 处理
3. 将结果写入 `$param`：
   - `positiveSymptoms`：阳性症状列表（如 `["发热", "咳嗽"]`）
   - `negativeSymptoms`：阴性症状列表（如 `["胸痛", "呼吸困难"]`）
   - `positiveSigns`、`negativeSigns` 等同理

**在画布中直接使用 NLP 衍生字段**：

无需配置 `medicalNer`，直接使用数据元 `POSITIVE_SYMPTOMS`：
- 条件字段：`positiveSymptoms`
- 计算符：`contains`
- 条件值：`发热`

或检测阴性症状数量：
- 条件字段：`negativeSymptoms`
- 计算符：`arrayLength`
- 比较符：`<`
- 阈值：`5`

---

## 五、典型质控规则配置示例

### 示例 1：R9 — 主诉与入院记录症状不一致

**逻辑**：主诉提到"发热"，但入院记录（病程）中未提及。

**画布配置**：
1. 条件节点 A：
   - 条件：`chiefComplaint`
   - 计算符：`medicalNer`
   - 实体类型：`symptoms`
   - 特定实体：`发热`
2. 条件节点 B（AND 连接）：
   - 条件：`firstCourseRecord`
   - 计算符：`medicalNer`
   - 实体类型：`symptoms`
   - 特定实体：`发热`
3. 结果节点：B 取 false 分支 → 提示"主诉发热但病程未描述"

### 示例 2：R17 — 阴性症状过少

**逻辑**：病历中阴性症状少于 5 个，可能描述不完整。

**画布配置**：
1. 条件节点：
   - 条件：`negativeSymptoms`（需 patientId 触发 NLP）
   - 计算符：`arrayLength`
   - 比较符：`<`
   - 阈值：`5`
2. 结果节点：true 分支 → 提示"阴性症状描述不完整"

### 示例 3：R56 — 病程记录雷同

**逻辑**：首次病程记录与日常病程记录相似度过高。

**画布配置**：
1. 条件节点：
   - 条件：`firstCourseRecord`
   - 计算符：`tokenSimilarity`
   - 对比字段：`latestDailyCourseRecord`
   - 阈值：`0.95`
2. 结果节点：true 分支 → 提示"病程记录可能存在复制粘贴"

### 示例 4：R29 — 主诉症状全被否定

**逻辑**：主诉中提到的症状全部被否定，主诉无实际意义。

**画布配置**：
1. 条件节点：
   - 条件：`chiefComplaint`
   - 计算符：`allNegated`
   - 实体类型：`symptoms`
2. 结果节点：true 分支 → 提示"主诉症状全部被否定，请确认"

---

## 六、注意事项

1. **字典维护**：医学实体识别的准确性依赖字典覆盖度。初期字典条目较少时，NLP 可能识别不到部分专业术语，需持续补充。

2. **否定检测范围**：否定词扫描窗口为实体前 15 个字符。对于跨度较大的否定（如"昨日发热，今日已无"），可能检测不到"昨日"的肯定表达。

3. **性能**：HanLP 分词在首次加载时较慢（约 1~2 秒），后续缓存。批量执行规则时建议预热。

4. **HIS 依赖**：NLP 衍生数据元（positiveSymptoms 等）仅在传入 `patientId` 时自动生成。纯参数测试时，需手动传入这些字段。

5. **相似度阈值建议**：
   - 字符相似度（similarity）：0.995+（对雷同极敏感）
   - 分词相似度（tokenSimilarity）：0.90~0.95（对近义改写更鲁棒）
