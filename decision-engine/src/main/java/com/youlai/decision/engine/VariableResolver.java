package com.youlai.decision.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.DecisionHitDetailLogMapper;
import com.youlai.decision.mapper.DecisionVariableMapper;
import com.youlai.decision.model.DecisionHitDetailLog;
import com.youlai.decision.model.DecisionVariable;
import com.youlai.decision.service.JsonService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VariableResolver {

    private static final Pattern HARE_PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^:}]+):([^}]+)}");

    private final DecisionVariableMapper variableMapper;
    private final DecisionHitDetailLogMapper hitDetailLogMapper;
    private final HareGatewayClient hareGatewayClient;
    private final JsonService jsonService;

    public VariableResolver(
            DecisionVariableMapper variableMapper,
            DecisionHitDetailLogMapper hitDetailLogMapper,
            HareGatewayClient hareGatewayClient,
            JsonService jsonService
    ) {
        this.variableMapper = variableMapper;
        this.hitDetailLogMapper = hitDetailLogMapper;
        this.hareGatewayClient = hareGatewayClient;
        this.jsonService = jsonService;
    }

    public ResolveResult resolve(String traceId, String sceneCode, Map<String, Object> params, String expression) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (params != null) {
            facts.putAll(params);
        }
        for (DecisionVariable variable : variables(sceneCode)) {
            String source = Objects.toString(variable.getSource(), "REQUEST").toUpperCase(Locale.ROOT);
            Map<String, Object> config = jsonService.readMap(variable.getSourceConfigJson());
            Object value = switch (source) {
                case "CONSTANT" -> config.getOrDefault("value", readDefault(variable));
                case "LOCAL" -> config.getOrDefault("value", readDefault(variable));
                case "HARE" -> resolveHare(traceId, sceneCode, variable.getCode(), config, facts);
                case "MODEL", "DATA_SOURCE" -> config.getOrDefault("mockOutput", readDefault(variable));
                default -> facts.getOrDefault(variable.getCode(), readDefault(variable));
            };
            if (value != null && !facts.containsKey(variable.getCode())) {
                facts.put(variable.getCode(), value);
            }
        }
        String normalized = resolvePlaceholders(traceId, sceneCode, expression, facts);
        return new ResolveResult(facts, normalized);
    }

    private List<DecisionVariable> variables(String sceneCode) {
        return variableMapper.selectList(Wrappers.lambdaQuery(DecisionVariable.class)
                .and(wrapper -> wrapper.eq(DecisionVariable::getSceneCode, sceneCode).or().isNull(DecisionVariable::getSceneCode)));
    }

    private Object readDefault(DecisionVariable variable) {
        if (!StringUtils.hasText(variable.getDefaultValueJson())) {
            return null;
        }
        try {
            return jsonService.readMap("{\"value\":" + variable.getDefaultValueJson() + "}").get("value");
        } catch (Exception ignored) {
            return variable.getDefaultValueJson();
        }
    }

    private Object resolveHare(String traceId, String sceneCode, String variableCode, Map<String, Object> config, Map<String, Object> facts) {
        String shortName = Objects.toString(config.get("shortName"), "");
        String apiName = Objects.toString(config.get("apiName"), "");
        String absoluteKey = Objects.toString(config.get("absoluteKey"), "");
        long started = System.currentTimeMillis();
        Map<String, Object> result = hareGatewayClient.fetch(shortName, apiName, facts);
        Object value = jsonPath(result, absoluteKey);
        saveExternalTrace(traceId, sceneCode, variableCode, "${" + shortName + ":" + apiName + ":" + absoluteKey + "}", result, System.currentTimeMillis() - started);
        return value;
    }

    private String resolvePlaceholders(String traceId, String sceneCode, String expression, Map<String, Object> facts) {
        if (!StringUtils.hasText(expression)) {
            return expression;
        }
        Matcher matcher = HARE_PLACEHOLDER.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        int index = 1;
        while (matcher.find()) {
            String alias = sanitize(matcher.group(1)) + "Value" + index++;
            long started = System.currentTimeMillis();
            Map<String, Object> result = hareGatewayClient.fetch(matcher.group(1), matcher.group(2), facts);
            Object value = jsonPath(result, matcher.group(3));
            facts.put(alias, value);
            saveExternalTrace(traceId, sceneCode, alias, matcher.group(), result, System.currentTimeMillis() - started);
            matcher.appendReplacement(buffer, alias);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void saveExternalTrace(String traceId, String sceneCode, String code, String expression, Map<String, Object> detail, long elapsedMs) {
        if (!StringUtils.hasText(traceId)) {
            return;
        }
        DecisionHitDetailLog log = new DecisionHitDetailLog();
        log.setTraceId(traceId);
        log.setSceneCode(sceneCode);
        log.setTargetType("VARIABLE");
        log.setTargetCode(code);
        log.setDetailType("EXTERNAL");
        log.setExpression(expression);
        log.setMatched(true);
        log.setDetailJson(jsonService.write(detail));
        log.setElapsedMs(elapsedMs);
        hitDetailLogMapper.insert(log);
    }

    @SuppressWarnings("unchecked")
    private Object jsonPath(Map<String, Object> json, String path) {
        if (!StringUtils.hasText(path)) {
            return json;
        }
        Object current = json;
        for (String part : path.replaceFirst("^\\$\\.", "").split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
                continue;
            }
            return null;
        }
        return current;
    }

    private String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_]", "");
        return StringUtils.hasText(sanitized) ? sanitized : "hare";
    }

    public record ResolveResult(Map<String, Object> facts, String expression) {
    }
}
