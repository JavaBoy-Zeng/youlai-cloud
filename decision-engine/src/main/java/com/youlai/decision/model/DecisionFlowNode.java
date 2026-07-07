package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_flow_node")
public class DecisionFlowNode extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private String nodeKey;
    private String type;
    private String code;
    private String label;
    private Boolean enabled = true;
    private Integer sort = 0;
    private String configJson = "{}";
    private Integer x = 0;
    private Integer y = 0;
}
