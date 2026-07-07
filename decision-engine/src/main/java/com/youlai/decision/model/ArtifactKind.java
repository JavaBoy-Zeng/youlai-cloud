package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.EnumValue;

import java.util.Locale;

public enum ArtifactKind {
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

    ArtifactKind(String value) {
        this.value = value;
    }

    /**
     * 将 REST 路径中的复数或中划线类型转换为资产枚举。
     *
     * @param path 路径类型片段
     * @return 资产类型枚举
     */
    public static ArtifactKind fromPath(String path) {
        String normalized = path.replace("-", "_").toUpperCase(Locale.ROOT);
        if ("RULE_SETS".equals(normalized)) {
            return RULE_SET;
        }
        if ("SCORE_CARDS".equals(normalized)) {
            return SCORE_CARD;
        }
        if ("DECISION_TABLES".equals(normalized)) {
            return DECISION_TABLE;
        }
        if (normalized.endsWith("S")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return ArtifactKind.valueOf(normalized);
    }
}
