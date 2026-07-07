package com.youlai.decision.engine;

import com.youlai.decision.model.DecisionArtifact;
import com.youlai.decision.service.JsonService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行外部数据源和模型节点，支持 HTTP 调用、超时、降级和本地短缓存。
 */
@Component
public class ExternalNodeExecutor {

    private final JsonService jsonService;
    private final HttpClient httpClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 创建外部节点执行器。
     *
     * @param jsonService JSON 服务
     */
    public ExternalNodeExecutor(JsonService jsonService) {
        this.jsonService = jsonService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * 执行数据源节点。
     *
     * @param dataSource 数据源资产
     * @param facts 当前事实
     * @param node 节点配置
     * @return 执行结果
     */
    public ExternalNodeResult executeDataSource(DecisionArtifact dataSource, Map<String, Object> facts, Map<String, Object> node) {
        Map<String, Object> content = merge(jsonService.readMap(dataSource.getContentJson()), node);
        String type = Objects.toString(content.getOrDefault("type", "HTTP")).toUpperCase(Locale.ROOT);
        if ("REQUEST".equals(type)) {
            return new ExternalNodeResult(selectFields(content, facts), false, "REQUEST");
        }
        if ("STATIC".equals(type)) {
            return new ExternalNodeResult(castMap(content.getOrDefault("output", content.get("staticOutput"))), false, "STATIC");
        }
        if (endpoint(content).isBlank()) {
            return fallback(content, "数据源未配置 endpoint");
        }
        return executeHttp(dataSource.getCode(), content, facts, "GET");
    }

    /**
     * 执行模型节点。
     *
     * @param model 模型资产
     * @param facts 当前事实
     * @param node 节点配置
     * @return 执行结果
     */
    public ExternalNodeResult executeModel(DecisionArtifact model, Map<String, Object> facts, Map<String, Object> node) {
        Map<String, Object> content = merge(jsonService.readMap(model.getContentJson()), node);
        if (endpoint(content).isBlank()) {
            Map<String, Object> fallbackOutput = fallbackOutput(content);
            if (fallbackOutput.isEmpty() && content.containsKey("fallbackScore")) {
                fallbackOutput = Map.of("score", content.get("fallbackScore"));
            }
            return new ExternalNodeResult(fallbackOutput, true, "模型未配置 endpoint");
        }
        return executeHttp(model.getCode(), content, facts, "POST");
    }

    private ExternalNodeResult executeHttp(String code, Map<String, Object> content, Map<String, Object> facts, String defaultMethod) {
        String cacheKey = null;
        long ttlMs = longValue(content.getOrDefault("cacheTtlMs", 0));
        if (ttlMs > 0) {
            cacheKey = code + ":" + endpoint(content) + ":" + jsonService.write(requestPayload(content, facts));
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return new ExternalNodeResult(cached.value, false, "CACHE");
            }
        }
        try {
            HttpRequest request = buildRequest(content, facts, defaultMethod);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback(content, "HTTP " + response.statusCode());
            }
            Map<String, Object> body = jsonService.readMap(response.body());
            if (cacheKey != null) {
                cache.put(cacheKey, new CacheEntry(body, System.currentTimeMillis() + ttlMs));
            }
            return new ExternalNodeResult(body, false, "HTTP");
        } catch (Exception ex) {
            return fallback(content, ex.getMessage());
        }
    }

    private HttpRequest buildRequest(Map<String, Object> content, Map<String, Object> facts, String defaultMethod) {
        String method = Objects.toString(content.getOrDefault("method", defaultMethod)).toUpperCase(Locale.ROOT);
        int timeoutMs = intValue(content.getOrDefault("timeoutMs", 1000));
        Map<String, Object> payload = requestPayload(content, facts);
        URI uri = URI.create("GET".equals(method) ? appendQuery(endpoint(content), payload) : endpoint(content));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 50)))
                .header("Content-Type", "application/json");
        castMap(content.get("headers")).forEach((key, value) -> builder.header(key, String.valueOf(value)));
        if ("GET".equals(method)) {
            return builder.GET().build();
        }
        return builder.method(method, HttpRequest.BodyPublishers.ofString(jsonService.write(payload))).build();
    }

    private Map<String, Object> requestPayload(Map<String, Object> content, Map<String, Object> facts) {
        if (content.get("payload") instanceof Map<?, ?> payload) {
            return castMap(payload);
        }
        if (content.get("requestFields") instanceof List<?> fields) {
            Map<String, Object> payload = new LinkedHashMap<>();
            for (Object field : fields) {
                String key = String.valueOf(field);
                if (facts.containsKey(key)) {
                    payload.put(key, facts.get(key));
                }
            }
            return payload;
        }
        return new LinkedHashMap<>(facts);
    }

    private Map<String, Object> selectFields(Map<String, Object> content, Map<String, Object> facts) {
        Object fieldsValue = content.get("fields");
        if (!(fieldsValue instanceof List<?> fields)) {
            return new LinkedHashMap<>(facts);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Object field : fields) {
            String key = String.valueOf(field);
            if (facts.containsKey(key)) {
                output.put(key, facts.get(key));
            }
        }
        return output;
    }

    private String appendQuery(String url, Map<String, Object> query) {
        if (query.isEmpty()) {
            return url;
        }
        StringJoiner joiner = new StringJoiner("&");
        query.forEach((key, value) -> joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
        return url + (url.contains("?") ? "&" : "?") + joiner;
    }

    private ExternalNodeResult fallback(Map<String, Object> content, String reason) {
        String fallback = Objects.toString(content.getOrDefault("fallback", "OUTPUT")).toUpperCase(Locale.ROOT);
        if ("ERROR".equals(fallback)) {
            throw new IllegalStateException("外部节点执行失败: " + reason);
        }
        if ("IGNORE".equals(fallback)) {
            return new ExternalNodeResult(Map.of(), true, reason);
        }
        return new ExternalNodeResult(fallbackOutput(content), true, reason);
    }

    private Map<String, Object> fallbackOutput(Map<String, Object> content) {
        Object output = content.getOrDefault("fallbackOutput", content.get("mockOutput"));
        return castMap(output);
    }

    private String endpoint(Map<String, Object> content) {
        return Objects.toString(content.getOrDefault("endpoint", content.getOrDefault("url", "")), "");
    }

    private Map<String, Object> merge(Map<String, Object> content, Map<String, Object> node) {
        Map<String, Object> merged = new LinkedHashMap<>(content);
        node.forEach((key, value) -> {
            if (!Set.of("id", "type", "code", "label", "sort", "enabled", "x", "y").contains(key)) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 1000;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (!StringUtils.hasText(String.valueOf(value))) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private record CacheEntry(Map<String, Object> value, long expiresAt) {
    }

    /**
     * 外部节点执行结果。
     *
     * @param outputs 输出内容
     * @param fallback 是否使用降级结果
     * @param source 结果来源或失败原因
     */
    public record ExternalNodeResult(Map<String, Object> outputs, boolean fallback, String source) {
    }
}
