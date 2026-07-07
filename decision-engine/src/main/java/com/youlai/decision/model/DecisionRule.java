package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_rule")
public class DecisionRule extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneCode;
    private String code;
    private String name;
    private Integer priority = 0;
    private String expressionType = "MIXED";
    private String matchMode = "BOOLEAN";
    private Integer requiredMatch = 0;
    private String conditionExpression;
    private String conditionsJson = "{}";
    private String actionsJson = "{}";
    private String fallbackActionJson = "{}";
    private String status = DecisionStatus.DRAFT;
    private Integer versionNo = 1;
    private String owner;
    private String remark;
}
