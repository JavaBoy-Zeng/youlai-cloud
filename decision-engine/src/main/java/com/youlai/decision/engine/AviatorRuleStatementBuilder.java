package com.youlai.decision.engine;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;

@Component
public class AviatorRuleStatementBuilder {

    public String build(String expressionType, String conditionExpression, Map<String, Object> conditions) {
        String type = StringUtils.hasText(expressionType) ? expressionType.toUpperCase(Locale.ROOT) : "MIXED";
        if (("AVIATOR".equals(type) || "MIXED".equals(type)) && StringUtils.hasText(conditionExpression)) {
            return conditionExpression;
        }
        return buildCondition(conditions);
    }

    @SuppressWarnings("unchecked")
    public String buildCondition(Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return "true";
        }
        if (condition.get("items") instanceof List<?> items) {
            String logic = "OR".equalsIgnoreCase(Objects.toString(condition.get("logic"), "AND")) ? " || " : " && ";
            return "(" + items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> buildCondition((Map<String, Object>) item))
                    .filter(StringUtils::hasText)
                    .reduce((a, b) -> a + logic + b)
                    .orElse("true") + ")";
        }
        if (StringUtils.hasText(Objects.toString(condition.get("expression"), ""))) {
            return Objects.toString(condition.get("expression"));
        }
        if (StringUtils.hasText(Objects.toString(condition.get("customFunction"), ""))) {
            String function = Objects.toString(condition.get("customFunction"));
            Object value = condition.get("value");
            List<?> args = value instanceof List<?> list ? list : List.of(value);
            return function + "(" + args.stream().map(this::operand).reduce((a, b) -> a + ", " + b).orElse("") + ")";
        }
        String left = arithmeticExpression(condition);
        String operator = normalizeOperator(Objects.toString(condition.getOrDefault("operator", "=")));
        Object value = condition.get("value");
        return switch (operator) {
            case "in" -> "inList(" + left + ", " + quoteList(value) + ")";
            case "notin", "not_in", "not in" -> "!inList(" + left + ", " + quoteList(value) + ")";
            case "contains" -> "string.contains(" + left + ", " + literal(value) + ")";
            case "between" -> betweenExpression(left, value);
            case "isnull", "is_null", "is null" -> "(" + left + " == nil || " + left + " == '')";
            case "notnull", "isnotnull", "is_not_null", "is not null" -> "(" + left + " != nil && " + left + " != '')";
            default -> left + " " + operator + " " + literal(value);
        };
    }

    @SuppressWarnings("unchecked")
    private String arithmeticExpression(Map<String, Object> condition) {
        Object arithmetic = condition.get("arithmeticExpressions");
        if (!(arithmetic instanceof List<?> list) || list.isEmpty()) {
            return field(Objects.toString(condition.get("field"), ""));
        }
        StringBuilder builder = new StringBuilder();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> expr = (Map<String, Object>) raw;
            if (StringUtils.hasText(Objects.toString(expr.get("operator"), ""))) {
                builder.append(' ').append(expr.get("operator")).append(' ');
            }
            builder.append(operand(expr.get("operand")));
        }
        return builder.isEmpty() ? field(Objects.toString(condition.get("field"), "")) : builder.toString();
    }

    private String betweenExpression(String left, Object value) {
        List<?> range = value instanceof List<?> list ? list : Arrays.stream(String.valueOf(value).split(",")).map(String::trim).toList();
        if (range.size() < 2) {
            throw new IllegalArgumentException("between 操作符需要两个边界值");
        }
        return "betweenNum(" + left + ", " + literal(range.get(0)) + ", " + literal(range.get(1)) + ")";
    }

    private String normalizeOperator(String operator) {
        return switch (operator.trim().toLowerCase(Locale.ROOT)) {
            case "=", "eq" -> "==";
            case "!=", "ne" -> "!=";
            case ">", "gt" -> ">";
            case ">=", "gte" -> ">=";
            case "<", "lt" -> "<";
            case "<=", "lte" -> "<=";
            default -> operator.trim().toLowerCase(Locale.ROOT);
        };
    }

    private String operand(Object value) {
        String text = Objects.toString(value, "");
        if (text.startsWith("'") || text.startsWith("\"") || text.matches("-?\\d+(\\.\\d+)?")) {
            return text;
        }
        return field(text);
    }

    private String field(String value) {
        String field = value.replaceAll("[^A-Za-z0-9_.$]", "");
        return StringUtils.hasText(field) ? field : "nil";
    }

    private String literal(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "'" + String.valueOf(value).replace("'", Matcher.quoteReplacement("\\'")) + "'";
    }

    private String quoteList(Object value) {
        if (value instanceof Collection<?> collection) {
            return "'" + collection.stream().map(String::valueOf).reduce((a, b) -> a + ";" + b).orElse("") + "'";
        }
        return literal(value);
    }
}
