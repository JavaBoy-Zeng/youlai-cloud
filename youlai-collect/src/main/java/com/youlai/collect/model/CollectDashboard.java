package com.youlai.collect.model;

public record CollectDashboard(
        long modelCount,
        long apiCount,
        long taskCount,
        long runningCount,
        long successCount,
        long failedCount
) {
}
