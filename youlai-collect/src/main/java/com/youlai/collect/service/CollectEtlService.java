package com.youlai.collect.service;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.collect.mapper.*;
import com.youlai.collect.model.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectEtlService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    private final CollectModelMapper modelMapper;
    private final CollectModelFieldMapper modelFieldMapper;
    private final CollectApiMapper apiMapper;
    private final CollectDbSourceMapper dbSourceMapper;
    private final CollectTaskMapper taskMapper;
    private final CollectInstanceMapper instanceMapper;
    private final CollectTaskMessageMapper messageMapper;
    private final CollectRawDataMapper rawDataMapper;
    private final CollectErrorDataMapper errorDataMapper;
    private final CollectQualityReportMapper qualityReportMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public CollectEtlService(
            CollectModelMapper modelMapper,
            CollectModelFieldMapper modelFieldMapper,
            CollectApiMapper apiMapper,
            CollectDbSourceMapper dbSourceMapper,
            CollectTaskMapper taskMapper,
            CollectInstanceMapper instanceMapper,
            CollectTaskMessageMapper messageMapper,
            CollectRawDataMapper rawDataMapper,
            CollectErrorDataMapper errorDataMapper,
            CollectQualityReportMapper qualityReportMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.modelMapper = modelMapper;
        this.modelFieldMapper = modelFieldMapper;
        this.apiMapper = apiMapper;
        this.dbSourceMapper = dbSourceMapper;
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.messageMapper = messageMapper;
        this.rawDataMapper = rawDataMapper;
        this.errorDataMapper = errorDataMapper;
        this.qualityReportMapper = qualityReportMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public CollectInstance execute(CollectTask task, CollectInstance instance, CollectTaskMessage message) {
        CollectInstance running = new CollectInstance();
        running.setId(instance.getId());
        running.setStatus("running");
        running.setStartTime(LocalDateTime.now());
        instanceMapper.updateById(running);

        CollectTaskMessage consuming = new CollectTaskMessage();
        consuming.setId(message.getId());
        consuming.setConsumeStatus("running");
        consuming.setConsumeTime(LocalDateTime.now());
        messageMapper.updateById(consuming);

        EtlStats stats = new EtlStats();
        try {
            CollectModel model = requireModel(task.getModelId());
            CollectApi api = requireApi(task.getApiId());
            List<CollectModelField> fields = listModelFields(model.getId());
            List<Map<String, Object>> rawRows = fetchRows(api, task);
            stats.totalCount = rawRows.size();

            ensureTargetTable(model, fields);
            if ("overwrite".equals(task.getInsertStrategy())) {
                jdbcTemplate.execute("TRUNCATE TABLE " + quoteIdentifier(targetTableName(model)));
            }

            List<MappingRule> mappings = parseMappings(task.getMappingJson(), fields);
            List<Map<String, Object>> transforms = parseList(task.getTransformJson());
            Set<String> seenUniqueKeys = new HashSet<>();
            Map<String, Integer> nonBlankByField = new LinkedHashMap<>();
            for (CollectModelField field : fields) {
                nonBlankByField.put(field.getFieldCode(), 0);
            }

            int index = 0;
            for (Map<String, Object> rawRow : rawRows) {
                index++;
                CollectRawData rawData = saveRaw(task, instance, index, rawRow);
                try {
                    Map<String, Object> transformed = transformRow(rawRow, mappings, transforms, fields);
                    validateRow(transformed, fields);
                    String uniqueKey = uniqueKey(transformed, fields);
                    if (StrUtil.isNotBlank(uniqueKey) && !seenUniqueKeys.add(uniqueKey)) {
                        stats.duplicateCount++;
                        markRaw(rawData.getId(), "duplicate", "批次内唯一键重复");
                        saveError(task, instance, index, "duplicate", "批次内唯一键重复", rawRow, transformed);
                        continue;
                    }

                    for (CollectModelField field : fields) {
                        if (transformed.get(field.getFieldCode()) != null && StrUtil.isNotBlank(String.valueOf(transformed.get(field.getFieldCode())))) {
                            nonBlankByField.computeIfPresent(field.getFieldCode(), (key, count) -> count + 1);
                        }
                    }

                    WriteResult writeResult = writeTarget(model, fields, transformed, task.getInsertStrategy());
                    stats.validCount++;
                    stats.insertedCount += writeResult.inserted();
                    stats.updatedCount += writeResult.updated();
                    stats.duplicateCount += writeResult.duplicated();
                    markRaw(rawData.getId(), "success", null);
                } catch (Exception ex) {
                    stats.invalidCount++;
                    stats.failedCount++;
                    markRaw(rawData.getId(), "failed", ex.getMessage());
                    saveError(task, instance, index, "transform_or_write", ex.getMessage(), rawRow, null);
                }
            }

            saveQuality(task, instance, stats, nonBlankByField);
            CollectInstance success = finishInstance(instance.getId(), stats, stats.failedCount == 0 ? "success" : "partial_success", null);
            finishMessage(message.getId(), "success", null);
            updateTaskCursor(task);
            return success;
        } catch (Exception ex) {
            saveQuality(task, instance, stats, Map.of());
            CollectInstance failed = finishInstance(instance.getId(), stats, "failed", ex.getMessage());
            finishMessage(message.getId(), "failed", ex.getMessage());
            return failed;
        }
    }

    private CollectModel requireModel(Long modelId) {
        CollectModel model = modelMapper.selectById(modelId);
        Assert.notNull(model, "采集模型不存在");
        Assert.isTrue(!"disabled".equals(model.getStatus()), "采集模型已停用");
        return model;
    }

    private CollectApi requireApi(Long apiId) {
        CollectApi api = apiMapper.selectById(apiId);
        Assert.notNull(api, "采集接口不存在");
        Assert.isTrue(!"disabled".equals(api.getStatus()), "采集接口已停用");
        return api;
    }

    private List<CollectModelField> listModelFields(Long modelId) {
        List<CollectModelField> fields = modelFieldMapper.selectList(new LambdaQueryWrapper<CollectModelField>()
                .eq(CollectModelField::getModelId, modelId)
                .orderByAsc(CollectModelField::getSort)
                .orderByAsc(CollectModelField::getId));
        Assert.isTrue(!fields.isEmpty(), "模型字段为空");
        return fields;
    }

    private List<Map<String, Object>> fetchRows(CollectApi api, CollectTask task) throws Exception {
        return switch (StrUtil.blankToDefault(api.getCollectType(), "").toLowerCase(Locale.ROOT)) {
            case "http" -> fetchHttp(api, task);
            case "db" -> fetchDb(api, task);
            case "mq" -> fetchConfiguredMessages(api, task);
            default -> throw new IllegalArgumentException("不支持的采集方式: " + api.getCollectType());
        };
    }

    private List<Map<String, Object>> fetchHttp(CollectApi api, CollectTask task) throws Exception {
        Map<String, Object> config = parseMap(api.getConfigJson());
        String url = requiredString(config, "url", "HTTP URL 不能为空");
        String method = stringValue(config.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        Map<String, Object> params = objectMap(config.get("params"));
        params.forEach((key, value) -> builder.queryParam(key, value));

        HttpHeaders headers = new HttpHeaders();
        objectMap(config.get("headers")).forEach((key, value) -> headers.add(key, stringValue(value)));
        Object body = config.get("body");
        ResponseEntity<String> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.valueOf(method),
                new HttpEntity<>(body, headers),
                String.class
        );
        Object payload = objectMapper.readValue(StrUtil.blankToDefault(response.getBody(), "[]"), Object.class);
        return normalizeRows(resolveRoot(payload, parseMap(api.getParseConfig()).get("rootPath")), task.getMaxFetchCount());
    }

    private List<Map<String, Object>> fetchDb(CollectApi api, CollectTask task) throws Exception {
        Map<String, Object> config = parseMap(api.getConfigJson());
        Long dbSourceId = longValue(config.get("dbSourceId"));
        CollectDbSource source = dbSourceId == null ? null : dbSourceMapper.selectById(dbSourceId);
        Assert.notNull(source, "DB 采集需要配置 dbSourceId");
        String sql = requiredString(config, "sql", "DB 采集 SQL 不能为空");
        if (StrUtil.isNotBlank(source.getDriverClass())) {
            Class.forName(source.getDriverClass());
        }
        try (Connection connection = DriverManager.getConnection(source.getJdbcUrl(), source.getUsername(), source.getPasswordEncrypt());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(maxFetchCount(task));
            List<Object> params = objectList(config.get("params"));
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                while (resultSet.next() && rows.size() < maxFetchCount(task)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        row.put(StrUtil.toCamelCase(metaData.getColumnLabel(i)), resultSet.getObject(i));
                        row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private List<Map<String, Object>> fetchConfiguredMessages(CollectApi api, CollectTask task) {
        Map<String, Object> config = parseMap(api.getConfigJson());
        Object messages = config.getOrDefault("messages", config.get("message"));
        return normalizeRows(messages == null ? List.of() : messages, task.getMaxFetchCount());
    }

    private CollectRawData saveRaw(CollectTask task, CollectInstance instance, int index, Map<String, Object> rawRow) {
        CollectRawData raw = new CollectRawData();
        raw.setTaskId(task.getId());
        raw.setInstanceId(instance.getId());
        raw.setTraceId(instance.getTraceId());
        raw.setDataIndex(index);
        raw.setRawBody(toJson(rawRow));
        raw.setStatus("pending");
        rawDataMapper.insert(raw);
        return raw;
    }

    private void markRaw(Long id, String status, String errorMessage) {
        CollectRawData raw = new CollectRawData();
        raw.setId(id);
        raw.setStatus(status);
        raw.setErrorMessage(errorMessage);
        rawDataMapper.updateById(raw);
    }

    private void saveError(CollectTask task, CollectInstance instance, int index, String type, String message, Map<String, Object> rawRow, Map<String, Object> transformed) {
        CollectErrorData error = new CollectErrorData();
        error.setTaskId(task.getId());
        error.setInstanceId(instance.getId());
        error.setTraceId(instance.getTraceId());
        error.setDataIndex(index);
        error.setErrorType(type);
        error.setErrorMessage(StrUtil.maxLength(message, 1000));
        error.setRawBody(toJson(rawRow));
        error.setTransformedBody(toJson(transformed));
        errorDataMapper.insert(error);
    }

    private Map<String, Object> transformRow(
            Map<String, Object> rawRow,
            List<MappingRule> mappings,
            List<Map<String, Object>> transforms,
            List<CollectModelField> fields
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (MappingRule mapping : mappings) {
            Object value = getPath(rawRow, mapping.source());
            if (value == null) {
                value = mapping.defaultValue();
            }
            row.put(mapping.target(), value);
        }

        for (Map<String, Object> rule : transforms) {
            String field = stringValue(rule.get("field"));
            String type = stringValue(rule.get("type"));
            if (StrUtil.isBlank(field) || StrUtil.isBlank(type)) {
                continue;
            }
            row.put(field, applyTransform(row.get(field), rule));
        }

        for (CollectModelField field : fields) {
            Object value = row.get(field.getFieldCode());
            if (value == null && StrUtil.isNotBlank(field.getDefaultValue())) {
                value = field.getDefaultValue();
            }
            row.put(field.getFieldCode(), convertValue(value, field));
        }
        return row;
    }

    private Object applyTransform(Object value, Map<String, Object> rule) {
        String type = stringValue(rule.get("type")).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "default" -> value == null || StrUtil.isBlank(stringValue(value)) ? rule.get("value") : value;
            case "trim" -> value == null ? null : stringValue(value).trim();
            case "upper" -> value == null ? null : stringValue(value).toUpperCase(Locale.ROOT);
            case "lower" -> value == null ? null : stringValue(value).toLowerCase(Locale.ROOT);
            case "number" -> value == null || StrUtil.isBlank(stringValue(value)) ? null : Long.valueOf(stringValue(value));
            case "decimal" -> value == null || StrUtil.isBlank(stringValue(value)) ? null : new BigDecimal(stringValue(value));
            case "boolean" -> booleanValue(value);
            case "dict" -> objectMap(rule.get("map")).getOrDefault(stringValue(value), value);
            default -> value;
        };
    }

    private Object convertValue(Object value, CollectModelField field) {
        if (value == null || StrUtil.isBlank(stringValue(value))) {
            return null;
        }
        String text = stringValue(value);
        return switch (StrUtil.blankToDefault(field.getFieldType(), "string")) {
            case "number" -> value instanceof Number number ? number.longValue() : Long.valueOf(text);
            case "decimal" -> value instanceof BigDecimal ? value : new BigDecimal(text);
            case "date" -> value instanceof LocalDate ? value : LocalDate.parse(text.substring(0, Math.min(10, text.length())));
            case "datetime" -> value instanceof LocalDateTime ? value : LocalDateTime.parse(text.replace(" ", "T"));
            case "boolean" -> booleanValue(value);
            case "json" -> value instanceof String ? value : toJson(value);
            default -> {
                int maxLength = field.getLengthLimit() == null ? 255 : field.getLengthLimit();
                yield StrUtil.maxLength(text, maxLength);
            }
        };
    }

    private void validateRow(Map<String, Object> row, List<CollectModelField> fields) {
        for (CollectModelField field : fields) {
            Object value = row.get(field.getFieldCode());
            if (Integer.valueOf(1).equals(field.getRequiredFlag())) {
                Assert.isTrue(value != null && StrUtil.isNotBlank(stringValue(value)), field.getFieldName() + "不能为空");
            }
        }
    }

    private void ensureTargetTable(CollectModel model, List<CollectModelField> fields) {
        String tableName = targetTableName(model);
        jdbcTemplate.execute(buildCreateTableDdl(tableName, fields));
        for (CollectModelField field : fields) {
            if (!columnExists(tableName, field.getFieldCode())) {
                jdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN " + columnDdl(field));
            }
        }
    }

    private String buildCreateTableDdl(String tableName, List<CollectModelField> fields) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quoteIdentifier(tableName)).append(" (\n");
        ddl.append("  `id` BIGINT NOT NULL AUTO_INCREMENT,\n");
        for (CollectModelField field : fields) {
            ddl.append("  ").append(columnDdl(field)).append(",\n");
        }
        ddl.append("  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,\n");
        ddl.append("  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n");
        ddl.append("  PRIMARY KEY (`id`)");
        List<String> uniqueFields = fields.stream()
                .filter(field -> Integer.valueOf(1).equals(field.getUniqueFlag()))
                .map(CollectModelField::getFieldCode)
                .toList();
        if (!uniqueFields.isEmpty()) {
            ddl.append(",\n  UNIQUE KEY `uk_").append(tableName).append("_biz` (")
                    .append(uniqueFields.stream().map(this::quoteIdentifier).collect(Collectors.joining(",")))
                    .append(")");
        }
        ddl.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return ddl.toString();
    }

    private String columnDdl(CollectModelField field) {
        String column = quoteIdentifier(field.getFieldCode()) + " " + toMysqlType(field);
        if (Integer.valueOf(1).equals(field.getRequiredFlag())) {
            column += " NOT NULL";
        }
        if (StrUtil.isNotBlank(field.getFieldName())) {
            column += " COMMENT '" + field.getFieldName().replace("'", "''") + "'";
        }
        return column;
    }

    private WriteResult writeTarget(CollectModel model, List<CollectModelField> fields, Map<String, Object> row, String strategy) {
        String tableName = quoteIdentifier(targetTableName(model));
        List<String> columns = fields.stream().map(CollectModelField::getFieldCode).toList();
        String columnSql = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(","));
        String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(","));
        List<Object> values = columns.stream().map(row::get).collect(Collectors.toList());
        String normalizedStrategy = StrUtil.blankToDefault(strategy, "insert");

        if ("ignore".equals(normalizedStrategy)) {
            int affected = jdbcTemplate.update("INSERT IGNORE INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholders + ")", values.toArray());
            return affected == 0 ? new WriteResult(0, 0, 1) : new WriteResult(1, 0, 0);
        }
        if ("upsert".equals(normalizedStrategy)) {
            String updateSql = columns.stream()
                    .filter(column -> !isUniqueField(column, fields))
                    .map(column -> quoteIdentifier(column) + "=VALUES(" + quoteIdentifier(column) + ")")
                    .collect(Collectors.joining(","));
            if (StrUtil.isBlank(updateSql)) {
                updateSql = "`update_time`=CURRENT_TIMESTAMP";
            }
            int affected = jdbcTemplate.update("INSERT INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholders + ") ON DUPLICATE KEY UPDATE " + updateSql, values.toArray());
            return affected > 1 ? new WriteResult(0, 1, 0) : new WriteResult(1, 0, 0);
        }

        jdbcTemplate.update("INSERT INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholders + ")", values.toArray());
        return new WriteResult(1, 0, 0);
    }

    private void saveQuality(CollectTask task, CollectInstance instance, EtlStats stats, Map<String, Integer> nonBlankByField) {
        CollectQualityReport report = new CollectQualityReport();
        report.setTaskId(task.getId());
        report.setInstanceId(instance.getId());
        report.setTraceId(instance.getTraceId());
        report.setTotalCount(stats.totalCount);
        report.setValidCount(stats.validCount);
        report.setInvalidCount(stats.invalidCount);
        report.setDuplicateCount(stats.duplicateCount);
        report.setInsertedCount(stats.insertedCount);
        report.setUpdatedCount(stats.updatedCount);
        report.setFailedCount(stats.failedCount);
        Map<String, Object> completeness = new LinkedHashMap<>();
        nonBlankByField.forEach((field, count) -> completeness.put(field, stats.totalCount == 0 ? 0 : BigDecimal.valueOf(count * 100.0 / stats.totalCount).setScale(2, RoundingMode.HALF_UP)));
        report.setFieldCompletenessJson(toJson(completeness));
        report.setSummaryJson(toJson(Map.of("finishedAt", LocalDateTime.now().toString())));
        qualityReportMapper.insert(report);
    }

    private CollectInstance finishInstance(Long instanceId, EtlStats stats, String status, String errorMessage) {
        CollectInstance update = new CollectInstance();
        update.setId(instanceId);
        update.setStatus(status);
        update.setEndTime(LocalDateTime.now());
        update.setTotalCount(stats.totalCount);
        update.setValidCount(stats.validCount);
        update.setInvalidCount(stats.invalidCount);
        update.setDuplicateCount(stats.duplicateCount);
        update.setInsertedCount(stats.insertedCount);
        update.setUpdatedCount(stats.updatedCount);
        update.setFailedCount(stats.failedCount);
        update.setErrorMessage(StrUtil.maxLength(errorMessage, 1000));
        instanceMapper.updateById(update);
        return instanceMapper.selectById(instanceId);
    }

    private void finishMessage(Long messageId, String consumeStatus, String errorMessage) {
        CollectTaskMessage update = new CollectTaskMessage();
        update.setId(messageId);
        update.setConsumeStatus(consumeStatus);
        update.setFinishTime(LocalDateTime.now());
        update.setErrorMessage(StrUtil.maxLength(errorMessage, 1000));
        messageMapper.updateById(update);
    }

    private void updateTaskCursor(CollectTask task) {
        CollectTask update = new CollectTask();
        update.setId(task.getId());
        update.setLastSuccessTime(LocalDateTime.now());
        update.setLastCursor(LocalDateTime.now().toString());
        taskMapper.updateById(update);
    }

    private List<MappingRule> parseMappings(String mappingJson, List<CollectModelField> fields) {
        if (StrUtil.isBlank(mappingJson)) {
            return fields.stream()
                    .map(field -> new MappingRule(field.getFieldCode(), field.getFieldCode(), field.getDefaultValue()))
                    .toList();
        }
        Object parsed = parseObject(mappingJson);
        if (parsed instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> new MappingRule(stringValue(entry.getValue()), stringValue(entry.getKey()), null))
                    .toList();
        }
        List<Map<String, Object>> list = objectMapper.convertValue(parsed, LIST_MAP_TYPE);
        return list.stream()
                .map(item -> new MappingRule(
                        StrUtil.blankToDefault(stringValue(item.get("source")), stringValue(item.get("target"))),
                        StrUtil.blankToDefault(stringValue(item.get("target")), stringValue(item.get("source"))),
                        item.get("defaultValue")
                ))
                .filter(rule -> StrUtil.isNotBlank(rule.target()))
                .toList();
    }

    private Map<String, Object> parseMap(String json) {
        if (StrUtil.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 配置解析失败: " + ex.getMessage());
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        Object parsed = parseObject(json);
        if (parsed instanceof List<?>) {
            return objectMapper.convertValue(parsed, LIST_MAP_TYPE);
        }
        return new ArrayList<>();
    }

    private Object parseObject(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败: " + ex.getMessage());
        }
    }

    private Object resolveRoot(Object payload, Object rootPath) {
        String path = stringValue(rootPath);
        if (StrUtil.isBlank(path)) {
            return payload;
        }
        return getPath(payload, path);
    }

    private List<Map<String, Object>> normalizeRows(Object payload, Integer maxFetchCount) {
        Object actual = payload;
        if (actual instanceof Map<?, ?> map && map.containsKey("list")) {
            actual = map.get("list");
        }
        List<Map<String, Object>> rows;
        if (actual instanceof List<?>) {
            rows = objectMapper.convertValue(actual, LIST_MAP_TYPE);
        } else if (actual instanceof Map<?, ?>) {
            rows = List.of(objectMapper.convertValue(actual, MAP_TYPE));
        } else {
            rows = new ArrayList<>();
        }
        return rows.stream().limit(maxFetchCount == null ? 1000 : maxFetchCount).toList();
    }

    private Object getPath(Object source, String path) {
        if (source == null || StrUtil.isBlank(path)) {
            return source;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private boolean isUniqueField(String column, List<CollectModelField> fields) {
        return fields.stream().anyMatch(field -> field.getFieldCode().equals(column) && Integer.valueOf(1).equals(field.getUniqueFlag()));
    }

    private String uniqueKey(Map<String, Object> row, List<CollectModelField> fields) {
        List<String> values = fields.stream()
                .filter(field -> Integer.valueOf(1).equals(field.getUniqueFlag()))
                .map(field -> stringValue(row.get(field.getFieldCode())))
                .toList();
        return values.isEmpty() ? "" : String.join("::", values);
    }

    private String targetTableName(CollectModel model) {
        String tableName = StrUtil.blankToDefault(model.getTargetTableName(), "collect_target_" + model.getModelCode());
        assertIdentifier(tableName, "目标表名不合法");
        return tableName;
    }

    private String quoteIdentifier(String identifier) {
        assertIdentifier(identifier, "SQL 标识符不合法: " + identifier);
        return "`" + identifier + "`";
    }

    private void assertIdentifier(String identifier, String message) {
        Assert.isTrue(StrUtil.isNotBlank(identifier) && identifier.matches("[A-Za-z_][A-Za-z0-9_]*"), message);
    }

    private String toMysqlType(CollectModelField field) {
        String type = StrUtil.blankToDefault(field.getFieldType(), "string");
        return switch (type) {
            case "number" -> "BIGINT";
            case "decimal" -> "DECIMAL(18,4)";
            case "date" -> "DATE";
            case "datetime" -> "DATETIME";
            case "boolean" -> "TINYINT(1)";
            case "json" -> "JSON";
            default -> "VARCHAR(" + (field.getLengthLimit() == null ? 255 : field.getLengthLimit()) + ")";
        };
    }

    private Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private List<Object> objectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private Long longValue(Object value) {
        if (value == null || StrUtil.isBlank(stringValue(value))) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(stringValue(value));
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value).toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private String requiredString(Map<String, Object> config, String key, String message) {
        String value = stringValue(config.get(key));
        Assert.isTrue(StrUtil.isNotBlank(value), message);
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int maxFetchCount(CollectTask task) {
        return task.getMaxFetchCount() == null ? 1000 : task.getMaxFetchCount();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private record MappingRule(String source, String target, Object defaultValue) {
    }

    private record WriteResult(int inserted, int updated, int duplicated) {
    }

    private static class EtlStats {
        int totalCount;
        int validCount;
        int invalidCount;
        int duplicateCount;
        int insertedCount;
        int updatedCount;
        int failedCount;
    }
}
