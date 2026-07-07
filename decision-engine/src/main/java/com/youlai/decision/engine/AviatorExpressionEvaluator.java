package com.youlai.decision.engine;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.youlai.decision.model.DomainApiModels.ConditionTrace;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AviatorExpressionEvaluator {

    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerFunctions() {
        AviatorEvaluator.addFunction(new IntersectFunction());
        AviatorEvaluator.addFunction(new ContainsAnyFunction());
        AviatorEvaluator.addFunction(new InListFunction());
        AviatorEvaluator.addFunction(new BetweenNumFunction());
        AviatorEvaluator.addFunction(new DaysBetweenFunction());
        AviatorEvaluator.addFunction(new JsonGetFunction());
    }

    public EvaluationResult evaluate(String targetType, String targetCode, String expression, Map<String, Object> facts) {
        long started = System.currentTimeMillis();
        if (!StringUtils.hasText(expression)) {
            return new EvaluationResult(true, new ConditionTrace(targetType, targetCode, expression, true, facts, 0L, null));
        }
        try {
            Expression compiled = cache.computeIfAbsent(expression, item -> AviatorEvaluator.compile(item, true));
            Object value = compiled.execute(new LinkedHashMap<>(facts));
            boolean matched = Boolean.TRUE.equals(value);
            return new EvaluationResult(matched, new ConditionTrace(
                    targetType,
                    targetCode,
                    expression,
                    matched,
                    new LinkedHashMap<>(facts),
                    System.currentTimeMillis() - started,
                    null
            ));
        } catch (RuntimeException ex) {
            return new EvaluationResult(false, new ConditionTrace(
                    targetType,
                    targetCode,
                    expression,
                    false,
                    new LinkedHashMap<>(facts),
                    System.currentTimeMillis() - started,
                    ex.getMessage()
            ));
        }
    }

    public record EvaluationResult(boolean matched, ConditionTrace trace) {
    }

    private abstract static class BooleanFunction extends AbstractFunction {
        protected AviatorBoolean result(boolean value) {
            return AviatorBoolean.valueOf(value);
        }

        protected Object value(Map<String, Object> env, AviatorObject object) {
            return object == null ? null : object.getValue(env);
        }

        protected Set<String> set(Object value) {
            if (value == null) {
                return Set.of();
            }
            if (value instanceof Collection<?> collection) {
                Set<String> values = new LinkedHashSet<>();
                collection.forEach(item -> values.add(String.valueOf(item)));
                return values;
            }
            String text = String.valueOf(value);
            if (!StringUtils.hasText(text)) {
                return Set.of();
            }
            return new LinkedHashSet<>(Arrays.stream(text.split("[,;]")).map(String::trim).filter(StringUtils::hasText).toList());
        }
    }

    private static class IntersectFunction extends BooleanFunction {
        @Override
        public String getName() {
            return "intersect";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
            Set<String> leftSet = set(value(env, left));
            Set<String> rightSet = set(value(env, right));
            leftSet.retainAll(rightSet);
            return result(!leftSet.isEmpty());
        }
    }

    private static class ContainsAnyFunction extends BooleanFunction {
        @Override
        public String getName() {
            return "containsAny";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
            Set<String> leftSet = set(value(env, left));
            for (String item : set(value(env, right))) {
                if (leftSet.contains(item)) {
                    return result(true);
                }
            }
            return result(false);
        }
    }

    private static class InListFunction extends BooleanFunction {
        @Override
        public String getName() {
            return "inList";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value, AviatorObject list) {
            return result(set(value(env, list)).contains(String.valueOf(value(env, value))));
        }
    }

    private static class BetweenNumFunction extends BooleanFunction {
        @Override
        public String getName() {
            return "betweenNum";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value, AviatorObject min, AviatorObject max) {
            BigDecimal current = number(value(env, value));
            BigDecimal low = number(value(env, min));
            BigDecimal high = number(value(env, max));
            return result(current != null && low != null && high != null
                    && current.compareTo(low) >= 0
                    && current.compareTo(high) <= 0);
        }

        private BigDecimal number(Object value) {
            try {
                return value == null ? null : new BigDecimal(String.valueOf(value));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static class DaysBetweenFunction extends BooleanFunction {
        @Override
        public String getName() {
            return "daysBetween";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject start, AviatorObject end) {
            LocalDate startDate = date(value(env, start));
            LocalDate endDate = date(value(env, end));
            if (startDate == null || endDate == null) {
                return AviatorLong.valueOf(0);
            }
            return AviatorLong.valueOf(ChronoUnit.DAYS.between(startDate, endDate));
        }

        private LocalDate date(Object value) {
            try {
                return LocalDate.parse(String.valueOf(value).substring(0, 10));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static class JsonGetFunction extends BooleanFunction {
        @Override
        public String getName() {
            return "jsonGet";
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject object, AviatorObject path) {
            Object current = value(env, object);
            String[] parts = String.valueOf(value(env, path)).replaceFirst("^\\$\\.", "").split("\\.");
            for (String part : parts) {
                if (current instanceof Map<?, ?> map) {
                    current = map.get(part);
                    continue;
                }
                return com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType.valueOf(null);
            }
            return com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType.valueOf(current);
        }
    }
}
