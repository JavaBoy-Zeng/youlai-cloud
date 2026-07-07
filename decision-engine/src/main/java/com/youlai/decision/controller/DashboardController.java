package com.youlai.decision.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.common.result.Result;
import com.youlai.decision.mapper.AuditLogMapper;
import com.youlai.decision.mapper.DecisionExecuteLogMapper;
import com.youlai.decision.mapper.DecisionHitDetailLogMapper;
import com.youlai.decision.model.AuditLog;
import com.youlai.decision.model.DecisionExecuteLog;
import com.youlai.decision.model.DecisionHitDetailLog;
import com.youlai.decision.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作台和日志查询接口，提供监控、执行日志与操作审计视图。
 */
@RestController
@RequestMapping("/api/v1/decision-engine")
public class DashboardController {

    /** 工作台统计服务。 */
    private final DashboardService dashboardService;

    /** 决策执行日志仓储。 */
    private final DecisionExecuteLogMapper executeLogMapper;

    /** 操作审计日志仓储。 */
    private final AuditLogMapper auditLogMapper;
    private final DecisionHitDetailLogMapper hitDetailLogMapper;

    /**
     * 创建工作台控制器。
     *
     * @param dashboardService 工作台统计服务
     * @param executeLogMapper 执行日志 Mapper
     * @param auditLogMapper 审计日志 Mapper
     */
    public DashboardController(
            DashboardService dashboardService,
            DecisionExecuteLogMapper executeLogMapper,
            AuditLogMapper auditLogMapper,
            DecisionHitDetailLogMapper hitDetailLogMapper
    ) {
        this.dashboardService = dashboardService;
        this.executeLogMapper = executeLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.hitDetailLogMapper = hitDetailLogMapper;
    }

    @GetMapping("/logs/hit-details")
    public Result<List<DecisionHitDetailLog>> hitDetailLogs() {
        return Result.success(hitDetailLogMapper.selectList(Wrappers.lambdaQuery(DecisionHitDetailLog.class)
                .orderByDesc(DecisionHitDetailLog::getCreateTime)
                .last("LIMIT 100")));
    }

    /**
     * 获取决策引擎工作台概览。
     *
     * @return 概览指标
     */
    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(dashboardService.overview());
    }

    /**
     * 查询最近一百条执行日志。
     *
     * @return 执行日志列表
     */
    @GetMapping("/logs/executions")
    public Result<List<DecisionExecuteLog>> executionLogs() {
        return Result.success(executeLogMapper.selectList(Wrappers.lambdaQuery(DecisionExecuteLog.class)
                .orderByDesc(DecisionExecuteLog::getCreateTime)
                .last("LIMIT 100")));
    }

    /**
     * 查询最近一百条操作审计日志。
     *
     * @return 审计日志列表
     */
    @GetMapping("/logs/audits")
    public Result<List<AuditLog>> auditLogs() {
        return Result.success(auditLogMapper.selectList(Wrappers.lambdaQuery(AuditLog.class)
                .orderByDesc(AuditLog::getCreateTime)
                .last("LIMIT 100")));
    }
}
