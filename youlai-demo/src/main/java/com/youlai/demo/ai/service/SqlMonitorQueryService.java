package com.youlai.demo.ai.service;

import com.youlai.demo.ai.model.SqlMonitorSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Service
public class SqlMonitorQueryService {

    private static final String CATEGORY = "ai";

    private final MeterRegistry meterRegistry;

    public SqlMonitorQueryService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public SqlMonitorSummary getSummary() {
        Collection<Timer> sqlTimers = meterRegistry.find("app_sql_execute_time").timers();

        long executeCount = sqlTimers.stream()
                .mapToLong(Timer::count)
                .sum();

        double totalCostMs = sqlTimers.stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
                .sum();

        double averageCostMs = executeCount == 0 ? 0 : totalCostMs / executeCount;

        double maxCostMs = sqlTimers.stream()
                .mapToDouble(timer -> timer.max(TimeUnit.MILLISECONDS))
                .max()
                .orElse(0);

        return new SqlMonitorSummary(
                CATEGORY,
                executeCount,
                averageCostMs,
                maxCostMs,
                sumCounters("app_sql_slow_total"),
                sumCounters("app_sql_error_total"),
                sumCounters("app_sql_timeout_total")
        );
    }

    private double sumCounters(String meterName) {
        return meterRegistry.find(meterName)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
