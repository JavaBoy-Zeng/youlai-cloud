package com.youlai.flowable.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.common.result.Result;
import com.youlai.flowable.mapper.AppBuilderApiLogMapper;
import com.youlai.flowable.mapper.AppBuilderApiMapper;
import com.youlai.flowable.model.entity.AppBuilderApi;
import com.youlai.flowable.model.entity.AppBuilderApiLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "应用搭建-Webhook回调接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app-builder/webhooks")
public class AppBuilderWebhookController {

    private final AppBuilderApiMapper apiMapper;
    private final AppBuilderApiLogMapper apiLogMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "接收外部 Webhook 回调")
    @PostMapping("/{apiCode}")
    public Result<Map<String, Object>> receive(@PathVariable String apiCode,
                                               @RequestBody(required = false) Object payload,
                                               HttpServletRequest request) {
        long start = System.currentTimeMillis();
        AppBuilderApiLog log = new AppBuilderApiLog();
        try {
            AppBuilderApi api = apiMapper.selectOne(new LambdaQueryWrapper<AppBuilderApi>()
                    .eq(AppBuilderApi::getApiCode, apiCode)
                    .eq(AppBuilderApi::getStatus, "ENABLED")
                    .last("LIMIT 1"));
            if (api == null) {
                log.setStatusCode(404);
                throw new IllegalArgumentException("Webhook 配置不存在或未启用：" + apiCode);
            }
            log.setApiId(api.getId());
            verifySecret(api, request);
            Map<String, Object> requestData = new LinkedHashMap<>();
            requestData.put("apiCode", apiCode);
            requestData.put("headers", requestHeaders(request));
            requestData.put("payload", payload == null ? Map.of() : payload);
            log.setRequestJson(toJson(requestData));
            log.setStatusCode(200);
            log.setResponseText("{\"received\":true}");
            log.setSuccess(1);
            return Result.success(Map.of(
                    "apiCode", apiCode,
                    "received", true,
                    "apiId", api.getId()
            ));
        } catch (Exception e) {
            log.setSuccess(0);
            log.setErrorMsg(e.getMessage());
            if (log.getStatusCode() == null) {
                log.setStatusCode(401);
            }
            throw e;
        } finally {
            log.setDurationMs(System.currentTimeMillis() - start);
            apiLogMapper.insert(log);
        }
    }

    private void verifySecret(AppBuilderApi api, HttpServletRequest request) {
        Map<String, Object> config = parseMap(api.getHeadersJson());
        Object expected = config.getOrDefault("webhookSecret", config.get("X-Webhook-Secret"));
        if (expected == null || StrUtil.isBlank(String.valueOf(expected))) {
            return;
        }
        String actual = StrUtil.blankToDefault(request.getHeader("X-Webhook-Secret"), request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!String.valueOf(expected).equals(actual)) {
            throw new IllegalArgumentException("Webhook 密钥不正确");
        }
    }

    private Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private Map<String, Object> parseMap(String json) {
        if (StrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Webhook 头部配置JSON格式不正确", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
