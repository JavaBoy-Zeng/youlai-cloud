package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 决策执行日志实体，用于保存每一次实时决策调用的请求、结果、路径和命中解释。
 */
@Getter
@Setter
@TableName("decision_execute_log")
public class DecisionExecuteLog extends BaseEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单次决策追踪 ID。 */
    private String traceId;

    /** 业务事件 ID，用于幂等和追溯。 */
    private String eventId;

    /** 决策场景编码。 */
    private String sceneCode;

    /** 最终决策结果。 */
    private String decisionResult;

    /** 最终风险等级。 */
    private String riskLevel;

    /** 最终风险分。 */
    private Integer score;

    /** 请求参数 JSON。 */
    private String requestJson;

    /** 响应结果 JSON。 */
    private String responseJson;

    /** 命中规则列表 JSON。 */
    private String hitRulesJson;

    /** 决策路径 JSON。 */
    private String pathJson;

    /** 是否执行成功。 */
    private boolean success;

    /** 异常信息，执行失败时写入。 */
    private String errorMessage;

    /** 执行耗时，单位毫秒。 */
    private Long elapsedMs;
}
