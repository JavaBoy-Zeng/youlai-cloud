package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.flowable.mapper.AppBuilderModelFieldMapper;
import com.youlai.flowable.mapper.AppBuilderModelMapper;
import com.youlai.flowable.model.entity.AppBuilderData;
import com.youlai.flowable.model.entity.AppBuilderModel;
import com.youlai.flowable.model.entity.AppBuilderModelField;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AppBuilderSchemaService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AppBuilderModelMapper modelMapper;
    private final AppBuilderModelFieldMapper fieldMapper;

    public void syncModelTable(Long modelId) {
        AppBuilderModel model = modelMapper.selectById(modelId);
        Assert.notNull(model, "模型不存在");
        List<AppBuilderModelField> fields = fieldMapper.selectList(new LambdaQueryWrapper<AppBuilderModelField>()
                .eq(AppBuilderModelField::getModelId, modelId)
                .orderByAsc(AppBuilderModelField::getSortOrder, AppBuilderModelField::getId));
        syncModelTable(model, fields);
    }

    public void syncModelTable(AppBuilderModel model, List<AppBuilderModelField> fields) {
        String tableName = safeIdentifier(StrUtil.blankToDefault(model.getTableName(), "biz_" + model.getModelCode()), "表名不合法");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  `id` BIGINT NOT NULL,
                  `business_key` VARCHAR(128) NOT NULL,
                  `status` VARCHAR(32) DEFAULT 'DRAFT',
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY %s (`business_key`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='%s'
                """.formatted(q(tableName), qIndex("uk_" + tableName + "_business_key"), sqlComment(model.getModelName())));

        Set<String> columns = existingColumns(tableName);
        for (AppBuilderModelField field : fields) {
            String fieldCode = safeIdentifier(field.getFieldCode(), "字段编码不合法");
            if (columns.contains(fieldCode.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String nullable = Integer.valueOf(1).equals(field.getRequired()) ? " NOT NULL" : " NULL";
            jdbcTemplate.execute("ALTER TABLE " + q(tableName)
                    + " ADD COLUMN " + q(fieldCode) + " " + toSqlType(field) + nullable
                    + " COMMENT '" + sqlComment(field.getFieldName()) + "'");
        }
        createMainFieldIndex(tableName, model.getMainField(), columns);
    }

    public void upsertDataRow(AppBuilderData entity, Map<String, Object> data) {
        AppBuilderModel model = modelMapper.selectById(entity.getModelId());
        Assert.notNull(model, "模型不存在");
        List<AppBuilderModelField> fields = fieldMapper.selectList(new LambdaQueryWrapper<AppBuilderModelField>()
                .eq(AppBuilderModelField::getModelId, entity.getModelId())
                .orderByAsc(AppBuilderModelField::getSortOrder, AppBuilderModelField::getId));
        syncModelTable(model, fields);

        String tableName = safeIdentifier(model.getTableName(), "表名不合法");
        List<String> columns = new ArrayList<>(List.of("id", "business_key", "status"));
        List<Object> values = new ArrayList<>(List.of(entity.getId(), entity.getBusinessKey(), entity.getStatus()));
        for (AppBuilderModelField field : fields) {
            String column = safeIdentifier(field.getFieldCode(), "字段编码不合法");
            columns.add(column);
            values.add(normalizeValue(data.get(field.getFieldCode())));
        }

        String columnSql = columns.stream().map(this::q).reduce((a, b) -> a + "," + b).orElse("");
        String placeholderSql = String.join(",", Collections.nCopies(columns.size(), "?"));
        String updateSql = columns.stream()
                .filter(column -> !"id".equals(column))
                .map(column -> q(column) + "=VALUES(" + q(column) + ")")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        jdbcTemplate.update("INSERT INTO " + q(tableName) + " (" + columnSql + ") VALUES (" + placeholderSql + ")"
                + " ON DUPLICATE KEY UPDATE " + updateSql, values.toArray());
    }

    public void deleteDataRow(AppBuilderData entity) {
        AppBuilderModel model = modelMapper.selectById(entity.getModelId());
        if (model == null || StrUtil.isBlank(model.getTableName())) {
            return;
        }
        String tableName = safeIdentifier(model.getTableName(), "表名不合法");
        jdbcTemplate.update("DELETE FROM " + q(tableName) + " WHERE `id` = ?", entity.getId());
    }

    private Set<String> existingColumns(String tableName) {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT LOWER(column_name)
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                """, String.class, tableName);
        return new HashSet<>(columns);
    }

    private void createMainFieldIndex(String tableName, String mainField, Set<String> oldColumns) {
        if (StrUtil.isBlank(mainField)) {
            return;
        }
        String column = safeIdentifier(mainField, "主字段不合法");
        Set<String> columns = new HashSet<>(oldColumns);
        columns.addAll(existingColumns(tableName));
        if (!columns.contains(column.toLowerCase(Locale.ROOT))) {
            return;
        }
        String indexName = indexName("idx_" + tableName + "_" + column);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("CREATE INDEX " + q(indexName) + " ON " + q(tableName) + " (" + q(column) + ")");
        }
    }

    private String toSqlType(AppBuilderModelField field) {
        String type = StrUtil.blankToDefault(field.getDbType(), field.getFieldType()).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "int", "integer" -> "INT";
            case "bigint" -> "BIGINT";
            case "number", "decimal" -> "DECIMAL(18,2)";
            case "date" -> "DATE";
            case "datetime", "time" -> "DATETIME";
            case "text", "textarea" -> "TEXT";
            case "json" -> "JSON";
            case "boolean", "bool" -> "TINYINT(1)";
            default -> "VARCHAR(255)";
        };
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("业务数据字段JSON序列化失败", e);
            }
        }
        return value;
    }

    private String safeIdentifier(String value, String message) {
        Assert.isTrue(StrUtil.isNotBlank(value) && IDENTIFIER_PATTERN.matcher(value).matches(), message + "：" + value);
        return value;
    }

    private String q(String identifier) {
        return "`" + safeIdentifier(identifier, "数据库标识符不合法") + "`";
    }

    private String qIndex(String identifier) {
        return "`" + indexName(identifier) + "`";
    }

    private String indexName(String value) {
        String normalized = value.length() > 64 ? value.substring(0, 64) : value;
        return safeIdentifier(normalized, "索引名不合法");
    }

    private String sqlComment(String value) {
        return StrUtil.blankToDefault(value, "").replace("'", "''");
    }
}
