package com.youlai.decision.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicDataSourceManager {

    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DataSource getDataSource(Long sourceId) {
        return dataSourceCache.computeIfAbsent(sourceId, this::createDataSource);
    }

    public void refresh(Long sourceId) {
        HikariDataSource old = dataSourceCache.remove(sourceId);
        if (old != null) {
            old.close();
        }
        dataSourceCache.put(sourceId, createDataSource(sourceId));
    }

    public void remove(Long sourceId) {
        HikariDataSource old = dataSourceCache.remove(sourceId);
        if (old != null) {
            old.close();
        }
    }

    private HikariDataSource createDataSource(Long sourceId) {
        DbSourceConfig config = dbSourceService.getById(sourceId);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(decrypt(config.getPasswordEncrypt()));
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setMaximumPoolSize(config.getPoolMaxSize());
        hikariConfig.setMinimumIdle(config.getPoolMinSize());

        return new HikariDataSource(hikariConfig);
    }
}