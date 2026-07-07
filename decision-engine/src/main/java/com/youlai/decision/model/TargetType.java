package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum TargetType {
    SCENE("SCENE"),
    VARIABLE("VARIABLE"),
    RULE("RULE"),
    RULE_SET("RULE_SET"),
    FLOW("FLOW"),
    DATA_SOURCE("DATA_SOURCE"),
    MODEL("MODEL"),
    SCORE_CARD("SCORE_CARD"),
    DECISION_TABLE("DECISION_TABLE");

    @EnumValue
    private final String value;

    TargetType(String value) {
        this.value = value;
    }
}
