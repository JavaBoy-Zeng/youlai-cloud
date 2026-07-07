package com.youlai.demo.monitor.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SqlMetricsService {

    private final MeterRegistry meterRegistry;

    public SqlMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录 SQL 执行耗时
     */
    public void recordSqlCost(String mapperId, String sqlType, long costMs) {
        Timer.builder("app_sql_execute_time")
                .description("SQL execution time")
                .tag("mapper", mapperId)
                .tag("sql_type", sqlType)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(costMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录慢 SQL 次数
     */
    public void recordSlowSql(String mapperId, String sqlType) {
        Counter.builder("app_sql_slow_total")
                .description("Slow SQL total count")
                .tag("mapper", mapperId)
                .tag("sql_type", sqlType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录错误 SQL 次数
     */
    public void recordErrorSql(String mapperId, String sqlType, String errorType) {
        Counter.builder("app_sql_error_total")
                .description("Error SQL total count")
                .tag("mapper", mapperId)
                .tag("sql_type", sqlType)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录 SQL 超时次数
     */
    public void recordTimeoutSql(String mapperId, String sqlType) {
        Counter.builder("app_sql_timeout_total")
                .description("SQL timeout total count")
                .tag("mapper", mapperId)
                .tag("sql_type", sqlType)
                .register(meterRegistry)
                .increment();
    }
}