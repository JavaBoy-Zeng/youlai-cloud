package com.youlai.demo.ai.model;

public record SqlMonitorSummary(
        String category,
        long executeCount,
        double averageCostMs,
        double maxCostMs,
        double slowSqlCount,
        double errorSqlCount,
        double timeoutSqlCount
) {
}
