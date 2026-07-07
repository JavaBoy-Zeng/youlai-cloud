package com.youlai.decision.model;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API 请求和响应模型集合。
 */
public final class ApiModels {

    /**
     * 隐藏工具类构造方法。
     */
    private ApiModels() {
    }

    /**
     * 策略资产保存请求。
     *
     * @param code 资产编码
     * @param name 资产名称
     * @param category 资产分类
     * @param status 资产状态
     * @param tags 标签字符串
     * @param owner 负责人
     * @param remark 备注
     * @param content 配置内容
     */
    public record ArtifactRequest(
            String code,
            String name,
            String category,
            String status,
            String tags,
            String owner,
            String remark,
            Map<String, Object> content
    ) {
    }

    /**
     * 策略资产响应。
     *
     * @param id 资产 ID
     * @param kind 资产类型
     * @param code 资产编码
     * @param name 资产名称
     * @param category 资产分类
     * @param status 资产状态
     * @param tags 标签字符串
     * @param owner 负责人
     * @param versionNo 当前版本号
     * @param content 配置内容
     * @param remark 备注
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    public record ArtifactResponse(
            Long id,
            ArtifactKind kind,
            String code,
            String name,
            String category,
            String status,
            String tags,
            String owner,
            Integer versionNo,
            Map<String, Object> content,
            String remark,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    /**
     * 实时决策请求。
     *
     * @param sceneCode 场景编码
     * @param eventId 业务事件 ID
     * @param userId 用户 ID
     * @param params 决策入参
     */
    public record ExecuteDecisionRequest(
            @NotBlank String sceneCode,
            String eventId,
            String userId,
            Map<String, Object> params
    ) {
    }

    /**
     * 单规则测试请求。
     *
     * @param ruleCode 规则编码
     * @param params 测试入参
     */
    public record RuleTestRequest(
            @NotBlank String ruleCode,
            Map<String, Object> params
    ) {
    }

    /**
     * 决策流测试请求。
     *
     * @param flowCode 决策流编码
     * @param eventId 业务事件 ID
     * @param userId 用户 ID
     * @param params 测试入参
     */
    public record FlowTestRequest(
            @NotBlank String flowCode,
            String eventId,
            String userId,
            Map<String, Object> params
    ) {
    }

    /**
     * 发布请求。
     *
     * @param environment 发布环境
     * @param publishBy 发布人
     * @param remark 发布说明
     */
    public record PublishRequest(
            String environment,
            String publishBy,
            String remark
    ) {
    }

    /**
     * 命中规则解释。
     *
     * @param ruleCode 规则编码
     * @param ruleName 规则名称
     * @param reason 命中原因
     * @param score 规则贡献分
     * @param decisionResult 规则决策结果
     * @param riskLevel 规则风险等级
     * @param tags 命中标签
     */
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

    /**
     * 决策响应。
     *
     * @param traceId 追踪 ID
     * @param eventId 业务事件 ID
     * @param sceneCode 场景编码
     * @param decisionResult 最终决策结果
     * @param riskLevel 最终风险等级
     * @param score 最终风险分
     * @param tags 命中标签
     * @param hitRules 命中规则列表
     * @param path 决策路径
     * @param outputs 输出变量
     * @param elapsedMs 执行耗时
     */
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
            Long elapsedMs
    ) {
    }
}
