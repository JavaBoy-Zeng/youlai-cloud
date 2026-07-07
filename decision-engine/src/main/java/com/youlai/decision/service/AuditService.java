package com.youlai.decision.service;

import com.youlai.decision.mapper.AuditLogMapper;
import com.youlai.decision.model.ArtifactKind;
import com.youlai.decision.model.AuditLog;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final JsonService jsonService;

    /**
     * 创建操作审计服务。
     *
     * @param auditLogMapper 审计日志 Mapper
     * @param jsonService JSON 序列化服务
     */
    public AuditService(AuditLogMapper auditLogMapper, JsonService jsonService) {
        this.auditLogMapper = auditLogMapper;
        this.jsonService = jsonService;
    }

    /**
     * 记录一条策略资产操作审计日志。
     *
     * @param action 操作类型
     * @param kind 操作对象类型
     * @param code 操作对象编码
     * @param detail 操作详情对象
     */
    public void record(String action, ArtifactKind kind, String code, Object detail) {
        AuditLog log = new AuditLog();
        log.setOperator("system");
        log.setAction(action);
        log.setTargetKind(kind == null ? null : kind.name());
        log.setTargetCode(code);
        log.setDetailJson(jsonService.write(detail));
        auditLogMapper.insert(log);
    }
}
