package com.youlai.decision.engine;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class ConditionEvaluator {

    /**
     * 判断条件树是否匹配输入事实。
     *
     * @param condition 条件树或单个条件
     * @param facts 输入事实
     * @return 是否匹配
     */
    public boolean matches(Map<String, Object> condition, Map<String, Object> facts) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        if (condition.containsKey("items")) {
            return matchGroup(condition, facts);
        }
        return matchOne(condition, facts);
    }

    /**
     * 判断 AND/OR 条件组是否匹配。
     *
     * @param group 条件组
     * @param facts 输入事实
     * @return 是否匹配
     */
    @SuppressWarnings("unchecked")
    private boolean matchGroup(Map<String, Object> group, Map<String, Object> facts) {
        String logic = Objects.toString(group.getOrDefault("logic", "AND")).toUpperCase(Locale.ROOT);
        Object itemsValue = group.get("items");
        if (!(itemsValue instanceof List<?> items) || items.isEmpty()) {
            return true;
        }
        if ("OR".equals(logic)) {
            return items.stream().anyMatch(item -> matches((Map<String, Object>) item, facts));
        }
        return items.stream().allMatch(item -> matches((Map<String, Object>) item, facts));
    }

    /**
     * 判断单个字段条件是否匹配。
     *
     * @param condition 单个条件配置
     * @param facts 输入事实
     * @return 是否匹配
     */
    private boolean matchOne(Map<String, Object> condition, Map<String, Object> facts) {
        String field = Objects.toString(condition.get("field"), "");
        String operator = normalizeOperator(Objects.toString(condition.getOrDefault("operator", "=")));
        Object actual = getValue(facts, field);
        Object expected = condition.get("value");

        return switch (operator) {
            case "=", "eq" -> compare(actual, expected) == 0;
            case "!=", "ne" -> compare(actual, expected) != 0;
            case ">", "gt" -> compare(actual, expected) > 0;
            case ">=", "gte" -> compare(actual, expected) >= 0;
            case "<", "lt" -> compare(actual, expected) < 0;
            case "<=", "lte" -> compare(actual, expected) <= 0;
            case "in" -> in(actual, expected);
            case "notin", "not_in", "not in" -> !in(actual, expected);
            case "contains" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "between" -> between(actual, expected);
            case "regex" -> actual != null && Pattern.compile(String.valueOf(expected)).matcher(String.valueOf(actual)).find();
            case "isnull", "is_null", "is null" -> actual == null || String.valueOf(actual).isBlank();
            case "notnull", "isnotnull", "is_not_null", "is not null" -> actual != null && !String.valueOf(actual).isBlank();
            default -> throw new IllegalArgumentException("不支持的操作符: " + operator);
        };
    }

    /**
     * 从输入事实中按点路径取值，支持 Map 和 List 下标。
     *
     * @param facts 输入事实
     * @param path 点路径字段名
     * @return 字段值
     */
    @SuppressWarnings("unchecked")
    public Object getValue(Map<String, Object> facts, String path) {
        if (facts == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = facts;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
                continue;
            }
            if (current instanceof List<?> list) {
                try {
                    current = list.get(Integer.parseInt(part));
                    continue;
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
        return current;
    }

    /**
     * 归一化操作符文本。
     *
     * @param operator 原始操作符
     * @return 小写后的操作符
     */
    private String normalizeOperator(String operator) {
        return operator.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断实际值是否在期望集合中。
     *
     * @param actual 实际值
     * @param expected 期望集合或逗号分隔字符串
     * @return 是否包含
     */
    private boolean in(Object actual, Object expected) {
        if (expected instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> compare(actual, item) == 0);
        }
        return Arrays.stream(String.valueOf(expected).split(","))
                .map(String::trim)
                .anyMatch(item -> compare(actual, item) == 0);
    }

    /**
     * 判断实际值是否落在闭区间内。
     *
     * @param actual 实际值
     * @param expected 两个边界值
     * @return 是否在区间内
     */
    private boolean between(Object actual, Object expected) {
        List<?> range;
        if (expected instanceof List<?> list) {
            range = list;
        } else {
            range = Arrays.stream(String.valueOf(expected).split(",")).map(String::trim).toList();
        }
        if (range.size() < 2) {
            throw new IllegalArgumentException("between 操作符需要两个边界值");
        }
        return compare(actual, range.get(0)) >= 0 && compare(actual, range.get(1)) <= 0;
    }

    /**
     * 比较两个值，优先按数字、日期处理，最后按字符串处理。
     *
     * @param actual 实际值
     * @param expected 期望值
     * @return 比较结果
     */
    private int compare(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return 0;
        }
        if (actual == null) {
            return -1;
        }
        if (expected == null) {
            return 1;
        }
        BigDecimal actualNumber = number(actual);
        BigDecimal expectedNumber = number(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber);
        }
        LocalDateTime actualDate = dateTime(actual);
        LocalDateTime expectedDate = dateTime(expected);
        if (actualDate != null && expectedDate != null) {
            return actualDate.compareTo(expectedDate);
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    /**
     * 尝试将值转换为数字。
     *
     * @param value 原始值
     * @return 数字值，无法转换时返回 null
     */
    private BigDecimal number(Object value) {
        try {
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 尝试将值转换为日期时间。
     *
     * @param value 原始值
     * @return 日期时间，无法转换时返回 null
     */
    private LocalDateTime dateTime(Object value) {
        try {
            String text = String.valueOf(value);
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }
}
