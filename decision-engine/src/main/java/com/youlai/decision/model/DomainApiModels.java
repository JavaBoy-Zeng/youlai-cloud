package com.youlai.decision.model;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DomainApiModels {

    private DomainApiModels() {
    }

    public record SceneRequest(
            String code,
            String name,
            String category,
            String status,
            List<Map<String, Object>> inputs,
            List<Map<String, Object>> outputs,
            String owner,
            String remark
    ) {
    }

    public record VariableRequest(
            String sceneCode,
            String code,
            String name,
            String type,
            String source,
            Map<String, Object> sourceConfig,
            Object defaultValue,
            String status,
            String remark
    ) {
    }

    public record RuleRequest(
            String sceneCode,
            String code,
            String name,
            Integer priority,
            String expressionType,
            String matchMode,
            Integer requiredMatch,
            String conditionExpression,
            Map<String, Object> conditions,
            Map<String, Object> actions,
            Map<String, Object> fallbackAction,
            String status,
            String owner,
            String remark
    ) {
    }

    public record RuleSetRequest(
            String sceneCode,
            String code,
            String name,
            String strategy,
            Integer requiredMatch,
            Boolean shortCircuit,
            List<String> ruleCodes,
            String status,
            String remark
    ) {
    }

    public record FlowRequest(
            String sceneCode,
            String code,
            String name,
            String status,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            String remark
    ) {
    }

    public record FlowView(
            Long id,
            String sceneCode,
            String code,
            String name,
            String status,
            Integer versionNo,
            String remark,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges
    ) {
    }

    public record AdvancedAssetRequest(
            String sceneCode,
            String code,
            String name,
            String type,
            String provider,
            String hitPolicy,
            Map<String, Object> config,
            List<Map<String, Object>> items,
            List<Map<String, Object>> mapping,
            List<Map<String, Object>> rows,
            String status,
            String remark
    ) {
    }

    public record PublishRequestForm(
            @NotBlank String targetType,
            Long targetId,
            String code,
            Long workflowModelId,
            String applicant,
            String remark
    ) {
    }

    public record GrayPolicyRequest(
            String sceneCode,
            String targetType,
            String targetCode,
            Integer versionNo,
            Integer percent,
            Map<String, Object> condition,
            Boolean enabled,
            String remark
    ) {
    }

    public record SimulationJobRequest(
            String sceneCode,
            String name,
            List<Map<String, Object>> samples,
            String remark
    ) {
    }

    public record RuleTestRequest(
            @NotBlank String ruleCode,
            Map<String, Object> params
    ) {
    }

    public record FlowTestRequest(
            @NotBlank String flowCode,
            String eventId,
            String userId,
            Map<String, Object> params
    ) {
    }

    public record ExecuteDecisionRequest(
            @NotBlank String sceneCode,
            String eventId,
            String userId,
            Map<String, Object> params
    ) {
    }

    public record ConditionTrace(
            String targetType,
            String targetCode,
            String expression,
            Boolean matched,
            Map<String, Object> facts,
            Long elapsedMs,
            String errorMessage
    ) {
    }

    public record HitRule(
            String ruleCode,
            String ruleName,
            String reason,
            Integer score,
            String decisionResult,
            String riskLevel,
            List<String> tags
    ) {
    }

    public record DecisionResponse(
            String traceId,
            String eventId,
            String sceneCode,
            String decisionResult,
            String riskLevel,
            Integer score,
            List<String> tags,
            List<HitRule> hitRules,
            List<String> path,
            Map<String, Object> outputs,
            List<ConditionTrace> conditionTraces,
            Long elapsedMs
    ) {
    }

    public record VersionResponse(
            Long id,
            String targetType,
            Long targetId,
            String code,
            Integer versionNo,
            String snapshotJson,
            String remark,
            LocalDateTime createTime
    ) {
    }
}
