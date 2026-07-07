package com.youlai.flowable.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.flowable.mapper.AppBuilderApiMapper;
import com.youlai.flowable.mapper.AppBuilderAutomationMapper;
import com.youlai.flowable.mapper.AppBuilderDataMapper;
import com.youlai.flowable.mapper.AppBuilderNotificationMapper;
import com.youlai.flowable.mapper.AppBuilderOperationLogMapper;
import com.youlai.flowable.model.entity.AppBuilderApi;
import com.youlai.flowable.model.entity.AppBuilderAutomation;
import com.youlai.flowable.model.entity.AppBuilderData;
import com.youlai.flowable.model.entity.AppBuilderNotification;
import com.youlai.flowable.model.entity.AppBuilderOperationLog;
import com.youlai.flowable.model.entity.WfInstance;
import com.youlai.flowable.model.form.StartProcessForm;
import com.youlai.flowable.service.WfRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AppBuilderAutomationService {

    private final AppBuilderAutomationMapper automationMapper;
    private final AppBuilderApiMapper apiMapper;
    private final AppBuilderDataMapper dataMapper;
    private final AppBuilderNotificationMapper notificationMapper;
    private final AppBuilderOperationLogMapper operationLogMapper;
    private final AppBuilderApiInvokeService apiInvokeService;
    private final ObjectMapper objectMapper;
    @Lazy
    private final WfRuntimeService wfRuntimeService;
    private final Map<Long, Long> scheduleLastRuns = new ConcurrentHashMap<>();

    public void executeDataTrigger(String triggerType, AppBuilderData entity, Map<String, Object> data) {
        List<AppBuilderAutomation> rules = automationMapper.selectList(new LambdaQueryWrapper<AppBuilderAutomation>()
                .eq(AppBuilderAutomation::getAppId, entity.getAppId())
                .eq(AppBuilderAutomation::getModelId, entity.getModelId())
                .eq(AppBuilderAutomation::getTriggerType, triggerType)
                .eq(AppBuilderAutomation::getStatus, "ENABLED"));
        for (AppBuilderAutomation rule : rules) {
            executeRule(rule, entity, data);
        }
    }

    public void executeProcessTrigger(WfInstance instance) {
        AppBuilderData entity = dataMapper.selectOne(new LambdaQueryWrapper<AppBuilderData>()
                .eq(AppBuilderData::getBusinessKey, instance.getBusinessKey())
                .last("LIMIT 1"));
        if (entity == null) {
            return;
        }
        Map<String, Object> data = parseMap(entity.getDataJson());
        data.put("_processInstanceId", instance.getProcessInstanceId());
        data.put("_workflowStatus", instance.getStatus());
        List<AppBuilderAutomation> rules = automationMapper.selectList(new LambdaQueryWrapper<AppBuilderAutomation>()
                .eq(AppBuilderAutomation::getAppId, entity.getAppId())
                .eq(AppBuilderAutomation::getModelId, entity.getModelId())
                .eq(AppBuilderAutomation::getTriggerType, "PROCESS_DONE")
                .eq(AppBuilderAutomation::getStatus, "ENABLED"));
        for (AppBuilderAutomation rule : rules) {
            executeRule(rule, entity, data);
        }
    }

    public void executeScheduleTriggers() {
        long now = System.currentTimeMillis();
        List<AppBuilderAutomation> rules = automationMapper.selectList(new LambdaQueryWrapper<AppBuilderAutomation>()
                .eq(AppBuilderAutomation::getTriggerType, "SCHEDULE")
                .eq(AppBuilderAutomation::getStatus, "ENABLED"));
        for (AppBuilderAutomation rule : rules) {
            Map<String, Object> triggerConfig = parseMap(rule.getTriggerConfigJson());
            long intervalMs = Math.max(60_000L, toLong(triggerConfig.getOrDefault("intervalSeconds", 60)) * 1000L);
            long lastRun = scheduleLastRuns.getOrDefault(rule.getId(), 0L);
            if (now - lastRun < intervalMs) {
                continue;
            }
            scheduleLastRuns.put(rule.getId(), now);
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("_triggerType", "SCHEDULE");
            context.put("_automationId", rule.getId());
            context.put("_scheduledAt", now);
            executeRule(rule, null, context);
        }
    }

    private void executeRule(AppBuilderAutomation rule, AppBuilderData entity, Map<String, Object> data) {
        AppBuilderOperationLog operationLog = new AppBuilderOperationLog();
        operationLog.setAppId(rule.getAppId());
        operationLog.setModuleName("AUTOMATION");
        operationLog.setOperationType(rule.getTriggerType() + ":" + rule.getActionType());
        operationLog.setOperator("system");
        Map<String, Object> logContent = new LinkedHashMap<>();
        logContent.put("ruleId", rule.getId());
        logContent.put("ruleName", rule.getRuleName());
        if (entity != null) {
            logContent.put("businessKey", entity.getBusinessKey());
        }
        operationLog.setContentJson(toJson(logContent));
        try {
            Map<String, Object> context = new LinkedHashMap<>(data == null ? Map.of() : data);
            if (entity != null) {
                context.put("_id", entity.getId());
                context.put("_businessKey", entity.getBusinessKey());
                context.put("_status", entity.getStatus());
            }
            Map<String, Object> actionConfig = parseMap(rule.getActionConfigJson());
            if ("CALL_API".equals(rule.getActionType())) {
                Long apiId = toLong(actionConfig.get("apiId"));
                AppBuilderApi api = apiMapper.selectById(apiId);
                if (api == null) {
                    throw new IllegalArgumentException("自动化绑定的API不存在：" + apiId);
                }
                if (!"ENABLED".equals(api.getStatus())) {
                    throw new IllegalArgumentException("自动化绑定的API未启用：" + apiId);
                }
                apiInvokeService.assertInvokeSuccess(api, renderPayload(actionConfig.get("payload"), context));
            } else if ("WEBHOOK".equals(rule.getActionType())) {
                AppBuilderApi api = new AppBuilderApi();
                Object method = actionConfig.get("method");
                Object url = actionConfig.get("url");
                api.setMethod(StrUtil.blankToDefault(method == null ? null : String.valueOf(method), "POST"));
                api.setUrl(url == null ? null : String.valueOf(url));
                api.setHeadersJson(toJson(actionConfig.getOrDefault("headers", Map.of())));
                api.setBodyTemplate(toJson(actionConfig.getOrDefault("body", context)));
                apiInvokeService.assertInvokeSuccess(api, context);
            } else if ("START_PROCESS".equals(rule.getActionType())) {
                StartProcessForm form = new StartProcessForm();
                form.setModelId(toLong(actionConfig.get("workflowModelId")));
                form.setProcessDefinitionId((String) actionConfig.get("processDefinitionId"));
                String defaultBusinessKey = entity == null ? "schedule-{{_automationId}}-{{_scheduledAt}}" : "{{_businessKey}}-auto";
                form.setBusinessKey(renderTemplate(String.valueOf(actionConfig.getOrDefault("businessKey", defaultBusinessKey)), context));
                form.setFormDataJson(toJson(renderPayload(actionConfig.get("formData"), context)));
                form.getVariables().putAll(renderPayload(actionConfig.get("variables"), context));
                if (entity != null) {
                    form.getVariables().put("sourceBusinessKey", entity.getBusinessKey());
                }
                form.getVariables().put("sourceAutomationId", rule.getId());
                wfRuntimeService.startProcess(form);
            } else if ("SEND_MESSAGE".equals(rule.getActionType())) {
                AppBuilderNotification notification = new AppBuilderNotification();
                notification.setAppId(rule.getAppId());
                notification.setAutomationId(rule.getId());
                notification.setReceiverUsername(renderTemplate(String.valueOf(actionConfig.getOrDefault("receiverUsername", "")), context));
                notification.setTitle(renderTemplate(String.valueOf(actionConfig.getOrDefault("title", rule.getRuleName())), context));
                notification.setContent(renderTemplate(String.valueOf(actionConfig.getOrDefault("content", "")), context));
                notification.setStatus("UNREAD");
                notificationMapper.insert(notification);
            }
            operationLog.setSuccess(1);
            operationLog.setRemark("执行成功");
        } catch (Exception e) {
            operationLog.setSuccess(0);
            operationLog.setRemark(e.getMessage());
        }
        operationLogMapper.insert(operationLog);
    }

    private Map<String, Object> renderPayload(Object configPayload, Map<String, Object> context) {
        if (configPayload == null) {
            return context;
        }
        Map<String, Object> payloadConfig = objectMapper.convertValue(configPayload, new TypeReference<>() {
        });
        Map<String, Object> result = new LinkedHashMap<>();
        payloadConfig.forEach((key, value) -> result.put(key, renderTemplate(String.valueOf(value), context)));
        return result;
    }

    private Map<String, Object> parseMap(String json) {
        if (StrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("自动化配置JSON格式不正确", e);
        }
    }

    private String renderTemplate(String template, Map<String, Object> payload) {
        String result = StrUtil.blankToDefault(template, "");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return StrUtil.isBlankIfStr(value) ? null : Long.valueOf(String.valueOf(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

}
