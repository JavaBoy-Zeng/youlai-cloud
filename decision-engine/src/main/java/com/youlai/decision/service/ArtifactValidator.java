package com.youlai.decision.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.DecisionArtifactMapper;
import com.youlai.decision.model.ArtifactKind;
import com.youlai.decision.model.DecisionArtifact;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 发布前策略资产完整性校验。
 */
@Service
public class ArtifactValidator {

    private static final Set<String> DECISION_TABLE_ACTION_KEYS = Set.of(
            "decisionResult", "riskLevel", "score", "tags", "reason", "outputs", "priority"
    );

    private final DecisionArtifactMapper artifactMapper;
    private final JsonService jsonService;

    /**
     * 创建资产校验器。
     *
     * @param artifactMapper 资产 Mapper
     * @param jsonService JSON 服务
     */
    public ArtifactValidator(DecisionArtifactMapper artifactMapper, JsonService jsonService) {
        this.artifactMapper = artifactMapper;
        this.jsonService = jsonService;
    }

    /**
     * 校验资产是否具备发布条件。
     *
     * @param artifact 待发布资产
     */
    public void validateBeforePublish(DecisionArtifact artifact) {
        Map<String, Object> content = jsonService.readMap(artifact.getContentJson());
        List<String> errors = new ArrayList<>();
        switch (artifact.getKind()) {
            case SCENE -> validateScene(content, errors);
            case RULE -> validateRule(artifact, content, errors);
            case RULE_SET -> validateRuleSet(content, errors);
            case FLOW -> validateFlow(content, errors);
            case SCORE_CARD -> validateScoreCard(content, errors);
            case DECISION_TABLE -> validateDecisionTable(content, errors);
            case DATA_SOURCE -> validateDataSource(content, errors);
            case MODEL -> validateModel(content, errors);
            case VARIABLE -> validateVariable(content, errors);
            default -> {
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("发布校验失败: " + String.join("；", errors));
        }
    }

    private void validateScene(Map<String, Object> content, List<String> errors) {
        if (!(content.get("outputs") instanceof List<?> outputs) || outputs.isEmpty()) {
            errors.add("场景必须配置 outputs");
        }
    }

    private void validateVariable(Map<String, Object> content, List<String> errors) {
        if (!StringUtils.hasText(Objects.toString(content.get("type"), ""))) {
            errors.add("变量必须配置 type");
        }
    }

    private void validateRule(DecisionArtifact artifact, Map<String, Object> content, List<String> errors) {
        if (!content.containsKey("conditions")) {
            errors.add("规则必须配置 conditions");
        }
        if (!content.containsKey("actions")) {
            errors.add("规则必须配置 actions");
        }
        String sceneCode = Objects.toString(content.get("sceneCode"), "");
        if (!StringUtils.hasText(sceneCode)) {
            errors.add("规则必须配置 sceneCode");
        } else if (find(ArtifactKind.SCENE, sceneCode) == null) {
            errors.add("规则引用的场景不存在: " + sceneCode);
        }
        validateFields(sceneCode, fieldsFromCondition(content.get("conditions")), errors);
        validateRuleConflicts(artifact, content, sceneCode, errors);
    }

    @SuppressWarnings("unchecked")
    private void validateRuleSet(Map<String, Object> content, List<String> errors) {
        if (!StringUtils.hasText(Objects.toString(content.get("sceneCode"), ""))) {
            errors.add("规则集必须配置 sceneCode");
        }
        Object ruleCodesValue = content.get("ruleCodes");
        if (!(ruleCodesValue instanceof List<?> ruleCodes) || ruleCodes.isEmpty()) {
            errors.add("规则集必须配置 ruleCodes");
            return;
        }
        for (Object ruleCode : ruleCodes) {
            if (find(ArtifactKind.RULE, String.valueOf(ruleCode)) == null) {
                errors.add("规则集引用的规则不存在: " + ruleCode);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateFlow(Map<String, Object> content, List<String> errors) {
        String sceneCode = Objects.toString(content.get("sceneCode"), "");
        if (!StringUtils.hasText(sceneCode)) {
            errors.add("决策流必须配置 sceneCode");
        } else if (find(ArtifactKind.SCENE, sceneCode) == null) {
            errors.add("决策流引用的场景不存在: " + sceneCode);
        }
        List<Map<String, Object>> nodes = content.get("nodes") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        if (nodes.isEmpty()) {
            errors.add("决策流必须配置 nodes");
            return;
        }
        long startCount = nodes.stream().filter(node -> "START".equalsIgnoreCase(Objects.toString(node.get("type"), ""))).count();
        long endCount = nodes.stream().filter(node -> "END".equalsIgnoreCase(Objects.toString(node.get("type"), ""))).count();
        if (startCount != 1) {
            errors.add("决策流必须且只能有一个 START 节点");
        }
        if (endCount < 1) {
            errors.add("决策流至少需要一个 END 节点");
        }
        for (Map<String, Object> node : nodes) {
            validateFlowNode(sceneCode, node, errors);
        }
        List<Map<String, Object>> edges = content.get("edges") instanceof List<?> edgeList
                ? (List<Map<String, Object>>) (List<?>) edgeList
                : List.of();
        validateFlowConnectivity(nodes, edges, errors);
    }

    private void validateFlowNode(String sceneCode, Map<String, Object> node, List<String> errors) {
        String type = Objects.toString(node.getOrDefault("type", "RULE")).toUpperCase(Locale.ROOT);
        String code = Objects.toString(node.getOrDefault("code", ""), "");
        if (Set.of("RULE", "RULE_SET", "MODEL", "DATA", "SCORE_CARD", "DECISION_TABLE").contains(type)
                && !StringUtils.hasText(code)) {
            errors.add(type + " 节点必须配置 code");
            return;
        }
        ArtifactKind kind = switch (type) {
            case "RULE" -> ArtifactKind.RULE;
            case "RULE_SET" -> ArtifactKind.RULE_SET;
            case "MODEL" -> ArtifactKind.MODEL;
            case "DATA" -> ArtifactKind.DATA_SOURCE;
            case "SCORE_CARD" -> ArtifactKind.SCORE_CARD;
            case "DECISION_TABLE" -> ArtifactKind.DECISION_TABLE;
            default -> null;
        };
        if (kind != null && find(kind, code) == null) {
            errors.add(type + " 节点引用的资产不存在: " + code);
        }
        if ("CONDITION".equals(type)) {
            validateFields(sceneCode, fieldsFromCondition(node.get("conditions")), errors);
        }
    }

    private void validateFlowConnectivity(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, List<String> errors) {
        if (edges.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            nodeMap.put(nodeId(node), node);
        }
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        Map<String, List<String>> incoming = new LinkedHashMap<>();
        for (Map<String, Object> edge : edges) {
            String source = Objects.toString(edge.get("source"), "");
            String target = Objects.toString(edge.get("target"), "");
            if (!nodeMap.containsKey(source)) {
                errors.add("连线 source 不存在: " + source);
            }
            if (!nodeMap.containsKey(target)) {
                errors.add("连线 target 不存在: " + target);
            }
            outgoing.computeIfAbsent(source, key -> new ArrayList<>()).add(target);
            incoming.computeIfAbsent(target, key -> new ArrayList<>()).add(source);
        }
        String start = nodes.stream()
                .filter(node -> "START".equalsIgnoreCase(Objects.toString(node.get("type"), "")))
                .findFirst()
                .map(this::nodeId)
                .orElse("");
        if (!StringUtils.hasText(start)) {
            return;
        }
        Set<String> reachable = walk(start, outgoing);
        nodeMap.keySet().stream()
                .filter(id -> !reachable.contains(id))
                .forEach(id -> errors.add("节点不可从 START 到达: " + id));
        Set<String> endReachable = reverseEndReachable(nodes, incoming);
        nodeMap.keySet().stream()
                .filter(id -> !endReachable.contains(id))
                .forEach(id -> errors.add("节点无法到达 END: " + id));
    }

    @SuppressWarnings("unchecked")
    private void validateScoreCard(Map<String, Object> content, List<String> errors) {
        List<Map<String, Object>> items = content.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        if (items.isEmpty()) {
            errors.add("评分卡必须配置 items");
        }
        Set<String> fields = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String field = Objects.toString(item.get("field"), "");
            if (!StringUtils.hasText(field)) {
                errors.add("评分卡 item 必须配置 field");
            }
            fields.add(field);
            if (!(item.get("ranges") instanceof List<?> ranges) || ranges.isEmpty()) {
                errors.add("评分卡字段 " + field + " 必须配置 ranges");
            }
        }
        validateFields(Objects.toString(content.get("sceneCode"), ""), fields, errors);
    }

    @SuppressWarnings("unchecked")
    private void validateDecisionTable(Map<String, Object> content, List<String> errors) {
        List<Map<String, Object>> rows = content.get("rows") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        if (rows.isEmpty()) {
            errors.add("决策表必须配置 rows");
            return;
        }
        Set<String> fields = new LinkedHashSet<>();
        Set<String> signatures = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Map<String, Object> conditions = new TreeMap<>();
            row.forEach((key, value) -> {
                if (!DECISION_TABLE_ACTION_KEYS.contains(key)) {
                    fields.add(key);
                    conditions.put(key, value);
                }
            });
            String signature = jsonService.write(conditions);
            if (!signatures.add(signature)) {
                errors.add("决策表存在重复条件行: 第" + (i + 1) + "行");
            }
            if (!row.containsKey("decisionResult") && !row.containsKey("outputs")) {
                errors.add("决策表第" + (i + 1) + "行必须配置 decisionResult 或 outputs");
            }
        }
        validateFields(Objects.toString(content.get("sceneCode"), ""), fields, errors);
    }

    private void validateDataSource(Map<String, Object> content, List<String> errors) {
        String type = Objects.toString(content.getOrDefault("type", "HTTP")).toUpperCase(Locale.ROOT);
        if ("HTTP".equals(type) && !StringUtils.hasText(endpoint(content))) {
            errors.add("HTTP 数据源必须配置 endpoint 或 url");
        }
    }

    private void validateModel(Map<String, Object> content, List<String> errors) {
        if (!StringUtils.hasText(endpoint(content)) && !content.containsKey("fallbackOutput") && !content.containsKey("fallbackScore")) {
            errors.add("模型必须配置 endpoint/url，或配置 fallbackOutput/fallbackScore");
        }
    }

    private void validateRuleConflicts(DecisionArtifact artifact, Map<String, Object> content, String sceneCode, List<String> errors) {
        if (!StringUtils.hasText(sceneCode)) {
            return;
        }
        String currentConditions = jsonService.write(content.getOrDefault("conditions", Map.of()));
        String currentActions = jsonService.write(content.getOrDefault("actions", Map.of()));
        for (DecisionArtifact rule : list(ArtifactKind.RULE)) {
            if (Objects.equals(rule.getId(), artifact.getId())) {
                continue;
            }
            Map<String, Object> other = jsonService.readMap(rule.getContentJson());
            if (!sceneCode.equals(Objects.toString(other.get("sceneCode"), ""))) {
                continue;
            }
            String otherConditions = jsonService.write(other.getOrDefault("conditions", Map.of()));
            if (!currentConditions.equals(otherConditions)) {
                continue;
            }
            String otherActions = jsonService.write(other.getOrDefault("actions", Map.of()));
            if (currentActions.equals(otherActions)) {
                errors.add("规则与 " + rule.getCode() + " 条件和动作重复");
            } else {
                errors.add("规则与 " + rule.getCode() + " 条件相同但动作不同，存在冲突");
            }
        }
    }

    private Set<String> fieldsFromCondition(Object condition) {
        Set<String> fields = new LinkedHashSet<>();
        collectConditionFields(conditionMap(condition), fields);
        return fields;
    }

    @SuppressWarnings("unchecked")
    private void collectConditionFields(Map<String, Object> condition, Set<String> fields) {
        if (condition == null || condition.isEmpty()) {
            return;
        }
        if (condition.get("items") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    collectConditionFields((Map<String, Object>) map, fields);
                }
            }
            return;
        }
        String field = Objects.toString(condition.get("field"), "");
        if (StringUtils.hasText(field)) {
            fields.add(field);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> conditionMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof List<?> list) {
            return Map.of("logic", "AND", "items", list);
        }
        return Map.of();
    }

