package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_hit_detail_log")
public class DecisionHitDetailLog extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String sceneCode;
    private String targetType;
    private String targetCode;
    private String detailType;
    private String expression;
    private Boolean matched;
    private String detailJson;
    private Long elapsedMs;
}
