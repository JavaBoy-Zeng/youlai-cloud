package com.youlai.decision.model;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 决策资产状态中文化与旧英文状态兼容工具。
 */
public final class DecisionStatus {

    public static final String DRAFT = "草稿";
    public static final String ENABLED = "已启用";
    public static final String PUBLISHED = "已发布";
    public static final String DISABLED = "已停用";

    private static final Map<String, String> ENGLISH_TO_CHINESE = Map.of(
            "DRAFT", DRAFT,
            "ENABLED", ENABLED,
            "PUBLISHED", PUBLISHED,
            "DISABLED", DISABLED
    );

    private static final Set<String> RUNNABLE = Set.of(PUBLISHED, ENABLED);

    /**
     * 隐藏工具类构造方法。
     */
    private DecisionStatus() {
    }

    /**
     * 将英文旧状态或空值归一化为中文状态。
     *
     * @param status 原始状态
     * @return 中文状态
     */
    public static String normalize(String status) {
        if (!StringUtils.hasText(status)) {
            return DRAFT;
        }
        String trimmed = status.trim();
        return ENGLISH_TO_CHINESE.getOrDefault(trimmed.toUpperCase(Locale.ROOT), trimmed);
    }

    /**
     * 判断资产状态是否允许执行。
     *
     * @param status 原始状态
     * @return 是否可执行
     */
    public static boolean isRunnable(String status) {
        return RUNNABLE.contains(normalize(status));
    }
}
