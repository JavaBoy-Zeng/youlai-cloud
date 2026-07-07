package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_rule_condition")
public class DecisionRuleCondition extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private String conditionKey;
    private String field;
    private String operator;
    private String valueJson;
    private String expression;
    private Boolean matched;
    private Integer sort;
}
