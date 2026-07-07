package com.youlai.decision.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.decision.model.DecisionPublishRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Component
public class DefaultFlowablePublishClient implements FlowablePublishClient {

    private final DecisionEngineProperties properties;
    private final ObjectMapper objectMapper;

    public DefaultFlowablePublishClient(DecisionEngineProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String submit(DecisionPublishRequest request) {
        Map<String, Object> body = Map.of(
                "modelId", request.getWorkflowModelId(),
                "businessKey", request.getWorkflowBusinessKey(),
                "formDataJson", write(Map.of(
                        "requestId", request.getId(),
                        "targetType", request.getTargetType(),
                        "targetId", request.getTargetId(),
                        "code", request.getCode(),
                        "versionNo", request.getVersionNo(),
                        "remark", request.getRemark()
                )),
                "variables", Map.of(
                        "requestId", request.getId(),
                        "targetType", request.getTargetType(),
                        "targetCode", request.getCode()
                )
        );
        Map<String, Object> response = post("/api/v1/workflow/instances/start", body);
        Map<String, Object> data = data(response);
        String processInstanceId = text(data.get("processInstanceId"));
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalStateException("Flowable 未返回 processInstanceId");
        }
        return processInstanceId;
    }

    @Override
    public String status(String processInstanceId) {
        Map<String, Object> response = get("/api/v1/workflow/instances/" + processInstanceId);
        return text(data(response).getOrDefault("status", "RUNNING"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        return client().post()
                .uri(path)
                .headers(this::copyAuthorization)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        return client().get()
                .uri(path)
                .headers(this::copyAuthorization)
                .retrieve()
                .body(Map.class);
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(properties.getPublish().getFlowableBaseUrl())
                .build();
    }

    private void copyAuthorization(HttpHeaders headers) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization)) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Flowable 表单数据序列化失败", ex);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
