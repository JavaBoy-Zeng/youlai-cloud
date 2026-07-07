package com.youlai.demo.ai.tool;

import com.youlai.demo.ai.model.AiToolResult;
import com.youlai.demo.ai.model.SqlMonitorSummary;
import com.youlai.demo.ai.service.SqlMonitorQueryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqlMonitorAiToolsTest {

    @Test
    void shouldWrapSqlMonitorSummaryAsAiToolResult() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Counter.builder("app_sql_slow_total")
                .tag("mapper", "DemoProjectMapper.selectList")
                .tag("sql_type", "SELECT")
                .register(meterRegistry)
                .increment();

        SqlMonitorAiTools tools = new SqlMonitorAiTools(new SqlMonitorQueryService(meterRegistry));

        AiToolResult<SqlMonitorSummary> result = tools.querySqlMonitorSummary(
                new ToolContext(Map.of("category", "ai"))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().category()).isEqualTo("ai");
        assertThat(result.data().slowSqlCount()).isEqualTo(1);
    }
}
