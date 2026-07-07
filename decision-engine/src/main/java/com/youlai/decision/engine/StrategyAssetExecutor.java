package com.youlai.decision.engine;

import com.youlai.decision.model.DecisionArtifact;
import com.youlai.decision.service.JsonService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 执行评分卡和决策表这类表格化策略资产。
 */
@Component
public class StrategyAssetExecutor {

    private static final Set<String> ACTION_KEYS = Set.of(
            "decisionResult", "riskLevel", "score", "tags", "reason", "outputs", "priority"
    );

    private final JsonService jsonService;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * 创建表格化策略执行器。
     *
     * @param jsonService JSON 服务
     * @param conditionEvaluator 条件解释器
     */
    public StrategyAssetExecutor(JsonService jsonService, ConditionEvaluator conditionEvaluator) {
        this.jsonService = jsonService;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * 执行评分卡并返回一组可合并到决策上下文的动作。
     *
     * @param scoreCard 评分卡资产
     * @param facts 输入事实
     * @return 评分卡执行结果
     */
    @SuppressWarnings("unchecked")
    public Optional<StrategyExecution> executeScoreCard(DecisionArtifact scoreCard, Map<String, Object> facts) {
        Map<String, Object> content = jsonService.readMap(scoreCard.getContentJson());
        List<Map<String, Object>> items = content.get("items") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        int score = 0;
        List<String> reasons = new ArrayList<>();
        Map<String, Object> detail = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String field = Objects.toString(item.get("field"), "");
            Object actual = conditionEvaluator.getValue(facts, field);
            int itemScore = matchScore(item, facts);
            score += Math.max(itemScore, 0);
            Map<String, Object> itemDetail = new LinkedHashMap<>();
            itemDetail.put("value", actual);
            itemDetail.put("score", itemScore);
            detail.put(field, itemDetail);
            if (itemScore > 0) {
                reasons.add(field + "+" + itemScore);
            }
        }
        Map<String, Object> mapped = mapScore(content, score);
        Map<String, Object> actions = new LinkedHashMap<>(mapped);
        actions.put("score", score);
        actions.putIfAbsent("reason", reasons.isEmpty() ? scoreCard.getName() : String.join(",", reasons));
        actions.putIfAbsent("outputs", Map.of(scoreCard.getCode(), Map.of("score", score, "detail", detail)));
        return Optional.of(new StrategyExecution(scoreCard.getCode(), scoreCard.getName(), actions));
    }

    /**
     * 执行决策表。
     *
     * @param decisionTable 决策表资产
     * @param facts 输入事实
     * @return 命中的表格行动作
     */
    @SuppressWarnings("unchecked")
    public List<StrategyExecution> executeDecisionTable(DecisionArtifact decisionTable, Map<String, Object> facts) {
        Map<String, Object> content = jsonService.readMap(decisionTable.getContentJson());
        String hitPolicy = Objects.toString(content.getOrDefault("hitPolicy", "FIRST")).toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rows = content.get("rows") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        List<StrategyExecution> executions = new ArrayList<>();
        int rowIndex = 0;
        for (Map<String, Object> row : rows) {
            rowIndex++;
            if (!rowMatches(row, facts)) {
                continue;
            }
            Map<String, Object> actions = actionsFromRow(row, rowIndex);
            executions.add(new StrategyExecution(
                    decisionTable.getCode() + "#" + rowIndex,
                    decisionTable.getName() + "第" + rowIndex + "行",
                    actions
            ));
            if ("FIRST".equals(hitPolicy) || "UNIQUE".equals(hitPolicy)) {
                break;
            }
        }
        return executions;
    }

    private int matchScore(Map<String, Object> item, Map<String, Object> facts) {
        String field = Objects.toString(item.get("field"), "");
        Object rangesValue = item.get("ranges");
        if (!(rangesValue instanceof List<?> ranges)) {
            return 0;
        }
        for (Object rangeValue : ranges) {
            if (!(rangeValue instanceof Map<?, ?> rawRange)) {
                continue;
            }
            Map<String, Object> range = castMap(rawRange);
            if (rangeMatches(field, range, facts)) {
                return intValue(range.getOrDefault("score", 0));
            }
        }
        return 0;
    }

    private boolean rangeMatches(String field, Map<String, Object> range, Map<String, Object> facts) {
        if (range.containsKey("condition")) {
            return conditionEvaluator.matches(castMap(range.get("condition")), facts);
        }
        if (range.containsKey("between")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "between", "value", range.get("between")), facts);
        }
        if (range.containsKey("operator")) {
            return conditionEvaluator.matches(Map.of(
                    "field", field,
                    "operator", range.get("operator"),
                    "value", range.get("value")
            ), facts);
        }
        if (range.containsKey("value")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "=", "value", range.get("value")), facts);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapScore(Map<String, Object> content, int score) {
        List<Map<String, Object>> mapping = content.get("mapping") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        return mapping.stream()
                .sorted(Comparator.comparingInt(item -> -intValue(item.getOrDefault("min", 0))))
                .filter(item -> score >= intValue(item.getOrDefault("min", 0)))
                .findFirst()
                .map(item -> {
                    Map<String, Object> actions = new LinkedHashMap<>(item);
                    actions.remove("min");
                    return actions;
                })
                .orElseGet(() -> Map.of("decisionResult", "PASS", "riskLevel", "LOW"));
    }

    private boolean rowMatches(Map<String, Object> row, Map<String, Object> facts) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (ACTION_KEYS.contains(entry.getKey())) {
                continue;
            }
            if (!cellMatches(entry.getKey(), entry.getValue(), facts)) {
                return false;
            }
        }
        return true;
    }

    private boolean cellMatches(String field, Object cell, Map<String, Object> facts) {
        if (cell == null) {
            return true;
        }
        if (cell instanceof Map<?, ?> map) {
            return conditionEvaluator.matches(castMap(map), facts);
        }
        String text = String.valueOf(cell).trim();
        if (text.isEmpty() || "*".equals(text) || "ANY".equalsIgnoreCase(text)) {
            return true;
        }
        if (text.startsWith(">=")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", ">=", "value", text.substring(2).trim()), facts);
        }
        if (text.startsWith("<=")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "<=", "value", text.substring(2).trim()), facts);
        }
        if (text.startsWith("!=")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "!=", "value", text.substring(2).trim()), facts);
        }
        if (text.startsWith(">")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", ">", "value", text.substring(1).trim()), facts);
        }
        if (text.startsWith("<")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "<", "value", text.substring(1).trim()), facts);
        }
        if (text.contains("..")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "between", "value", text.split("\\.\\.", 2)), facts);
        }
        if (text.contains(",")) {
            return conditionEvaluator.matches(Map.of("field", field, "operator", "in", "value", text), facts);
        }
        return conditionEvaluator.matches(Map.of("field", field, "operator", "=", "value", text), facts);
    }

    private Map<String, Object> actionsFromRow(Map<String, Object> row, int rowIndex) {
        Map<String, Object> actions = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            if (ACTION_KEYS.contains(key)) {
                actions.put(key, value);
            }
        });
        actions.putIfAbsent("decisionResult", "REVIEW");
        actions.putIfAbsent("riskLevel", "MEDIUM");
        actions.putIfAbsent("reason", "命中决策表第" + rowIndex + "行");
        actions.putIfAbsent("outputs", Map.of("decisionTableRow", rowIndex));
        return actions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> castMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * 表格化策略命中后产生的动作。
     *
     * @param code 命中编码
     * @param name 命中名称
     * @param actions 动作配置
     */
    public record StrategyExecution(String code, String name, Map<String, Object> actions) {
    }
}
