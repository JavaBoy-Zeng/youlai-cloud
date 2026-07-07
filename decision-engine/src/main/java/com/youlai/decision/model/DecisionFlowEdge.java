package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_flow_edge")
public class DecisionFlowEdge extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private String edgeKey;
    private String sourceKey;
    private String targetKey;
    private String branch;
    private String label;
}
