package com.youlai.flowable.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.flowable.config.AppBuilderApiSecurityProperties;
import com.youlai.flowable.mapper.AppBuilderApiLogMapper;
import com.youlai.flowable.model.entity.AppBuilderApi;
import com.youlai.flowable.model.entity.AppBuilderApiLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppBuilderApiInvokeService {

    private final AppBuilderApiLogMapper apiLogMapper;
    private final AppBuilderApiSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public AppBuilderApiLog invoke(AppBuilderApi api, Map<String, Object> payload) {
        AppBuilderApiLog log = new AppBuilderApiLog();
        log.setApiId(api.getId());
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> requestPayload = payload == null ? Map.of() : payload;
            HttpMethod method = HttpMethod.valueOf(StrUtil.blankToDefault(api.getMethod(), "GET").toUpperCase());
            Map<String, String> queryParams = renderMap(parseMap(api.getParamsJson()), requestPayload);
            if (queryParams.isEmpty() && method == HttpMethod.GET) {
                requestPayload.forEach((key, value) -> queryParams.put(key, value == null ? "" : String.valueOf(value)));
            }
            String url = buildUrl(api.getUrl(), queryParams);
            assertUrlAllowed(url);

            HttpHeaders headers = new HttpHeaders();
            parseMap(api.getHeadersJson()).forEach((key, value) -> headers.set(key, renderTemplate(String.valueOf(value), requestPayload)));
            if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            Object body = method == HttpMethod.GET ? null : buildRequestBody(api.getBodyTemplate(), requestPayload);
            int retryTimes = retryTimes(api);
            log.setRequestJson(toJson(Map.of(
                    "method", method.name(),
                    "url", url,
                    "headers", maskSensitive(headers.toSingleValueMap()),
                    "body", body == null ? "" : maskSensitive(body),
                    "retryTimes", retryTimes,
                    "timeoutMs", timeoutMs(api)
            )));
            ResponseEntity<String> response = exchangeWithRetry(api, url, method, new HttpEntity<>(body, headers), retryTimes);
            log.setStatusCode(response.getStatusCode().value());
            log.setResponseText(response.getBody());
            log.setSuccess(1);
        } catch (Exception e) {
            log.setSuccess(0);
            log.setErrorMsg(e.getMessage());
        } finally {
            log.setDurationMs(System.currentTimeMillis() - start);
            apiLogMapper.insert(log);
        }
        return log;
    }

    public void assertInvokeSuccess(AppBuilderApi api, Map<String, Object> payload) {
        AppBuilderApiLog log = invoke(api, payload);
        if (!Integer.valueOf(1).equals(log.getSuccess())) {
            throw new IllegalStateException(StrUtil.blankToDefault(log.getErrorMsg(), "API调用失败"));
        }
    }

    private ResponseEntity<String> exchangeWithRetry(AppBuilderApi api, String url, HttpMethod method, HttpEntity<?> entity, int retryTimes) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            try {
                return restTemplate(api).exchange(url, method, entity, String.class);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last == null ? new IllegalStateException("API调用失败") : last;
    }

    private RestTemplate restTemplate(AppBuilderApi api) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(timeoutMs(api));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void assertUrlAllowed(String url) {
        URI uri = URI.create(url);
        String scheme = StrUtil.blankToDefault(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        String host = StrUtil.blankToDefault(uri.getHost(), "").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(scheme) || StrUtil.isBlank(host)) {
            throw new IllegalArgumentException("API地址必须是完整URL");
        }
        if (!securityProperties.getAllowedSchemes().stream().map(item -> item.toLowerCase(Locale.ROOT)).toList().contains(scheme)) {
            throw new IllegalArgumentException("API地址协议不允许：" + scheme);
        }
        if (!isHostAllowed(host)) {
            throw new IllegalArgumentException("API地址域名不在白名单：" + host);
        }
        if (securityProperties.isBlockPrivateAddress()) {
            assertPublicAddress(host);
        }
    }

    private boolean isHostAllowed(String host) {
        if (securityProperties.getAllowedHosts().isEmpty()) {
            return true;
        }
        for (String pattern : securityProperties.getAllowedHosts()) {
            String normalized = StrUtil.blankToDefault(pattern, "").toLowerCase(Locale.ROOT).trim();
            if (StrUtil.isBlank(normalized)) {
                continue;
            }
            if (normalized.startsWith("*.")) {
                String suffix = normalized.substring(1);
                if (host.endsWith(suffix) && host.length() > suffix.length()) {
                    return true;
                }
            } else if (host.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private void assertPublicAddress(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("API地址指向内网或本机地址：" + host);
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("API地址域名无法解析：" + host, e);
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private String buildUrl(String url, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        queryParams.forEach(builder::queryParam);
        return builder.build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private Object buildRequestBody(String bodyTemplate, Map<String, Object> payload) {
        if (StrUtil.isBlank(bodyTemplate)) {
            return payload;
        }
        String rendered = renderTemplate(bodyTemplate, payload);
        try {
            return objectMapper.readValue(rendered, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ignored) {
            return rendered;
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (StrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("API配置JSON格式不正确", e);
        }
    }

    private Map<String, String> renderMap(Map<String, Object> config, Map<String, Object> payload) {
        Map<String, String> result = new LinkedHashMap<>();
        config.forEach((key, value) -> result.put(key, renderTemplate(String.valueOf(value), payload)));
        return result;
    }

    private String renderTemplate(String template, Map<String, Object> payload) {
        String result = StrUtil.blankToDefault(template, "");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private int retryTimes(AppBuilderApi api) {
        return Math.max(0, api.getRetryTimes() == null ? 0 : api.getRetryTimes());
    }

    private int timeoutMs(AppBuilderApi api) {
        return Math.max(1000, api.getTimeoutMs() == null ? 10000 : api.getTimeoutMs());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Object maskSensitive(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                masked.put(name, isSensitiveKey(name) ? "******" : maskSensitive(item));
            });
            return masked;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::maskSensitive).toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization")
                || lower.contains("cookie");
    }
}
