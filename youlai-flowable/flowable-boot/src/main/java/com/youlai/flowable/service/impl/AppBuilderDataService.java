package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.flowable.mapper.AppBuilderDataMapper;
import com.youlai.flowable.model.entity.AppBuilderModel;
import com.youlai.flowable.model.entity.AppBuilderData;
import com.youlai.flowable.model.entity.WfModel;
import com.youlai.flowable.model.form.StartProcessForm;
import com.youlai.flowable.service.WfModelService;
import com.youlai.flowable.service.WfRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppBuilderDataService extends ServiceImpl<AppBuilderDataMapper, AppBuilderData> {

    private final AppBuilderModelService modelService;
    private final AppBuilderAppService appService;
    private final WfModelService wfModelService;
    private final WfRuntimeService wfRuntimeService;
    private final AppBuilderSchemaService schemaService;
    private final AppBuilderAutomationService automationService;
    private final AppBuilderOperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public AppBuilderData createData(Long modelId, Map<String, Object> data) {
        var model = modelService.getById(modelId);
        Assert.notNull(model, "模型不存在");
        assertAppAccessible(model.getAppId());
        AppBuilderData entity = new AppBuilderData();
        entity.setAppId(model.getAppId());
        entity.setModelId(modelId);
        entity.setBusinessKey(model.getModelCode() + "-" + System.currentTimeMillis());
        entity.setStatus("DRAFT");
        entity.setDataJson(toJson(data));
        this.save(entity);
        schemaService.upsertDataRow(entity, data == null ? Map.of() : data);
        operationLogService.record(entity.getAppId(), "DATA", "CREATE", entity, "新增业务数据");
        automationService.executeDataTrigger("DATA_CREATE", entity, data == null ? Map.of() : data);
        return entity;
    }

    public AppBuilderData updateData(Long id, Map<String, Object> data) {
        AppBuilderData entity = this.getById(id);
        Assert.notNull(entity, "业务数据不存在");
        assertAppAccessible(entity.getAppId());
        entity.setDataJson(toJson(data));
        this.updateById(entity);
        schemaService.upsertDataRow(entity, data == null ? Map.of() : data);
        operationLogService.record(entity.getAppId(), "DATA", "UPDATE", entity, "修改业务数据");
        automationService.executeDataTrigger("DATA_UPDATE", entity, data == null ? Map.of() : data);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteData(Long id) {
        AppBuilderData entity = this.getById(id);
        if (entity == null) {
            return true;
        }
        assertAppAccessible(entity.getAppId());
        schemaService.deleteDataRow(entity);
        boolean removed = this.removeById(id);
        operationLogService.record(entity.getAppId(), "DATA", "DELETE", entity, "删除业务数据");
        return removed;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppBuilderData submitForApproval(Long id) {
        AppBuilderData entity = this.getById(id);
        Assert.notNull(entity, "业务数据不存在");
        assertAppAccessible(entity.getAppId());
        Assert.isTrue(!"APPROVING".equals(entity.getStatus()), "业务数据已在审批中");
        Assert.isTrue(!"APPROVED".equals(entity.getStatus()), "业务数据已审批通过，不能重复提交");

        AppBuilderModel appModel = modelService.getById(entity.getModelId());
        Assert.notNull(appModel, "业务模型不存在");
        Assert.isTrue(Integer.valueOf(1).equals(appModel.getEnableFlow()), "当前模型未启用审批流程");
        Assert.isTrue(StrUtil.isNotBlank(appModel.getProcessKey()), "当前模型未绑定流程编码");

        WfModel wfModel = wfModelService.getOne(new LambdaQueryWrapper<WfModel>()
                .eq(WfModel::getModelKey, appModel.getProcessKey())
                .eq(WfModel::getStatus, "PUBLISHED")
                .orderByDesc(WfModel::getVersion, WfModel::getId)
                .last("LIMIT 1"));
        Assert.notNull(wfModel, "绑定的流程未发布或不存在");
        Assert.isTrue(StrUtil.isNotBlank(wfModel.getProcessDefinitionId()), "绑定的流程尚未部署");

        entity.setStatus("APPROVING");
        this.updateById(entity);
        operationLogService.record(entity.getAppId(), "DATA", "SUBMIT_APPROVAL", entity, "提交审批");

        StartProcessForm form = new StartProcessForm();
        form.setModelId(wfModel.getId());
        form.setBusinessKey(entity.getBusinessKey());
        form.setFormDataJson(entity.getDataJson());
        form.getVariables().put("appId", entity.getAppId());
        form.getVariables().put("appModelId", entity.getModelId());
        form.getVariables().put("appDataId", entity.getId());
        wfRuntimeService.startProcess(form);
        return entity;
    }

    public List<Map<String, Object>> toDataList(List<AppBuilderData> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> data = parseJson(row.getDataJson());
            data.put("_id", row.getId());
            data.put("_businessKey", row.getBusinessKey());
            data.put("_status", row.getStatus());
            data.put("_createTime", row.getCreateTime());
            data.put("_updateTime", row.getUpdateTime());
            return data;
        }).toList();
    }

    public void assertModelAccessible(Long modelId) {
        var model = modelService.getById(modelId);
        Assert.notNull(model, "模型不存在");
        assertAppAccessible(model.getAppId());
    }

    public Long getAppIdByModelId(Long modelId) {
        var model = modelService.getById(modelId);
        Assert.notNull(model, "模型不存在");
        return model.getAppId();
    }

    public void assertDataAccessible(Long id) {
        AppBuilderData entity = this.getById(id);
        Assert.notNull(entity, "业务数据不存在");
        assertAppAccessible(entity.getAppId());
    }

    private void assertAppAccessible(Long appId) {
        var app = appService.getById(appId);
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(!"DISABLED".equals(app.getStatus()), "应用已停用，禁止访问业务数据");
    }

    public Map<String, Object> parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("业务数据JSON格式不正确", e);
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("业务数据JSON格式不正确", e);
        }
    }
}
