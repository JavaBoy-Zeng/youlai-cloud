package com.youlai.demo.ai.tool;

import com.youlai.demo.ai.model.AiToolResult;
import com.youlai.demo.ai.model.SqlMonitorSummary;
import com.youlai.demo.ai.service.SqlMonitorQueryService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SqlMonitorAiTools {

    private final SqlMonitorQueryService sqlMonitorQueryService;

    public SqlMonitorAiTools(SqlMonitorQueryService sqlMonitorQueryService) {
        this.sqlMonitorQueryService = sqlMonitorQueryService;
    }

    @Tool(
            name = "query_sql_monitor_summary",
            description = "Query SQL monitor metrics summary, including SQL execute count, average cost, slow SQL count, error SQL count, and timeout SQL count."
    )
    public AiToolResult<SqlMonitorSummary> querySqlMonitorSummary(ToolContext context) {
        return AiToolResult.success(sqlMonitorQueryService.getSummary());
    }
}
