package com.youlai.decision.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 条件解释器测试。
 */
class ConditionEvaluatorTest {

    /** 条件解释器。 */
    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    /**
     * 验证嵌套条件组、区间和集合操作符可以正确匹配。
     */
    @Test
    void shouldMatchNestedConditions() {
        Map<String, Object> condition = Map.of(
                "logic", "AND",
                "items", List.of(
                        Map.of("field", "order.amount", "operator", "between", "value", List.of(1000, 20000)),
                        Map.of(
                                "logic", "OR",
                                "items", List.of(
                                        Map.of("field", "city", "operator", "in", "value", List.of("重庆", "深圳")),
                                        Map.of("field", "vip", "operator", "=", "value", true)
                                )
                        )
                )
        );
        Map<String, Object> facts = Map.of(
                "order", Map.of("amount", 12000),
                "city", "重庆",
                "vip", false
        );

        assertThat(evaluator.matches(condition, facts)).isTrue();
    }
}
