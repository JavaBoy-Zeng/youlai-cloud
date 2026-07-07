package com.youlai.demo.ai.service;

import com.youlai.demo.ai.model.SqlMonitorSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SqlMonitorQueryServiceTest {

    @Test
    void shouldReturnEmptySummaryWhenNoSqlMetrics() {
        SqlMonitorQueryService service = new SqlMonitorQueryService(new SimpleMeterRegistry());

        SqlMonitorSummary summary = service.getSummary();

        assertThat(summary.category()).isEqualTo("ai");
        assertThat(summary.executeCount()).isZero();
        assertThat(summary.averageCostMs()).isZero();
        assertThat(summary.maxCostMs()).isZero();
        assertThat(summary.slowSqlCount()).isZero();
        assertThat(summary.errorSqlCount()).isZero();
        assertThat(summary.timeoutSqlCount()).isZero();
    }

    @Test
    void shouldAggregateSqlMonitorMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Timer.builder("app_sql_execute_time")
                .tag("mapper", "DemoProjectMapper.selectById")
                .tag("sql_type", "SELECT")
                .register(meterRegistry)
                .record(100, TimeUnit.MILLISECONDS);

        Timer.builder("app_sql_execute_time")
                .tag("mapper", "DemoProjectMapper.selectList")
                .tag("sql_type", "SELECT")
                .register(meterRegistry)
                .record(300, TimeUnit.MILLISECONDS);

        Counter.builder("app_sql_slow_total")
                .tag("mapper", "DemoProjectMapper.selectList")
                .tag("sql_type", "SELECT")
                .register(meterRegistry)
                .increment();

        Counter.builder("app_sql_error_total")
                .tag("mapper", "DemoProjectMapper.insert")
                .tag("sql_type", "INSERT")
                .tag("error_type", "DUPLICATE_KEY")
                .register(meterRegistry)
                .increment(2);

        Counter.builder("app_sql_timeout_total")
                .tag("mapper", "DemoProjectMapper.update")
                .tag("sql_type", "UPDATE")
                .register(meterRegistry)
                .increment();

        SqlMonitorQueryService service = new SqlMonitorQueryService(meterRegistry);

        SqlMonitorSummary summary = service.getSummary();

        assertThat(summary.category()).isEqualTo("ai");
        assertThat(summary.executeCount()).isEqualTo(2);
        assertThat(summary.averageCostMs()).isEqualTo(200);
        assertThat(summary.maxCostMs()).isEqualTo(300);
        assertThat(summary.slowSqlCount()).isEqualTo(1);
        assertThat(summary.errorSqlCount()).isEqualTo(2);
        assertThat(summary.timeoutSqlCount()).isEqualTo(1);
    }
}
