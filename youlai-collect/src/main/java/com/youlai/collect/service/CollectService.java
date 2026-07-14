package com.youlai.collect.service;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.collect.mapper.*;
import com.youlai.collect.model.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CollectService {

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
    private final CollectDataSourceMigrationRunner migrationRunner;
    private final CollectEtlService collectEtlService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 注入采集配置、执行记录和 ETL 执行相关依赖。
     */
    public CollectService(
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
            CollectDataSourceMigrationRunner migrationRunner,
            CollectEtlService collectEtlService,
            TransactionTemplate transactionTemplate
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
        this.migrationRunner = migrationRunner;
        this.collectEtlService = collectEtlService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 汇总采集模型、接口、任务和执行实例的核心统计指标。
     */
    public CollectDashboard dashboard() {
        return new CollectDashboard(
                modelMapper.selectCount(null),
                apiMapper.selectCount(null),
                taskMapper.selectCount(null),
                instanceMapper.selectCount(new LambdaQueryWrapper<CollectInstance>().eq(CollectInstance::getStatus, "running")),
                instanceMapper.selectCount(new LambdaQueryWrapper<CollectInstance>().eq(CollectInstance::getStatus, "success")),
                instanceMapper.selectCount(new LambdaQueryWrapper<CollectInstance>().eq(CollectInstance::getStatus, "failed"))
        );
    }

    /**
     * 按关键字和状态分页查询采集模型。
     */
    public Page<CollectModel> pageModels(int pageNum, int pageSize, String keywords, String status) {
        ensureCollectSchema();
        return modelMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectModel>()
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectModel::getModelName, keywords)
                        .or()
                        .like(CollectModel::getModelCode, keywords))
                .eq(StrUtil.isNotBlank(status), CollectModel::getStatus, status)
                .orderByDesc(CollectModel::getUpdateTime));
    }

    /**
     * 查询采集模型详情，并附带字段列表。
     */
    public CollectModelDetail getModel(Long id) {
        ensureCollectSchema();
        CollectModel model = modelMapper.selectById(id);
        Assert.notNull(model, "采集模型不存在");
        List<CollectModelField> fields = modelFieldMapper.selectList(new LambdaQueryWrapper<CollectModelField>()
                .eq(CollectModelField::getModelId, id)
                .orderByAsc(CollectModelField::getSort)
                .orderByAsc(CollectModelField::getId));
        return CollectModelDetail.of(model, fields);
    }

    /**
     * 保存采集模型主表和字段配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean saveModel(CollectModelRequest request) {
        ensureCollectSchema();
        validateModel(request);
        CollectModel model = new CollectModel();
        BeanUtils.copyProperties(request, model);
        model.setStatus(StrUtil.blankToDefault(model.getStatus(), "enabled"));
        model.setFieldCount(request.getFields() == null ? 0 : request.getFields().size());
        modelMapper.insert(model);
        saveModelFields(model.getId(), request.getFields());
        return true;
    }

    /**
     * 更新采集模型主表和字段配置，字段采用先删后插的快照式保存。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateModel(Long id, CollectModelRequest request) {
        ensureCollectSchema();
        CollectModel exists = modelMapper.selectById(id);
        Assert.notNull(exists, "采集模型不存在");
        request.setId(id);
        validateModel(request);
        CollectModel model = new CollectModel();
        BeanUtils.copyProperties(request, model);
        model.setId(id);
        model.setFieldCount(request.getFields() == null ? 0 : request.getFields().size());
        modelMapper.updateById(model);
        modelMapper.update(null, new LambdaUpdateWrapper<CollectModel>()
                .eq(CollectModel::getId, id)
                .set(CollectModel::getTargetDataSourceId, request.getTargetDataSourceId()));
        modelFieldMapper.delete(new LambdaQueryWrapper<CollectModelField>().eq(CollectModelField::getModelId, id));
        saveModelFields(id, request.getFields());
        return true;
    }

    /**
     * 更新采集模型启停状态。
     */
    public boolean updateModelStatus(Long id, String status) {
        ensureCollectSchema();
        CollectModel model = new CollectModel();
        model.setId(id);
        model.setStatus(status);
        return modelMapper.updateById(model) > 0;
    }

    /**
     * 生成采集模型目标表的 MySQL 建表 SQL 预览。
     */
    public String previewModelTableDdl(Long id) {
        CollectModelDetail detail = getModel(id);
        String tableName = StrUtil.blankToDefault(detail.getTargetTableName(), "collect_target_" + detail.getModelCode());
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
        ddl.append("  id BIGINT NOT NULL AUTO_INCREMENT,\n");
        for (CollectModelField field : detail.getFields()) {
            ddl.append("  ").append(field.getFieldCode()).append(" ").append(toMysqlType(field)).append(",");
            if (StrUtil.isNotBlank(field.getFieldName())) {
                ddl.append(" COMMENT '").append(field.getFieldName().replace("'", "''")).append("'");
            }
            ddl.append("\n");
        }
        ddl.append("  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,\n");
        ddl.append("  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n");
        ddl.append("  PRIMARY KEY (id)\n");
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return ddl.toString();
    }

    /**
     * 按关键字、采集方式和状态分页查询采集接口。
     */
    public Page<CollectApi> pageApis(int pageNum, int pageSize, String keywords, String collectType, String status) {
        ensureCollectSchema();
        return apiMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectApi>()
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectApi::getApiName, keywords)
                        .or()
                        .like(CollectApi::getApiCode, keywords))
                .eq(StrUtil.isNotBlank(collectType), CollectApi::getCollectType, collectType)
                .eq(StrUtil.isNotBlank(status), CollectApi::getStatus, status)
                .orderByDesc(CollectApi::getUpdateTime));
    }

    /**
     * 保存采集接口配置。
     */
    public boolean saveApi(CollectApi api) {
        ensureCollectSchema();
        assertUniqueApiCode(api);
        validateApi(api);
        api.setStatus(StrUtil.blankToDefault(api.getStatus(), "enabled"));
        return apiMapper.insert(api) > 0;
    }

    /**
     * 更新采集接口配置。
     */
    public boolean updateApi(Long id, CollectApi api) {
        ensureCollectSchema();
        api.setId(id);
        assertUniqueApiCode(api);
        validateApi(api);
        int updated = apiMapper.updateById(api);
        apiMapper.update(null, new LambdaUpdateWrapper<CollectApi>()
                .eq(CollectApi::getId, id)
                .set(CollectApi::getSourceDataSourceId, api.getSourceDataSourceId()));
        return updated > 0;
    }

    /**
     * 校验采集接口配置是否存在并返回测试结果。
     */
    public Map<String, Object> testApi(Long id) {
        ensureCollectSchema();
        CollectApi api = apiMapper.selectById(id);
        Assert.notNull(api, "采集接口不存在");
        return Map.of("success", true, "collectType", api.getCollectType(), "message", "接口配置校验通过，真实采集适配器待接入");
    }

    /**
     * 按模型、接口、关键字和状态分页查询模型接入规则。
     */
    public Page<CollectModelRule> pageModelRules(int pageNum, int pageSize, Long modelId, Long apiId, String keywords, String status) {
        ensureCollectSchema();
        return modelRuleMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectModelRule>()
                .eq(modelId != null, CollectModelRule::getModelId, modelId)
                .eq(apiId != null, CollectModelRule::getApiId, apiId)
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectModelRule::getRuleName, keywords)
                        .or()
                        .like(CollectModelRule::getRuleCode, keywords))
                .eq(StrUtil.isNotBlank(status), CollectModelRule::getStatus, status)
                .orderByDesc(CollectModelRule::getUpdateTime));
    }

    /**
     * 查询模型接入规则详情。
     */
    public CollectModelRule getModelRule(Long id) {
        ensureCollectSchema();
        CollectModelRule rule = modelRuleMapper.selectById(id);
        Assert.notNull(rule, "模型接入规则不存在");
        return rule;
    }

    /**
     * 保存模型接入规则。
     */
    public boolean saveModelRule(CollectModelRule rule) {
        ensureCollectSchema();
        validateModelRule(rule);
        assertUniqueRuleCode(rule);
        rule.setStatus(StrUtil.blankToDefault(rule.getStatus(), "enabled"));
        rule.setMappingJson(StrUtil.blankToDefault(rule.getMappingJson(), "[]"));
        rule.setTransformJson(StrUtil.blankToDefault(rule.getTransformJson(), "[]"));
        return modelRuleMapper.insert(rule) > 0;
    }

    /**
     * 更新模型接入规则。
     */
    public boolean updateModelRule(Long id, CollectModelRule rule) {
        ensureCollectSchema();
        Assert.notNull(modelRuleMapper.selectById(id), "模型接入规则不存在");
        rule.setId(id);
        validateModelRule(rule);
        assertUniqueRuleCode(rule);
        return modelRuleMapper.updateById(rule) > 0;
    }

    /**
     * 更新模型接入规则启停状态。
     */
    public boolean updateModelRuleStatus(Long id, String status) {
        ensureCollectSchema();
        CollectModelRule rule = new CollectModelRule();
        rule.setId(id);
        rule.setStatus(status);
        return modelRuleMapper.updateById(rule) > 0;
    }

    /**
     * 按关键字和状态分页查询 数据源配置。
     */
    public Page<CollectDataSource> pageDataSources(int pageNum, int pageSize, String keywords, String status) {
        ensureCollectSchema();
        return dataSourceMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectDataSource>()
                .like(StrUtil.isNotBlank(keywords), CollectDataSource::getSourceName, keywords)
                .eq(StrUtil.isNotBlank(status), CollectDataSource::getStatus, status)
                .orderByDesc(CollectDataSource::getUpdateTime));
    }

    /**
     * 保存 数据源配置，并补齐驱动类、校验 SQL 等默认值。
     */
    public boolean saveDataSource(CollectDataSource source) {
        ensureCollectSchema();
        normalizeDataSource(source);
        return dataSourceMapper.insert(source) > 0;
    }

    /**
     * 更新 数据源配置；传入数据库类型时同步补齐默认连接配置。
     */
    public boolean updateDataSource(Long id, CollectDataSource source) {
        ensureCollectSchema();
        source.setId(id);
        normalizeDataSource(source);
        return dataSourceMapper.updateById(source) > 0;
    }

    /**
     * 仅更新数据源状态，不改动连接配置。
     */
    public boolean updateDataSourceStatus(Long id, String status) {
        ensureCollectSchema();
        CollectDataSource source = new CollectDataSource();
        source.setId(id);
        source.setStatus(status);
        return dataSourceMapper.updateById(source) > 0;
    }

    /**
     * 规范化 数据源配置。
     */
    private void normalizeDataSource(CollectDataSource source) {
        source.setSourceType(normalizeSourceType(source.getSourceType()));
        source.setStatus(StrUtil.blankToDefault(source.getStatus(), "enabled"));
        if (!"db".equals(source.getSourceType())) {
            return;
        }
        String dbType = normalizeDbType(source.getDbType());
        source.setDbType(dbType);
        source.setDriverClass(StrUtil.blankToDefault(source.getDriverClass(), defaultDriverClass(dbType)));
        source.setValidationQuery(StrUtil.blankToDefault(source.getValidationQuery(), defaultValidationQuery(dbType)));
    }

    /**
     * 统一数据源类型。
     */
    private String normalizeSourceType(String sourceType) {
        String normalized = StrUtil.blankToDefault(sourceType, "db").trim().toLowerCase();
        return switch (normalized) {
            case "http", "https", "api" -> "http";
            case "mq", "kafka", "rocketmq", "rabbitmq", "pulsar" -> "mq";
            default -> "db";
        };
    }

    /**
     * 统一数据库类型别名。
     */
    private String normalizeDbType(String dbType) {
        String normalized = StrUtil.blankToDefault(dbType, "mysql").trim().toLowerCase();
        return switch (normalized) {
            case "postgres", "postgresql" -> "postgresql";
            case "clickhouse" -> "clickhouse";
            case "dm", "dameng" -> "dm";
            default -> "mysql";
        };
    }

    /**
     * 根据数据库类型返回默认 JDBC 驱动类。
     */
    private String defaultDriverClass(String dbType) {
        return switch (dbType) {
            case "postgresql" -> "org.postgresql.Driver";
            case "clickhouse" -> "com.clickhouse.jdbc.ClickHouseDriver";
            case "dm" -> "dm.jdbc.driver.DmDriver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    /**
     * 根据数据库类型返回默认连接校验 SQL。
     */
    private String defaultValidationQuery(String dbType) {
        return "dm".equals(dbType) ? "SELECT 1 FROM DUAL" : "SELECT 1";
    }

    /**
     * 测试 数据源连接，并记录最近测试时间和状态。
     */
    public Map<String, Object> testDataSource(Long id) {
        ensureCollectSchema();
        CollectDataSource source = dataSourceMapper.selectById(id);
        Assert.notNull(source, "数据源不存在");
        if (!"db".equals(normalizeSourceType(source.getSourceType()))) {
            return testNonDbDataSource(source);
        }
        boolean success = false;
        String message = "连接成功";
        try {
            if (StrUtil.isNotBlank(source.getDriverClass())) {
                Class.forName(source.getDriverClass());
            }
            try (Connection ignored = DriverManager.getConnection(source.getJdbcUrl(), source.getUsername(), source.getPasswordEncrypt())) {
                success = true;
            }
        } catch (Exception ex) {
            message = ex.getMessage();
        }
        CollectDataSource update = new CollectDataSource();
        update.setId(id);
        update.setLastTestTime(LocalDateTime.now());
        update.setLastTestStatus(success ? "success" : "failed");
        dataSourceMapper.updateById(update);
        return Map.of("success", success, "message", message);
    }

    /**
     * HTTP/MQ 数据源首期做配置完整性校验，真实探活由后续适配器接入。
     */
    private Map<String, Object> testNonDbDataSource(CollectDataSource source) {
        boolean success = true;
        String message = "配置校验通过";
        if ("http".equals(normalizeSourceType(source.getSourceType())) && StrUtil.isBlank(source.getBaseUrl())) {
            success = false;
            message = "HTTP 数据源 Base URL 不能为空";
        }
        if ("mq".equals(normalizeSourceType(source.getSourceType())) && StrUtil.isBlank(source.getConfigJson())) {
            success = false;
            message = "MQ 数据源连接配置不能为空";
        }
        CollectDataSource update = new CollectDataSource();
        update.setId(source.getId());
        update.setLastTestTime(LocalDateTime.now());
        update.setLastTestStatus(success ? "success" : "failed");
        dataSourceMapper.updateById(update);
        return Map.of("success", success, "message", message);
    }

    /**
     * 按关键字和状态分页查询采集任务。
     */
    public Page<CollectTask> pageTasks(int pageNum, int pageSize, String keywords, String status) {
        ensureCollectSchema();
        return taskMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectTask>()
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectTask::getTaskName, keywords)
                        .or()
                        .like(CollectTask::getTaskCode, keywords))
                .eq(StrUtil.isNotBlank(status), CollectTask::getStatus, status)
                .orderByDesc(CollectTask::getUpdateTime));
    }

    /**
     * 保存采集任务配置。
     */
    public boolean saveTask(CollectTask task) {
        ensureCollectSchema();
        assertUniqueTaskCode(task);
        prepareTaskRule(task);
        task.setStatus(StrUtil.blankToDefault(task.getStatus(), "draft"));
        return taskMapper.insert(task) > 0;
    }

    /**
     * 更新采集任务配置。
     */
    public boolean updateTask(Long id, CollectTask task) {
        ensureCollectSchema();
        task.setId(id);
        assertUniqueTaskCode(task);
        prepareTaskRule(task);
        int updated = taskMapper.updateById(task);
        taskMapper.update(null, new LambdaUpdateWrapper<CollectTask>()
                .eq(CollectTask::getId, id)
                .set(CollectTask::getRuleId, task.getRuleId())
                .set(CollectTask::getModelId, task.getModelId())
                .set(CollectTask::getApiId, task.getApiId())
                .set(CollectTask::getMappingJson, task.getMappingJson())
                .set(CollectTask::getTransformJson, task.getTransformJson()));
        return updated > 0;
    }

    /**
     * 更新采集任务启停状态。
     */
    public boolean updateTaskStatus(Long id, String status) {
        ensureCollectSchema();
        CollectTask task = new CollectTask();
        task.setId(id);
        task.setStatus(status);
        return taskMapper.updateById(task) > 0;
    }

    /**
     * 创建任务执行实例和内部消息，并立即执行 ETL。
     */
    public CollectInstance runTask(Long taskId, CollectRunRequest request) {
        ensureCollectSchema();
        DispatchResult dispatch = dispatchTaskMessage(taskId, request);
        return collectEtlService.execute(dispatch.task(), dispatch.instance(), dispatch.message());
    }

    /**
     * 创建任务执行实例和内部消息，等待消费者异步处理。
     */
    public CollectInstance dispatchTask(Long taskId, CollectRunRequest request) {
        ensureCollectSchema();
        return dispatchTaskMessage(taskId, request).instance();
    }

    /**
     * 基于历史执行实例重新发起一次任务执行。
     */
    public CollectInstance retryInstance(Long instanceId) {
        ensureCollectSchema();
        CollectInstance instance = instanceMapper.selectById(instanceId);
        Assert.notNull(instance, "执行实例不存在");
        CollectRunRequest request = new CollectRunRequest();
        request.setTriggerType("retry");
        return runTask(instance.getTaskId(), request);
    }

    /**
     * 批量认领并消费待处理的内部采集消息。
     */
    public int consumePendingMessages(int limit) {
        ensureCollectSchema();
        List<CollectTaskMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<CollectTaskMessage>()
                .eq(CollectTaskMessage::getSendStatus, "success")
                .eq(CollectTaskMessage::getConsumeStatus, "pending")
                .orderByAsc(CollectTaskMessage::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        int consumed = 0;
        for (CollectTaskMessage message : messages) {
            CollectTaskMessage claim = new CollectTaskMessage();
            claim.setConsumeStatus("running");
            int updated = messageMapper.update(claim, new LambdaQueryWrapper<CollectTaskMessage>()
                    .eq(CollectTaskMessage::getId, message.getId())
                    .eq(CollectTaskMessage::getConsumeStatus, "pending"));
            if (updated == 0) {
                continue;
            }
            CollectTask task = taskMapper.selectById(message.getTaskId());
            CollectInstance instance = instanceMapper.selectById(message.getInstanceId());
            if (task == null || instance == null) {
                CollectTaskMessage failed = new CollectTaskMessage();
                failed.setId(message.getId());
                failed.setConsumeStatus("failed");
                failed.setFinishTime(LocalDateTime.now());
                failed.setErrorMessage("任务或执行实例不存在");
                messageMapper.updateById(failed);
                continue;
            }
            collectEtlService.execute(task, instance, message);
            consumed++;
        }
        return consumed;
    }

    /**
     * 处理 XXL-JOB 调度请求，并投递采集任务。
     */
    public CollectInstance triggerXxlJob(CollectRunRequest request) {
        ensureCollectSchema();
        Assert.notNull(request, "XXL-JOB 触发参数不能为空");
        Assert.notNull(request.getTaskId(), "taskId 不能为空");
        request.setTriggerType(StrUtil.blankToDefault(request.getTriggerType(), "xxljob"));
        return dispatchTask(request.getTaskId(), request);
    }

    /**
     * 在事务中创建采集执行实例和内部消息。
     */
    protected DispatchResult dispatchTaskMessage(Long taskId, CollectRunRequest request) {
        return transactionTemplate.execute(status -> {
            CollectTask task = taskMapper.selectById(taskId);
            Assert.notNull(task, "采集任务不存在");
            Assert.isTrue(!"disabled".equals(task.getStatus()), "采集任务已停用");

            String traceId = UUID.randomUUID().toString().replace("-", "");
            CollectInstance instance = new CollectInstance();
            instance.setTaskId(taskId);
            instance.setJobLogId(request == null ? null : request.getJobLogId());
            instance.setTraceId(traceId);
            instance.setTriggerType(request == null || StrUtil.isBlank(request.getTriggerType()) ? "manual" : request.getTriggerType());
            instance.setStatus("pending_consume");
            instance.setStartTime(LocalDateTime.now());
            instance.setTotalCount(0);
            instance.setValidCount(0);
            instance.setInvalidCount(0);
            instance.setDuplicateCount(0);
            instance.setInsertedCount(0);
            instance.setUpdatedCount(0);
            instance.setFailedCount(0);
            instanceMapper.insert(instance);

            String messageId = "collect-" + instance.getId() + "-" + traceId.substring(0, 8);
            CollectTaskMessage message = new CollectTaskMessage();
            message.setTaskId(taskId);
            message.setInstanceId(instance.getId());
            message.setTraceId(traceId);
            message.setMqTopic("collect.task.dispatch");
            message.setMqMessageId(messageId);
            message.setMessageBody("{\"taskId\":" + taskId + ",\"instanceId\":" + instance.getId() + ",\"traceId\":\"" + traceId + "\"}");
            message.setSendStatus("success");
            message.setConsumeStatus("pending");
            message.setSendTime(LocalDateTime.now());
            messageMapper.insert(message);

            instance.setMqMessageId(messageId);
            instanceMapper.updateById(instance);
            return new DispatchResult(task, instance, message);
        });
    }

    /**
     * 分页查询执行实例，可按任务 ID 过滤。
     */
    public Page<CollectInstance> pageInstances(Long taskId, int pageNum, int pageSize) {
        ensureCollectSchema();
        return instanceMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectInstance>()
                .eq(taskId != null, CollectInstance::getTaskId, taskId)
                .orderByDesc(CollectInstance::getCreateTime));
    }

    /**
     * 查询指定执行实例的内部消息记录。
     */
    public List<CollectTaskMessage> listInstanceMessages(Long instanceId) {
        return messageMapper.selectList(new LambdaQueryWrapper<CollectTaskMessage>()
                .eq(CollectTaskMessage::getInstanceId, instanceId)
                .orderByDesc(CollectTaskMessage::getCreateTime));
    }

    /**
     * 分页查询指定执行实例的原始数据。
     */
    public Page<CollectRawData> pageRawData(Long instanceId, int pageNum, int pageSize) {
        ensureCollectSchema();
        return rawDataMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectRawData>()
                .eq(CollectRawData::getInstanceId, instanceId)
                .orderByAsc(CollectRawData::getDataIndex));
    }

    /**
     * 分页查询指定执行实例的异常数据。
     */
    public Page<CollectErrorData> pageErrorData(Long instanceId, int pageNum, int pageSize) {
        ensureCollectSchema();
        return errorDataMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectErrorData>()
                .eq(CollectErrorData::getInstanceId, instanceId)
                .orderByAsc(CollectErrorData::getDataIndex));
    }

    /**
     * 查询指定执行实例最近一次数据质量报告。
     */
    public CollectQualityReport getQualityReport(Long instanceId) {
        ensureCollectSchema();
        return qualityReportMapper.selectOne(new LambdaQueryWrapper<CollectQualityReport>()
                .eq(CollectQualityReport::getInstanceId, instanceId)
                .orderByDesc(CollectQualityReport::getCreateTime)
                .last("LIMIT 1"));
    }

    /**
     * 校验采集模型编码唯一性、字段配置和可选目标数据源。
     */
    private void validateModel(CollectModelRequest request) {
        Assert.isTrue(request.getFields() != null && !request.getFields().isEmpty(), "至少配置一个模型字段");
        long sameCode = modelMapper.selectCount(new LambdaQueryWrapper<CollectModel>()
                .eq(CollectModel::getModelCode, request.getModelCode())
                .ne(request.getId() != null, CollectModel::getId, request.getId()));
        Assert.isTrue(sameCode == 0, "模型编码已存在");
        if (request.getTargetDataSourceId() != null) {
            CollectDataSource target = requireDataSource(request.getTargetDataSourceId(), "目标数据源不存在");
            Assert.isTrue("db".equals(normalizeSourceType(target.getSourceType())), "目标表只能选择 DB 类型数据源");
        }
        long uniqueCount = request.getFields().stream().filter(field -> Integer.valueOf(1).equals(field.getUniqueFlag())).count();
        Assert.isTrue(uniqueCount > 0 || !"upsert".equals(request.getStatus()), "更新/去重策略至少配置一个唯一键字段");
    }

    /**
     * 校验采集接口基本配置；DB 采集接口必须绑定来源 数据源。
     */
    private void validateApi(CollectApi api) {
        Assert.isTrue(StrUtil.isNotBlank(api.getApiName()), "采集接口名称不能为空");
        Assert.isTrue(StrUtil.isNotBlank(api.getApiCode()), "采集接口编码不能为空");
        Assert.isTrue(StrUtil.isNotBlank(api.getCollectType()), "采集方式不能为空");
        if ("db".equalsIgnoreCase(api.getCollectType())) {
            CollectDataSource source = requireDataSource(api.getSourceDataSourceId(), "来源数据源不存在");
            Assert.isTrue("db".equals(normalizeSourceType(source.getSourceType())), "DB 采集接口必须选择 DB 类型数据源");
        } else if (api.getSourceDataSourceId() != null) {
            CollectDataSource source = requireDataSource(api.getSourceDataSourceId(), "来源数据源不存在");
            Assert.isTrue(normalizeSourceType(api.getCollectType()).equals(normalizeSourceType(source.getSourceType())), "采集接口和来源数据源类型不一致");
        }
    }

    /**
     * 查询数据源并校验存在。
     */
    private CollectDataSource requireDataSource(Long sourceId, String message) {
        Assert.notNull(sourceId, message);
        ensureCollectSchema();
        CollectDataSource source = dataSourceMapper.selectById(sourceId);
        Assert.notNull(source, message);
        return source;
    }

    /**
     * 校验模型接入规则绑定的模型和接口。
     */
    private void validateModelRule(CollectModelRule rule) {
        Assert.notNull(rule.getModelId(), "模型不能为空");
        Assert.notNull(rule.getApiId(), "采集接口不能为空");
        Assert.isTrue(StrUtil.isNotBlank(rule.getRuleName()), "规则名称不能为空");
        Assert.isTrue(StrUtil.isNotBlank(rule.getRuleCode()), "规则编码不能为空");
        Assert.notNull(modelMapper.selectById(rule.getModelId()), "采集模型不存在");
        Assert.notNull(apiMapper.selectById(rule.getApiId()), "采集接口不存在");
    }

    /**
     * 使用模型接入规则补齐任务的模型、接口、映射和转换配置。
     */
    private void prepareTaskRule(CollectTask task) {
        Assert.notNull(task.getRuleId(), "接入规则不能为空");
        CollectModelRule rule = modelRuleMapper.selectById(task.getRuleId());
        Assert.notNull(rule, "模型接入规则不存在");
        Assert.isTrue(!"disabled".equals(rule.getStatus()), "模型接入规则已停用");
        CollectModel model = modelMapper.selectById(rule.getModelId());
        Assert.notNull(model, "采集模型不存在");
        Assert.isTrue(!"disabled".equals(model.getStatus()), "采集模型已停用");
        CollectApi api = apiMapper.selectById(rule.getApiId());
        Assert.notNull(api, "采集接口不存在");
        Assert.isTrue(!"disabled".equals(api.getStatus()), "采集接口已停用");
        task.setModelId(rule.getModelId());
        task.setApiId(rule.getApiId());
        task.setMappingJson(rule.getMappingJson());
        task.setTransformJson(rule.getTransformJson());
    }

    private void ensureCollectSchema() {
        migrationRunner.ensureSchema();
    }

    /**
     * 保存采集模型字段列表，并补齐字段排序。
     */
    private void saveModelFields(Long modelId, List<CollectModelField> fields) {
        if (fields == null) {
            return;
        }
        int index = 1;
        for (CollectModelField field : fields) {
            field.setId(null);
            field.setModelId(modelId);
            field.setSort(field.getSort() == null ? index : field.getSort());
            modelFieldMapper.insert(field);
            index++;
        }
    }

    /**
     * 校验采集接口编码唯一性。
     */
    private void assertUniqueApiCode(CollectApi api) {
        long count = apiMapper.selectCount(new LambdaQueryWrapper<CollectApi>()
                .eq(CollectApi::getApiCode, api.getApiCode())
                .ne(api.getId() != null, CollectApi::getId, api.getId()));
        Assert.isTrue(count == 0, "采集接口编码已存在");
    }

    /**
     * 校验模型接入规则编码唯一性。
     */
    private void assertUniqueRuleCode(CollectModelRule rule) {
        long count = modelRuleMapper.selectCount(new LambdaQueryWrapper<CollectModelRule>()
                .eq(CollectModelRule::getRuleCode, rule.getRuleCode())
                .ne(rule.getId() != null, CollectModelRule::getId, rule.getId()));
        Assert.isTrue(count == 0, "模型接入规则编码已存在");
    }

    /**
     * 校验采集任务编码唯一性。
     */
    private void assertUniqueTaskCode(CollectTask task) {
        long count = taskMapper.selectCount(new LambdaQueryWrapper<CollectTask>()
                .eq(CollectTask::getTaskCode, task.getTaskCode())
                .ne(task.getId() != null, CollectTask::getId, task.getId()));
        Assert.isTrue(count == 0, "采集任务编码已存在");
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

    private record DispatchResult(CollectTask task, CollectInstance instance, CollectTaskMessage message) {
    }
}
