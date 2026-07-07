package com.youlai.collect.service;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final CollectApiMapper apiMapper;
    private final CollectDbSourceMapper dbSourceMapper;
    private final CollectTaskMapper taskMapper;
    private final CollectInstanceMapper instanceMapper;
    private final CollectTaskMessageMapper messageMapper;
    private final CollectRawDataMapper rawDataMapper;
    private final CollectErrorDataMapper errorDataMapper;
    private final CollectQualityReportMapper qualityReportMapper;
    private final CollectEtlService collectEtlService;
    private final TransactionTemplate transactionTemplate;

    public CollectService(
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
            CollectEtlService collectEtlService,
            TransactionTemplate transactionTemplate
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
        this.collectEtlService = collectEtlService;
        this.transactionTemplate = transactionTemplate;
    }

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

    public Page<CollectModel> pageModels(int pageNum, int pageSize, String keywords, String status) {
        return modelMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectModel>()
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectModel::getModelName, keywords)
                        .or()
                        .like(CollectModel::getModelCode, keywords))
                .eq(StrUtil.isNotBlank(status), CollectModel::getStatus, status)
                .orderByDesc(CollectModel::getUpdateTime));
    }

    public CollectModelDetail getModel(Long id) {
        CollectModel model = modelMapper.selectById(id);
        Assert.notNull(model, "采集模型不存在");
        List<CollectModelField> fields = modelFieldMapper.selectList(new LambdaQueryWrapper<CollectModelField>()
                .eq(CollectModelField::getModelId, id)
                .orderByAsc(CollectModelField::getSort)
                .orderByAsc(CollectModelField::getId));
        return CollectModelDetail.of(model, fields);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveModel(CollectModelRequest request) {
        validateModel(request);
        CollectModel model = new CollectModel();
        BeanUtils.copyProperties(request, model);
        model.setStatus(StrUtil.blankToDefault(model.getStatus(), "enabled"));
        model.setFieldCount(request.getFields() == null ? 0 : request.getFields().size());
        modelMapper.insert(model);
        saveModelFields(model.getId(), request.getFields());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateModel(Long id, CollectModelRequest request) {
        CollectModel exists = modelMapper.selectById(id);
        Assert.notNull(exists, "采集模型不存在");
        request.setId(id);
        validateModel(request);
        CollectModel model = new CollectModel();
        BeanUtils.copyProperties(request, model);
        model.setId(id);
        model.setFieldCount(request.getFields() == null ? 0 : request.getFields().size());
        modelMapper.updateById(model);
        modelFieldMapper.delete(new LambdaQueryWrapper<CollectModelField>().eq(CollectModelField::getModelId, id));
        saveModelFields(id, request.getFields());
        return true;
    }

    public boolean updateModelStatus(Long id, String status) {
        CollectModel model = new CollectModel();
        model.setId(id);
        model.setStatus(status);
        return modelMapper.updateById(model) > 0;
    }

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

    public Page<CollectApi> pageApis(int pageNum, int pageSize, String keywords, String collectType, String status) {
        return apiMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectApi>()
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectApi::getApiName, keywords)
                        .or()
                        .like(CollectApi::getApiCode, keywords))
                .eq(StrUtil.isNotBlank(collectType), CollectApi::getCollectType, collectType)
                .eq(StrUtil.isNotBlank(status), CollectApi::getStatus, status)
                .orderByDesc(CollectApi::getUpdateTime));
    }

    public boolean saveApi(CollectApi api) {
        assertUniqueApiCode(api);
        api.setStatus(StrUtil.blankToDefault(api.getStatus(), "enabled"));
        return apiMapper.insert(api) > 0;
    }

    public boolean updateApi(Long id, CollectApi api) {
        api.setId(id);
        assertUniqueApiCode(api);
        return apiMapper.updateById(api) > 0;
    }

    public Map<String, Object> testApi(Long id) {
        CollectApi api = apiMapper.selectById(id);
        Assert.notNull(api, "采集接口不存在");
        return Map.of("success", true, "collectType", api.getCollectType(), "message", "接口配置校验通过，真实采集适配器待接入");
    }

    public Page<CollectDbSource> pageDbSources(int pageNum, int pageSize, String keywords, String status) {
        return dbSourceMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectDbSource>()
                .like(StrUtil.isNotBlank(keywords), CollectDbSource::getSourceName, keywords)
                .eq(StrUtil.isNotBlank(status), CollectDbSource::getStatus, status)
                .orderByDesc(CollectDbSource::getUpdateTime));
    }

    public boolean saveDbSource(CollectDbSource source) {
        normalizeDbSource(source);
        return dbSourceMapper.insert(source) > 0;
    }

    public boolean updateDbSource(Long id, CollectDbSource source) {
        source.setId(id);
        if (StrUtil.isNotBlank(source.getDbType())) {
            normalizeDbSource(source);
        }
        return dbSourceMapper.updateById(source) > 0;
    }

    private void normalizeDbSource(CollectDbSource source) {
        String dbType = normalizeDbType(source.getDbType());
        source.setDbType(dbType);
        source.setDriverClass(StrUtil.blankToDefault(source.getDriverClass(), defaultDriverClass(dbType)));
        source.setValidationQuery(StrUtil.blankToDefault(source.getValidationQuery(), defaultValidationQuery(dbType)));
        source.setStatus(StrUtil.blankToDefault(source.getStatus(), "enabled"));
    }

    private String normalizeDbType(String dbType) {
        String normalized = StrUtil.blankToDefault(dbType, "mysql").trim().toLowerCase();
        return switch (normalized) {
            case "postgres", "postgresql" -> "postgresql";
            case "clickhouse" -> "clickhouse";
            case "dm", "dameng" -> "dm";
            default -> "mysql";
        };
    }

    private String defaultDriverClass(String dbType) {
        return switch (dbType) {
            case "postgresql" -> "org.postgresql.Driver";
            case "clickhouse" -> "com.clickhouse.jdbc.ClickHouseDriver";
            case "dm" -> "dm.jdbc.driver.DmDriver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    private String defaultValidationQuery(String dbType) {
        return "dm".equals(dbType) ? "SELECT 1 FROM DUAL" : "SELECT 1";
    }

    public Map<String, Object> testDbSource(Long id) {
        CollectDbSource source = dbSourceMapper.selectById(id);
        Assert.notNull(source, "DB 数据源不存在");
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
        CollectDbSource update = new CollectDbSource();
        update.setId(id);
        update.setLastTestTime(LocalDateTime.now());
        update.setLastTestStatus(success ? "success" : "failed");
        dbSourceMapper.updateById(update);
        return Map.of("success", success, "message", message);
    }

    public Page<CollectTask> pageTasks(int pageNum, int pageSize, String keywords, String status) {
        return taskMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectTask>()
                .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                        .like(CollectTask::getTaskName, keywords)
                        .or()
                        .like(CollectTask::getTaskCode, keywords))
                .eq(StrUtil.isNotBlank(status), CollectTask::getStatus, status)
                .orderByDesc(CollectTask::getUpdateTime));
    }

    public boolean saveTask(CollectTask task) {
        assertUniqueTaskCode(task);
        task.setStatus(StrUtil.blankToDefault(task.getStatus(), "draft"));
        return taskMapper.insert(task) > 0;
    }

    public boolean updateTask(Long id, CollectTask task) {
        task.setId(id);
        assertUniqueTaskCode(task);
        return taskMapper.updateById(task) > 0;
    }

    public boolean updateTaskStatus(Long id, String status) {
        CollectTask task = new CollectTask();
        task.setId(id);
        task.setStatus(status);
        return taskMapper.updateById(task) > 0;
    }

    public CollectInstance runTask(Long taskId, CollectRunRequest request) {
        DispatchResult dispatch = dispatchTaskMessage(taskId, request);
        return collectEtlService.execute(dispatch.task(), dispatch.instance(), dispatch.message());
    }

    public CollectInstance dispatchTask(Long taskId, CollectRunRequest request) {
        return dispatchTaskMessage(taskId, request).instance();
    }

    public CollectInstance retryInstance(Long instanceId) {
        CollectInstance instance = instanceMapper.selectById(instanceId);
        Assert.notNull(instance, "执行实例不存在");
        CollectRunRequest request = new CollectRunRequest();
        request.setTriggerType("retry");
        return runTask(instance.getTaskId(), request);
    }

    public int consumePendingMessages(int limit) {
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

    public CollectInstance triggerXxlJob(CollectRunRequest request) {
        Assert.notNull(request, "XXL-JOB 触发参数不能为空");
        Assert.notNull(request.getTaskId(), "taskId 不能为空");
        request.setTriggerType(StrUtil.blankToDefault(request.getTriggerType(), "xxljob"));
        return dispatchTask(request.getTaskId(), request);
    }

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

    public Page<CollectInstance> pageInstances(Long taskId, int pageNum, int pageSize) {
        return instanceMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectInstance>()
                .eq(taskId != null, CollectInstance::getTaskId, taskId)
                .orderByDesc(CollectInstance::getCreateTime));
    }

    public List<CollectTaskMessage> listInstanceMessages(Long instanceId) {
        return messageMapper.selectList(new LambdaQueryWrapper<CollectTaskMessage>()
                .eq(CollectTaskMessage::getInstanceId, instanceId)
                .orderByDesc(CollectTaskMessage::getCreateTime));
    }

    public Page<CollectRawData> pageRawData(Long instanceId, int pageNum, int pageSize) {
        return rawDataMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectRawData>()
                .eq(CollectRawData::getInstanceId, instanceId)
                .orderByAsc(CollectRawData::getDataIndex));
    }

    public Page<CollectErrorData> pageErrorData(Long instanceId, int pageNum, int pageSize) {
        return errorDataMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<CollectErrorData>()
                .eq(CollectErrorData::getInstanceId, instanceId)
                .orderByAsc(CollectErrorData::getDataIndex));
    }

    public CollectQualityReport getQualityReport(Long instanceId) {
        return qualityReportMapper.selectOne(new LambdaQueryWrapper<CollectQualityReport>()
                .eq(CollectQualityReport::getInstanceId, instanceId)
                .orderByDesc(CollectQualityReport::getCreateTime)
                .last("LIMIT 1"));
    }

    private void validateModel(CollectModelRequest request) {
        Assert.isTrue(request.getFields() != null && !request.getFields().isEmpty(), "至少配置一个模型字段");
        long sameCode = modelMapper.selectCount(new LambdaQueryWrapper<CollectModel>()
                .eq(CollectModel::getModelCode, request.getModelCode())
                .ne(request.getId() != null, CollectModel::getId, request.getId()));
        Assert.isTrue(sameCode == 0, "模型编码已存在");
        long uniqueCount = request.getFields().stream().filter(field -> Integer.valueOf(1).equals(field.getUniqueFlag())).count();
        Assert.isTrue(uniqueCount > 0 || !"upsert".equals(request.getStatus()), "更新/去重策略至少配置一个唯一键字段");
    }

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

    private void assertUniqueApiCode(CollectApi api) {
        long count = apiMapper.selectCount(new LambdaQueryWrapper<CollectApi>()
                .eq(CollectApi::getApiCode, api.getApiCode())
                .ne(api.getId() != null, CollectApi::getId, api.getId()));
        Assert.isTrue(count == 0, "采集接口编码已存在");
    }

    private void assertUniqueTaskCode(CollectTask task) {
        long count = taskMapper.selectCount(new LambdaQueryWrapper<CollectTask>()
                .eq(CollectTask::getTaskCode, task.getTaskCode())
                .ne(task.getId() != null, CollectTask::getId, task.getId()));
        Assert.isTrue(count == 0, "采集任务编码已存在");
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

    private record DispatchResult(CollectTask task, CollectInstance instance, CollectTaskMessage message) {
    }
}
