package com.youlai.demo.monitor.interceptor;

import com.youlai.demo.monitor.service.SqlMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.sql.SQLTimeoutException;
import java.util.Properties;

@Slf4j
@Component
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                args = {
                        MappedStatement.class,
                        Object.class,
                        RowBounds.class,
                        ResultHandler.class
                }
        ),
        @Signature(
                type = Executor.class,
                method = "update",
                args = {
                        MappedStatement.class,
                        Object.class
                }
        )
})
public class SqlMonitorInterceptor implements Interceptor {

    private static final long SLOW_SQL_THRESHOLD = 1000L;

    private final SqlMetricsService sqlMetricsService;

    public SqlMonitorInterceptor(SqlMetricsService sqlMetricsService) {
        this.sqlMetricsService = sqlMetricsService;
    }

    @PostConstruct
    public void init() {
        log.info("SQL monitor interceptor initialized");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();

        MappedStatement mappedStatement = null;
        String mapperId = "unknown";
        String sqlType = "UNKNOWN";
        String sql = "";

        try {
            Object[] args = invocation.getArgs();

            mappedStatement = (MappedStatement) args[0];
            Object parameter = args.length > 1 ? args[1] : null;

            mapperId = mappedStatement.getId();
            sqlType = getSqlType(mappedStatement);

            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            sql = formatSql(boundSql.getSql());

            Object result = invocation.proceed();

            long costMs = System.currentTimeMillis() - startTime;

            // 记录所有 SQL 执行耗时
            sqlMetricsService.recordSqlCost(mapperId, sqlType, costMs);

            // 记录慢 SQL 次数
            if (costMs >= SLOW_SQL_THRESHOLD) {
                sqlMetricsService.recordSlowSql(mapperId, sqlType);

                log.warn(
                        "[慢SQL] mapperId={}, sqlType={}, cost={}ms, sql={}",
                        mapperId,
                        sqlType,
                        costMs,
                        sql
                );
            }

            return result;
        } catch (Throwable e) {
            long costMs = System.currentTimeMillis() - startTime;

            String errorType = getErrorType(e);

            // 记录错误 SQL 次数
            sqlMetricsService.recordErrorSql(mapperId, sqlType, errorType);

            // 记录超时 SQL
            if (isTimeoutException(e)) {
                sqlMetricsService.recordTimeoutSql(mapperId, sqlType);
            }

            log.error(
                    "[错误SQL] mapperId={}, sqlType={}, cost={}ms, errorType={}, sql={}, error={}",
                    mapperId,
                    sqlType,
                    costMs,
                    errorType,
                    sql,
                    e.getMessage(),
                    e
            );

            throw e;
        }
    }

    private String getSqlType(MappedStatement mappedStatement) {
        SqlCommandType commandType = mappedStatement.getSqlCommandType();
        return commandType == null ? "UNKNOWN" : commandType.name();
    }

    private String formatSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replaceAll("\\s+", " ").trim();
    }

    private String getErrorType(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }

        String lowerMsg = msg.toLowerCase();

        if (lowerMsg.contains("deadlock")) {
            return "DEADLOCK";
        }
        if (lowerMsg.contains("lock wait timeout")) {
            return "LOCK_WAIT_TIMEOUT";
        }
        if (lowerMsg.contains("timeout")) {
            return "TIMEOUT";
        }
        if (lowerMsg.contains("duplicate")) {
            return "DUPLICATE_KEY";
        }
        if (lowerMsg.contains("unknown column")) {
            return "UNKNOWN_COLUMN";
        }
        if (lowerMsg.contains("table") && lowerMsg.contains("doesn't exist")) {
            return "TABLE_NOT_EXISTS";
        }
        if (lowerMsg.contains("bad sql grammar")) {
            return "BAD_SQL_GRAMMAR";
        }

        return e.getClass().getSimpleName();
    }

    private boolean isTimeoutException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SQLTimeoutException) {
                return true;
            }

            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
