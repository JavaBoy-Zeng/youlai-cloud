package com.youlai.flowable.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.flowable.mapper.AppBuilderOperationLogMapper;
import com.youlai.flowable.model.entity.AppBuilderOperationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppBuilderOperationLogService {

    private final AppBuilderOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public void record(Long appId, String moduleName, String operationType, Object content, String remark) {
        try {
            AppBuilderOperationLog log = new AppBuilderOperationLog();
            log.setAppId(appId);
            log.setModuleName(moduleName);
            log.setOperationType(operationType);
            log.setOperator(currentUsername());
            log.setContentJson(toJson(content));
            log.setSuccess(1);
            log.setRemark(remark);
            operationLogMapper.insert(log);
        } catch (Exception ignored) {
            // 操作日志不能影响主业务流程。
        }
    }

    private String currentUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    private String toJson(Object content) throws JsonProcessingException {
        if (content == null) {
            return "{}";
        }
        if (content instanceof String text) {
            return text;
        }
        return objectMapper.writeValueAsString(content);
    }
}
