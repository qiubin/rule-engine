package com.ruleengine.drools.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrlCompiler {

    private final ObjectMapper objectMapper;

    private static final String DRL_TEMPLATE = 
            "package rules.%s;\n" +
            "import java.util.Map;\n" +
            "import java.util.HashMap;\n" +
            "import com.ruleengine.script.RuleScriptUtils;\n" +
            "global java.util.Map result\n" +
            "\n" +
            "%s\n";

    @SneakyThrows
    public String compile(String ruleCode, JsonNode canvasData) {
        ArrayNode nodes = (ArrayNode) canvasData.get("nodes");
        ArrayNode edges = (ArrayNode) canvasData.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            return "";
        }

        Map<String, JsonNode> nodeMap = new HashMap<>();
        Map<String, List<JsonNode>> adjacency = new HashMap<>();
        Map<String, List<JsonNode>> reverseAdjacency = new HashMap<>();
        
        for (JsonNode node : nodes) {
            String id = node.get("id").asText();
            nodeMap.put(id, node);
            adjacency.put(id, new ArrayList<>());
            reverseAdjacency.put(id, new ArrayList<>());
        }
        
        if (edges != null) {
            for (JsonNode edge : edges) {
                String source = edge.get("source").asText();
                String target = edge.get("target").asText();
                adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(edge);
                reverseAdjacency.computeIfAbsent(target, k -> new ArrayList<>()).add(edge);
            }
        }

        // 预计算 and/or 节点的上游条件组合
        Map<String, String> gateConditions = new HashMap<>();
        for (JsonNode node : nodes) {
            String type = node.get("type").asText();
            if ("and".equals(type) || "or".equals(type)) {
                String nodeId = node.get("id").asText();
                String separator = "and".equals(type) ? " && " : " || ";
                String combined = combineUpstreamConditions(nodeId, nodeMap, reverseAdjacency, separator, new HashSet<>());
                gateConditions.put(nodeId, combined);
            }
        }

        String startNodeId = nodeMap.values().stream()
                .filter(n -> "start".equals(n.get("type").asText()))
                .findFirst()
                .map(n -> n.get("id").asText())
                .orElse(nodeMap.keySet().iterator().next());

        StringBuilder rulesBuilder = new StringBuilder();
        Set<String> generatedRules = new HashSet<>();
        
        generateRuleRecursive(ruleCode, startNodeId, nodeMap, adjacency, gateConditions,
                rulesBuilder, generatedRules, "", 0);

        String drl = String.format(DRL_TEMPLATE, sanitizePackage(ruleCode), rulesBuilder.toString());
        log.debug("生成的 DRL:\n{}", drl);
        return drl;
    }

    private void generateRuleRecursive(String ruleCode, String nodeId, 
                                       Map<String, JsonNode> nodeMap, 
                                       Map<String, List<JsonNode>> adjacency,
                                       Map<String, String> gateConditions,
                                       StringBuilder rulesBuilder, Set<String> generatedRules,
                                       String parentCondition, int depth) {
        if (depth > 50) {
            return;
        }

        JsonNode node = nodeMap.get(nodeId);
        if (node == null) return;

        String type = node.get("type").asText();
        
        // and/or 节点不受 generatedRules 限制（允许多入边重复访问）
        if (!"and".equals(type) && !"or".equals(type)) {
            String key = nodeId + "_" + parentCondition;
            if (generatedRules.contains(key)) {
                return;
            }
            generatedRules.add(key);
        }

        String nodeLabel = node.has("data") && node.get("data").has("label") 
                ? node.get("data").get("label").asText() 
                : nodeId;

        if ("start".equals(type)) {
            List<JsonNode> outEdges = adjacency.getOrDefault(nodeId, Collections.emptyList());
            for (JsonNode edge : outEdges) {
                generateRuleRecursive(ruleCode, edge.get("target").asText(), nodeMap, adjacency, gateConditions,
                        rulesBuilder, generatedRules, parentCondition, depth + 1);
            }
        } else if ("condition".equals(type)) {
            String conditionExpr = buildConditionExpression(node);
            String fullCondition = parentCondition.isEmpty() ? conditionExpr 
                    : parentCondition + " && " + conditionExpr;
            
            List<JsonNode> outEdges = adjacency.getOrDefault(nodeId, Collections.emptyList());
            for (JsonNode edge : outEdges) {
                String label = edge.has("label") ? edge.get("label").asText() : "";
                String sourceHandle = edge.has("sourceHandle") ? edge.get("sourceHandle").asText() : "";
                boolean isTrueBranch = "true".equals(sourceHandle) || "是".equals(label) || "true".equalsIgnoreCase(label) || label.isEmpty();
                
                String branchCondition = isTrueBranch ? fullCondition : negateCondition(fullCondition);
                String targetId = edge.get("target").asText();
                
                JsonNode targetNode = nodeMap.get(targetId);
                if (targetNode != null && "result".equals(targetNode.get("type").asText())) {
                    generateResultRule(ruleCode, nodeLabel, branchCondition, targetNode, rulesBuilder);
                } else {
                    generateRuleRecursive(ruleCode, targetId, nodeMap, adjacency, gateConditions,
                            rulesBuilder, generatedRules, branchCondition, depth + 1);
                }
            }
        } else if ("result".equals(type)) {
            generateResultRule(ruleCode, nodeLabel, parentCondition, node, rulesBuilder);
        } else if ("and".equals(type) || "or".equals(type)) {
            // 使用预计算的上游条件组合，替代 parentCondition
            String gateCondition = gateConditions.getOrDefault(nodeId, "");
            String finalCondition = gateCondition.isEmpty() ? parentCondition : gateCondition;
            
            List<JsonNode> outEdges = adjacency.getOrDefault(nodeId, Collections.emptyList());
            for (JsonNode edge : outEdges) {
                generateRuleRecursive(ruleCode, edge.get("target").asText(), nodeMap, adjacency, gateConditions,
                        rulesBuilder, generatedRules, finalCondition, depth + 1);
            }
        }
    }

    /**
     * 递归收集指定节点所有上游的 condition 节点条件，并用指定分隔符连接
     */
    private String combineUpstreamConditions(String nodeId, Map<String, JsonNode> nodeMap,
                                              Map<String, List<JsonNode>> reverseAdjacency,
                                              String separator, Set<String> visited) {
        if (visited.contains(nodeId)) return "";
        visited.add(nodeId);
        
        List<JsonNode> inEdges = reverseAdjacency.getOrDefault(nodeId, Collections.emptyList());
        List<String> conditions = new ArrayList<>();
        
        for (JsonNode edge : inEdges) {
            String sourceId = edge.get("source").asText();
            JsonNode sourceNode = nodeMap.get(sourceId);
            if (sourceNode == null) continue;
            
            String sourceType = sourceNode.get("type").asText();
            if ("condition".equals(sourceType)) {
                conditions.add(buildConditionExpression(sourceNode));
            } else if ("and".equals(sourceType) || "or".equals(sourceType)) {
                String subSep = "and".equals(sourceType) ? " && " : " || ";
                String subCond = combineUpstreamConditions(sourceId, nodeMap, reverseAdjacency, subSep, visited);
                if (!subCond.isEmpty()) conditions.add(subCond);
            }
        }
        
        return String.join(separator, conditions);
    }

    private String buildConditionExpression(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null || !data.has("conditionConfig")) {
            return "true";
        }
        JsonNode config = data.get("conditionConfig");
        String field = config.has("field") ? config.get("field").asText() : "param";
        String operator = config.has("operator") ? config.get("operator").asText() : "==";
        JsonNode value = config.get("value");
        
        String valueStr = value != null ? value.asText() : "";
        valueStr = valueStr.replace("\\", "\\\\").replace("\"", "\\\"");
        
        JsonNode extra1Node = config.get("extraValue1");
        String extraValue1 = extra1Node != null && !extra1Node.isNull() ? extra1Node.asText() : "";
        extraValue1 = extraValue1.replace("\\", "\\\\").replace("\"", "\\\"");

        JsonNode extra2Node = config.get("extraValue2");
        String extraValue2 = extra2Node != null && !extra2Node.isNull() ? extra2Node.asText() : "";
        extraValue2 = extraValue2.replace("\\", "\\\\").replace("\"", "\\\"");
        
        if ("==".equals(operator) || "equals".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && \"%s\".equals(String.valueOf($param.get(\"%s\")))", field, valueStr, field);
        } else if ("!=".equals(operator) || "notEquals".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && !\"%s\".equals(String.valueOf($param.get(\"%s\")))", field, valueStr, field);
        } else if (">".equals(operator) || "greaterThan".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && Double.parseDouble(String.valueOf($param.get(\"%s\"))) > %s", field, field, valueStr);
        } else if ("<".equals(operator) || "lessThan".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && Double.parseDouble(String.valueOf($param.get(\"%s\"))) < %s", field, field, valueStr);
        } else if (">=".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && Double.parseDouble(String.valueOf($param.get(\"%s\"))) >= %s", field, field, valueStr);
        } else if ("<=".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && Double.parseDouble(String.valueOf($param.get(\"%s\"))) <= %s", field, field, valueStr);
        } else if ("contains".equals(operator)) {
            return String.format("$param.get(\"%s\") != null && String.valueOf($param.get(\"%s\")).contains(\"%s\")", field, field, valueStr);
        } else if ("regexMatch".equals(operator)) {
            return String.format("RuleScriptUtils.regexMatch((String)$param.get(\"%s\"), \"%s\")", field, valueStr);
        } else if ("multiRegexMatch".equals(operator)) {
            String antiBioticsSet = valueStr;
            String courseRecordField = !extraValue1.isEmpty() ? extraValue1 : "courseRecord";
            return String.format("RuleScriptUtils.multiConditionRegexMatch(\"%s\", (String)$param.get(\"%s\"), (String)$param.get(\"%s\"))", antiBioticsSet, field, courseRecordField);
        } else if ("contradictionCheck".equals(operator)) {
            String negativeWords = valueStr;
            String keywords = extraValue1;
            String str2Field = !extraValue2.isEmpty() ? extraValue2 : "入院主诉";
            return String.format("RuleScriptUtils.contradictionCheck((String)$param.get(\"%s\"), (String)$param.get(\"%s\"), \"%s\", \"%s\")", field, str2Field, negativeWords, keywords);
        } else if ("dataCheck".equals(operator)) {
            String op = valueStr;
            String thresholdStr = extraValue1;
            return String.format("$param.get(\"%s\") != null && RuleScriptUtils.dataCheck(Double.valueOf(String.valueOf($param.get(\"%s\"))), \"%s\", Double.valueOf(\"%s\"))", field, field, op, thresholdStr);
        } else if ("timeCheck".equals(operator)) {
            String baseTimeField = valueStr;
            String minHours = (!extraValue1.trim().isEmpty() && !extraValue1.equalsIgnoreCase("null")) ? extraValue1 : "null";
            String maxHours = (!extraValue2.trim().isEmpty() && !extraValue2.equalsIgnoreCase("null")) ? extraValue2 : "null";
            return String.format("RuleScriptUtils.timeCheck((String)$param.get(\"%s\"), (String)$param.get(\"%s\"), %s, %s)", field, baseTimeField, minHours, maxHours);
        }
        return "true";
    }

    private String negateCondition(String condition) {
        return "!(" + condition + ")";
    }

    private void generateResultRule(String ruleCode, String nodeLabel, String condition, 
                                     JsonNode resultNode, StringBuilder rulesBuilder) {
        JsonNode data = resultNode.get("data");
        JsonNode resultConfig = data != null && data.has("resultConfig") ? data.get("resultConfig") : null;
        String resultType = resultConfig != null && resultConfig.has("resultType") ? resultConfig.get("resultType").asText() : "DEFAULT";
        String resultValue = resultConfig != null && resultConfig.has("resultValue") ? resultConfig.get("resultValue").asText() : nodeLabel;
        
        String ruleName = sanitizeRuleName(ruleCode + "_" + nodeLabel + "_" + System.currentTimeMillis());
        
        rulesBuilder.append("rule \"").append(ruleName).append("\"\n")
                .append("when\n")
                .append("    $param : Map(").append(condition).append(")\n")
                .append("then\n")
                .append("    Map r = new HashMap();\n")
                .append("    r.put(\"ruleCode\", \"").append(ruleCode).append("\");\n")
                .append("    r.put(\"nodeLabel\", \"").append(nodeLabel).append("\");\n")
                .append("    r.put(\"resultType\", \"").append(resultType).append("\");\n")
                .append("    r.put(\"resultValue\", \"").append(resultValue).append("\");\n")
                .append("    r.put(\"matched\", true);\n")
                .append("    result.put(\"result_\" + System.currentTimeMillis(), r);\n")
                .append("end\n")
                .append("\n");
    }

    private String sanitizePackage(String code) {
        String sanitized = code.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        if (sanitized.length() > 0 && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "r_" + sanitized;
        }
        return sanitized;
    }

    private String sanitizeRuleName(String name) {
        int len = Math.min(name.length(), 100);
        String sanitized = name.substring(0, len).replaceAll("[^a-zA-Z0-9_\u4e00-\u9fa5]", "_");
        if (sanitized.length() > 0 && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "r_" + sanitized;
        }
        return sanitized;
    }
}
