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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
    private final CollectModelRuleMapper modelRuleMapper;
    private final CollectApiMapper apiMapper;
    private final CollectDataSourceMapper dataSourceMapper;
    private final CollectTaskMapper taskMapper;
    private final CollectInstanceMapper instanceMapper;
    private final CollectTaskMessageMapper messageMapper;
    private final CollectRawDataMapper rawDataMapper;
    private final CollectErrorDataMapper errorDataMapper;
    private final CollectQualityReportMapper qualityReportMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 注入 ETL 执行所需的配置表、日志表、JDBC 和 JSON 处理组件。
     */
    public CollectEtlService(
            CollectModelMapper modelMapper,
            CollectModelFieldMapper modelFieldMapper,
            CollectModelRuleMapper modelRuleMapper,
            CollectApiMapper apiMapper,
            CollectDataSourceMapper dataSourceMapper,
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
        this.modelRuleMapper = modelRuleMapper;
        this.apiMapper = apiMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.messageMapper = messageMapper;
        this.rawDataMapper = rawDataMapper;
        this.errorDataMapper = errorDataMapper;
        this.qualityReportMapper = qualityReportMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次采集 ETL：拉取源数据、转换校验、写入目标表并更新实例状态。
     */
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
            ExecutionConfig executionConfig = resolveExecutionConfig(task);
            CollectModel model = executionConfig.model();
            CollectApi api = executionConfig.api();
            List<CollectModelField> fields = listModelFields(model.getId());
            List<Map<String, Object>> rawRows = fetchRows(api, task);
            JdbcTemplate targetJdbcTemplate = targetJdbcTemplate(model);
            stats.totalCount = rawRows.size();

            ensureTargetTable(targetJdbcTemplate, model, fields);
            if ("overwrite".equals(task.getInsertStrategy())) {
                targetJdbcTemplate.execute("TRUNCATE TABLE " + quoteIdentifier(targetTableName(model)));
            }

            List<MappingRule> mappings = parseMappings(executionConfig.mappingJson(), fields);
            List<Map<String, Object>> transforms = parseList(executionConfig.transformJson());
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

                    WriteResult writeResult = writeTarget(targetJdbcTemplate, model, fields, transformed, task.getInsertStrategy());
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

    /**
     * 解析任务执行配置；任务必须通过接入规则绑定采集接口、模型、映射和转换。
     */
    private ExecutionConfig resolveExecutionConfig(CollectTask task) {
        Assert.notNull(task.getRuleId(), "接入规则不能为空");
        CollectModelRule rule = modelRuleMapper.selectById(task.getRuleId());
        Assert.notNull(rule, "模型接入规则不存在");
        Assert.isTrue(!"disabled".equals(rule.getStatus()), "模型接入规则已停用");
        return new ExecutionConfig(requireModel(rule.getModelId()), requireApi(rule.getApiId()), rule.getMappingJson(), rule.getTransformJson());
    }

    /**
     * 查询并校验采集模型可用性。
     */
    private CollectModel requireModel(Long modelId) {
        CollectModel model = modelMapper.selectById(modelId);
        Assert.notNull(model, "采集模型不存在");
        Assert.isTrue(!"disabled".equals(model.getStatus()), "采集模型已停用");
        return model;
    }

    /**
     * 查询并校验采集接口可用性。
     */
    private CollectApi requireApi(Long apiId) {
        CollectApi api = apiMapper.selectById(apiId);
        Assert.notNull(api, "采集接口不存在");
        Assert.isTrue(!"disabled".equals(api.getStatus()), "采集接口已停用");
        return api;
    }

    /**
     * 根据模型配置创建目标数据源 JDBC 模板，未配置时使用默认数据源。
     */
    private JdbcTemplate targetJdbcTemplate(CollectModel model) {
        CollectDataSource targetSource = model.getTargetDataSourceId() == null ? null : dataSourceMapper.selectById(model.getTargetDataSourceId());
        if (targetSource == null) {
            return jdbcTemplate;
        }
        Assert.isTrue(!"disabled".equals(targetSource.getStatus()), "目标数据源已停用");
        Assert.isTrue("db".equals(sourceType(targetSource)), "目标表只能写入 DB 类型数据源");
        if (StrUtil.isNotBlank(targetSource.getDriverClass())) {
            try {
                Class.forName(targetSource.getDriverClass());
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("目标数据源驱动不存在: " + targetSource.getDriverClass(), ex);
            }
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(targetSource.getJdbcUrl());
        dataSource.setUsername(targetSource.getUsername());
        dataSource.setPassword(targetSource.getPasswordEncrypt());
        if (StrUtil.isNotBlank(targetSource.getDriverClass())) {
            dataSource.setDriverClassName(targetSource.getDriverClass());
        }
        return new JdbcTemplate(dataSource);
    }

    /**
     * 查询模型字段，并按排序字段稳定排列。
     */
    private List<CollectModelField> listModelFields(Long modelId) {
        List<CollectModelField> fields = modelFieldMapper.selectList(new LambdaQueryWrapper<CollectModelField>()
                .eq(CollectModelField::getModelId, modelId)
                .orderByAsc(CollectModelField::getSort)
                .orderByAsc(CollectModelField::getId));
        Assert.isTrue(!fields.isEmpty(), "模型字段为空");
        return fields;
    }

    /**
     * 按采集方式分发到 HTTP、DB 或消息配置采集实现。
     */
    private List<Map<String, Object>> fetchRows(CollectApi api, CollectTask task) throws Exception {
        return switch (StrUtil.blankToDefault(api.getCollectType(), "").toLowerCase(Locale.ROOT)) {
            case "http" -> fetchHttp(api, task);
            case "db" -> fetchDb(api, task);
            case "mq" -> fetchConfiguredMessages(api, task);
            default -> throw new IllegalArgumentException("不支持的采集方式: " + api.getCollectType());
        };
    }

    /**
     * 执行 HTTP 采集，按分页配置决定单次请求或分页请求。
     */
    private List<Map<String, Object>> fetchHttp(CollectApi api, CollectTask task) throws Exception {
        Map<String, Object> config = parseMap(api.getConfigJson());
        Map<String, Object> pagination = objectMap(config.get("pagination"));
        if (booleanValue(pagination.get("enabled"))) {
            return fetchHttpPages(api, task, config, pagination);
        }
        return fetchHttpOnce(api, task, config, objectMap(config.get("params")));
    }

    /**
     * 按分页配置循环请求 HTTP 接口并合并结果。
     */
    private List<Map<String, Object>> fetchHttpPages(CollectApi api, CollectTask task, Map<String, Object> config, Map<String, Object> pagination) throws Exception {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int pageNo = intValue(pagination.getOrDefault("startPage", 1), 1);
        int pageSize = intValue(pagination.getOrDefault("pageSize", 100), 100);
        int maxPages = intValue(pagination.getOrDefault("maxPages", 100), 100);
        String pageParam = StrUtil.blankToDefault(stringValue(pagination.get("pageParam")), "pageNo");
        String sizeParam = StrUtil.blankToDefault(stringValue(pagination.get("sizeParam")), "pageSize");
        String hasNextPath = stringValue(pagination.get("hasNextPath"));
        String totalPath = stringValue(pagination.get("totalPath"));
        int maxFetchCount = maxFetchCount(task);

        for (int pageIndex = 0; pageIndex < maxPages && allRows.size() < maxFetchCount; pageIndex++) {
            Map<String, Object> params = objectMap(config.get("params"));
            params.put(pageParam, pageNo);
            params.put(sizeParam, pageSize);
            HttpFetchResult result = fetchHttpPayload(api, config, params);
            List<Map<String, Object>> rows = normalizeRows(resolveRoot(result.payload(), parseMap(api.getParseConfig()).get("rootPath")), maxFetchCount - allRows.size());
            if (rows.isEmpty()) {
                break;
            }
            allRows.addAll(rows);
            Object hasNext = StrUtil.isBlank(hasNextPath) ? null : getPath(result.payload(), hasNextPath);
            if (hasNext != null && !booleanValue(hasNext)) {
                break;
            }
            Long total = StrUtil.isBlank(totalPath) ? null : longValue(getPath(result.payload(), totalPath));
            if (total != null && allRows.size() >= total) {
                break;
            }
            if (rows.size() < pageSize && hasNext == null && total == null) {
                break;
            }
            pageNo++;
        }
        return allRows;
    }

    /**
     * 执行一次 HTTP 请求并按 rootPath 解析数据列表。
     */
    private List<Map<String, Object>> fetchHttpOnce(CollectApi api, CollectTask task, Map<String, Object> config, Map<String, Object> params) throws Exception {
        HttpFetchResult result = fetchHttpPayload(api, config, params);
        return normalizeRows(resolveRoot(result.payload(), parseMap(api.getParseConfig()).get("rootPath")), task.getMaxFetchCount());
    }

    /**
     * 构造并发送 HTTP 请求，返回原始响应对象。
     */
    private HttpFetchResult fetchHttpPayload(CollectApi api, Map<String, Object> config, Map<String, Object> params) throws Exception {
        String url = resolveHttpUrl(api, config);
        String method = stringValue(config.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
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
        return new HttpFetchResult(payload);
    }

    /**
     * 解析 HTTP 请求地址：优先使用接口配置 url，其次使用数据源 baseUrl + path。
     */
    private String resolveHttpUrl(CollectApi api, Map<String, Object> config) {
        String url = stringValue(config.get("url"));
        String path = stringValue(config.get("path"));
        if (api.getSourceDataSourceId() == null) {
            return StrUtil.isNotBlank(url) ? url : requiredString(config, "path", "HTTP URL 或 path 不能为空");
        }
        CollectDataSource source = dataSourceMapper.selectById(api.getSourceDataSourceId());
        Assert.notNull(source, "HTTP 来源数据源不存在");
        Assert.isTrue("http".equals(sourceType(source)), "HTTP 采集接口必须选择 HTTP 类型数据源");
        if (StrUtil.isNotBlank(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
            return url;
        }
        String baseUrl = source.getBaseUrl();
        Assert.isTrue(StrUtil.isNotBlank(baseUrl), "HTTP 数据源 Base URL 不能为空");
        baseUrl = StrUtil.removeSuffix(baseUrl, "/");
        String actualPath = StrUtil.blankToDefault(StrUtil.isNotBlank(url) ? url : path, "");
        Assert.isTrue(StrUtil.isNotBlank(actualPath), "HTTP 接口 path 不能为空");
        return baseUrl + "/" + StrUtil.removePrefix(actualPath, "/");
    }

    /**
     * 执行 DB SQL 采集，并将结果集转成字段 Map 列表。
     */
    private List<Map<String, Object>> fetchDb(CollectApi api, CollectTask task) throws Exception {
        Map<String, Object> config = parseMap(api.getConfigJson());
        CollectDataSource source = api.getSourceDataSourceId() == null ? null : dataSourceMapper.selectById(api.getSourceDataSourceId());
        Assert.notNull(source, "DB 采集接口必须选择来源数据源");
        Assert.isTrue("db".equals(sourceType(source)), "DB 采集接口必须选择 DB 类型数据源");
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

    /**
     * 从接口配置中读取模拟消息数据，用于 MQ 场景的配置化演示。
     */
    private List<Map<String, Object>> fetchConfiguredMessages(CollectApi api, CollectTask task) {
        if (api.getSourceDataSourceId() != null) {
            CollectDataSource source = dataSourceMapper.selectById(api.getSourceDataSourceId());
            Assert.notNull(source, "MQ 来源数据源不存在");
            Assert.isTrue("mq".equals(sourceType(source)), "MQ 采集接口必须选择 MQ 类型数据源");
        }
        Map<String, Object> config = parseMap(api.getConfigJson());
        Object messages = config.getOrDefault("messages", config.get("message"));
        return normalizeRows(messages == null ? List.of() : messages, task.getMaxFetchCount());
    }

    /**
     * 保存一条原始采集数据记录。
     */
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

    /**
     * 更新原始数据处理状态。
     */
    private void markRaw(Long id, String status, String errorMessage) {
        CollectRawData raw = new CollectRawData();
        raw.setId(id);
        raw.setStatus(status);
        raw.setErrorMessage(errorMessage);
        rawDataMapper.updateById(raw);
    }

    /**
     * 保存一条异常数据明细。
     */
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

    /**
     * 根据字段映射、转换规则和模型字段定义生成目标行数据。
     */
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

    /**
     * 对单个字段值应用转换规则。
     */
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

    /**
     * 按模型字段类型转换目标字段值。
     */
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

    /**
     * 校验目标行中模型必填字段是否有值。
     */
    private void validateRow(Map<String, Object> row, List<CollectModelField> fields) {
        for (CollectModelField field : fields) {
            Object value = row.get(field.getFieldCode());
            if (Integer.valueOf(1).equals(field.getRequiredFlag())) {
                Assert.isTrue(value != null && StrUtil.isNotBlank(stringValue(value)), field.getFieldName() + "不能为空");
            }
        }
    }

    /**
     * 确保目标表存在，并补齐模型中新增的字段列。
     */
    private void ensureTargetTable(JdbcTemplate targetJdbcTemplate, CollectModel model, List<CollectModelField> fields) {
        String tableName = targetTableName(model);
        targetJdbcTemplate.execute(buildCreateTableDdl(tableName, fields));
        for (CollectModelField field : fields) {
            if (!columnExists(targetJdbcTemplate, tableName, field.getFieldCode())) {
                targetJdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN " + columnDdl(field));
            }
        }
    }

    /**
     * 根据模型字段构造目标表建表语句。
     */
    private String buildCreateTableDdl(String tableName, List<CollectModelField> fields) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quoteIdentifier(tableName)).append(" (\n");
        boolean hasIdField = fields.stream().anyMatch(field -> "id".equals(field.getFieldCode()));
        if (!hasIdField) {
            ddl.append("  `id` BIGINT NOT NULL AUTO_INCREMENT,\n");
        }
        for (CollectModelField field : fields) {
            ddl.append("  ").append(columnDdl(field)).append(",\n");
        }
        ddl.append("  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,\n");
        ddl.append("  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n");
        ddl.append("  PRIMARY KEY (`id`)");
        List<String> uniqueFields = fields.stream()
                .filter(field -> Integer.valueOf(1).equals(field.getUniqueFlag()))
                .filter(field -> !hasIdField || !"id".equals(field.getFieldCode()))
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

    /**
     * 构造单个模型字段对应的列定义。
     */
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

    /**
     * 按任务入库策略写入目标表，并返回新增、更新或重复统计。
     */
    private WriteResult writeTarget(JdbcTemplate targetJdbcTemplate, CollectModel model, List<CollectModelField> fields, Map<String, Object> row, String strategy) {
        String tableName = quoteIdentifier(targetTableName(model));
        List<String> columns = fields.stream().map(CollectModelField::getFieldCode).toList();
        String columnSql = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(","));
        String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(","));
        List<Object> values = columns.stream().map(row::get).collect(Collectors.toList());
        String normalizedStrategy = StrUtil.blankToDefault(strategy, "insert");

        if ("ignore".equals(normalizedStrategy)) {
            int affected = targetJdbcTemplate.update("INSERT IGNORE INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholders + ")", values.toArray());
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
            int affected = targetJdbcTemplate.update("INSERT INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholders + ") ON DUPLICATE KEY UPDATE " + updateSql, values.toArray());
            return affected > 1 ? new WriteResult(0, 1, 0) : new WriteResult(1, 0, 0);
        }

        targetJdbcTemplate.update("INSERT INTO " + tableName + " (" + columnSql + ") VALUES (" + placeholders + ")", values.toArray());
        return new WriteResult(1, 0, 0);
    }

    /**
     * 保存本次执行的数据质量报告。
     */
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

    /**
     * 完成执行实例并回写统计结果。
     */
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

    /**
     * 完成内部消息消费并记录消费状态。
     */
    private void finishMessage(Long messageId, String consumeStatus, String errorMessage) {
        CollectTaskMessage update = new CollectTaskMessage();
        update.setId(messageId);
        update.setConsumeStatus(consumeStatus);
        update.setFinishTime(LocalDateTime.now());
        update.setErrorMessage(StrUtil.maxLength(errorMessage, 1000));
        messageMapper.updateById(update);
    }

    /**
     * 更新任务最近成功时间和游标。
     */
    private void updateTaskCursor(CollectTask task) {
        CollectTask update = new CollectTask();
        update.setId(task.getId());
        update.setLastSuccessTime(LocalDateTime.now());
        update.setLastCursor(LocalDateTime.now().toString());
        taskMapper.updateById(update);
    }

    /**
     * 解析字段映射配置；未配置时默认字段同名映射。
     */
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

    /**
     * 将 JSON 对象字符串解析为 Map。
     */
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

    /**
     * 将 JSON 数组字符串解析为 Map 列表。
     */
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

    /**
     * 将 JSON 字符串解析为通用对象。
     */
    private Object parseObject(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败: " + ex.getMessage());
        }
    }

    /**
     * 根据 rootPath 从响应体中定位实际数据节点。
     */
    private Object resolveRoot(Object payload, Object rootPath) {
        String path = stringValue(rootPath);
        if (StrUtil.isBlank(path)) {
            return payload;
        }
        return getPath(payload, path);
    }

    /**
     * 将任意响应节点规范化为行数据列表。
     */
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

    /**
     * 按点号路径从 Map/List 嵌套结构中取值。
     */
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

    /**
     * 判断目标表中指定列是否已存在。
     */
    private boolean columnExists(JdbcTemplate targetJdbcTemplate, String tableName, String columnName) {
        Integer count = targetJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    /**
     * 判断字段是否为模型唯一键字段。
     */
    private boolean isUniqueField(String column, List<CollectModelField> fields) {
        return fields.stream().anyMatch(field -> field.getFieldCode().equals(column) && Integer.valueOf(1).equals(field.getUniqueFlag()));
    }

    /**
     * 根据唯一键字段拼接批次内去重键。
     */
    private String uniqueKey(Map<String, Object> row, List<CollectModelField> fields) {
        List<String> values = fields.stream()
                .filter(field -> Integer.valueOf(1).equals(field.getUniqueFlag()))
                .map(field -> stringValue(row.get(field.getFieldCode())))
                .toList();
        return values.isEmpty() ? "" : String.join("::", values);
    }

    /**
     * 解析并校验模型目标表名。
     */
    private String targetTableName(CollectModel model) {
        String tableName = StrUtil.blankToDefault(model.getTargetTableName(), "collect_target_" + model.getModelCode());
        assertIdentifier(tableName, "目标表名不合法");
        return tableName;
    }

    /**
     * 校验并引用 SQL 标识符。
     */
    private String quoteIdentifier(String identifier) {
        assertIdentifier(identifier, "SQL 标识符不合法: " + identifier);
        return "`" + identifier + "`";
    }

    /**
     * 校验 SQL 标识符格式，避免拼接非法表名或字段名。
     */
    private void assertIdentifier(String identifier, String message) {
        Assert.isTrue(StrUtil.isNotBlank(identifier) && identifier.matches("[A-Za-z_][A-Za-z0-9_]*"), message);
    }

    /**
     * 将模型字段类型转换为 MySQL 字段类型。
     */
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

    /**
     * 将对象转换为 Map，空值返回空 Map。
     */
    private Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    /**
     * 将对象转换为列表，非列表时返回空列表。
     */
    private List<Object> objectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    /**
     * 将对象转换为 Long，空值返回 null。
     */
    private Long longValue(Object value) {
        if (value == null || StrUtil.isBlank(stringValue(value))) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(stringValue(value));
    }

    /**
     * 将对象转换为布尔值。
     */
    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value).toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    /**
     * 将对象转换为 int，空值使用默认值。
     */
    private int intValue(Object value, int defaultValue) {
        if (value == null || StrUtil.isBlank(stringValue(value))) {
            return defaultValue;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(stringValue(value));
    }

    /**
     * 读取必填字符串配置项。
     */
    private String requiredString(Map<String, Object> config, String key, String message) {
        String value = stringValue(config.get(key));
        Assert.isTrue(StrUtil.isNotBlank(value), message);
        return value;
    }

    /**
     * 安全转换字符串。
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 读取数据源类型，缺省按 DB 处理。
     */
    private String sourceType(CollectDataSource source) {
        return StrUtil.blankToDefault(source.getSourceType(), "db").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 获取任务最大采集量，未配置时使用默认值。
     */
    private int maxFetchCount(CollectTask task) {
        return task.getMaxFetchCount() == null ? 1000 : task.getMaxFetchCount();
    }

    /**
     * 将对象序列化为 JSON，失败时回退为字符串。
     */
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

    private record HttpFetchResult(Object payload) {
    }

    private record WriteResult(int inserted, int updated, int duplicated) {
    }

    private record ExecutionConfig(CollectModel model, CollectApi api, String mappingJson, String transformJson) {
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