    private void validateFields(String sceneCode, Set<String> fields, List<String> errors) {
        if (fields.isEmpty()) {
            return;
        }
        Set<String> knownFields = knownFields(sceneCode);
        if (knownFields.isEmpty()) {
            return;
        }
        for (String field : fields) {
            String root = field.contains(".") ? field.substring(0, field.indexOf('.')) : field;
            if (!knownFields.contains(field) && !knownFields.contains(root)) {
                errors.add("变量未定义: " + field);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> knownFields(String sceneCode) {
        Set<String> fields = new LinkedHashSet<>();
        for (DecisionArtifact variable : list(ArtifactKind.VARIABLE)) {
            fields.add(variable.getCode());
        }
        DecisionArtifact scene = StringUtils.hasText(sceneCode) ? find(ArtifactKind.SCENE, sceneCode) : null;
        if (scene != null) {
            Map<String, Object> content = jsonService.readMap(scene.getContentJson());
            if (content.get("inputs") instanceof List<?> inputs) {
                for (Object input : inputs) {
                    if (input instanceof Map<?, ?> map) {
                        fields.add(Objects.toString(((Map<String, Object>) map).get("field"), ""));
                    }
                }
            }
        }
        fields.remove("");
        return fields;
    }

    private Set<String> walk(String start, Map<String, List<String>> outgoing) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            outgoing.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return visited;
    }

    private Set<String> reverseEndReachable(List<Map<String, Object>> nodes, Map<String, List<String>> incoming) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        nodes.stream()
                .filter(node -> "END".equalsIgnoreCase(Objects.toString(node.get("type"), "")))
                .map(this::nodeId)
                .forEach(queue::addLast);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            incoming.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return visited;
    }

    private String nodeId(Map<String, Object> node) {
        return Objects.toString(node.getOrDefault("id", node.getOrDefault("code", "")), "");
    }

    private String endpoint(Map<String, Object> content) {
        return Objects.toString(content.getOrDefault("endpoint", content.getOrDefault("url", "")), "");
    }

    private DecisionArtifact find(ArtifactKind kind, String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return artifactMapper.selectOne(Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind)
                .eq(DecisionArtifact::getCode, code)
                .last("LIMIT 1"));
    }

    private List<DecisionArtifact> list(ArtifactKind kind) {
        return artifactMapper.selectList(Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }
}
