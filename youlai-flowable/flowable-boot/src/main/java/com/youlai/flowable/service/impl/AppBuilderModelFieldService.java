package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.flowable.mapper.AppBuilderModelFieldMapper;
import com.youlai.flowable.mapper.AppBuilderModelFieldVersionMapper;
import com.youlai.flowable.model.entity.AppBuilderModel;
import com.youlai.flowable.model.entity.AppBuilderModelField;
import com.youlai.flowable.model.entity.AppBuilderModelFieldVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppBuilderModelFieldService extends ServiceImpl<AppBuilderModelFieldMapper, AppBuilderModelField> {

    private final AppBuilderModelService modelService;
    private final AppBuilderSchemaService schemaService;
    private final AppBuilderOperationLogService operationLogService;
    private final AppBuilderModelFieldVersionMapper fieldVersionMapper;
    private final ObjectMapper objectMapper;

    public List<AppBuilderModelField> listByModelId(Long modelId) {
        Assert.notNull(modelService.getById(modelId), "模型不存在");
        return this.list(new LambdaQueryWrapper<AppBuilderModelField>()
                .eq(AppBuilderModelField::getModelId, modelId)
                .orderByAsc(AppBuilderModelField::getSortOrder, AppBuilderModelField::getId));
    }

    @Transactional
    public boolean saveFields(Long modelId, List<AppBuilderModelField> fields) {
        AppBuilderModel model = modelService.getById(modelId);
        Assert.notNull(model, "模型不存在");
        this.remove(new LambdaQueryWrapper<AppBuilderModelField>().eq(AppBuilderModelField::getModelId, modelId));
        for (int i = 0; i < fields.size(); i++) {
            AppBuilderModelField field = fields.get(i);
            Assert.isTrue(StrUtil.isNotBlank(field.getFieldCode()), "字段编码不能为空");
            Assert.isTrue(StrUtil.isNotBlank(field.getFieldName()), "字段名称不能为空");
            field.setId(null);
            field.setModelId(modelId);
            if (field.getRequired() == null) {
                field.setRequired(0);
            }
            if (field.getSortOrder() == null) {
                field.setSortOrder(i + 1);
            }
            if (StrUtil.isBlank(field.getDbType())) {
                field.setDbType("varchar");
            }
        }
        boolean saved = fields.isEmpty() || this.saveBatch(fields);
        recordFieldVersion(modelId, fields);
        schemaService.syncModelTable(modelId);
        operationLogService.record(model.getAppId(), "MODEL_FIELD", "SAVE", fields, "保存字段并同步表结构");
        return saved;
    }

    public List<AppBuilderModelFieldVersion> listVersions(Long modelId) {
        Assert.notNull(modelService.getById(modelId), "模型不存在");
        return fieldVersionMapper.selectList(new LambdaQueryWrapper<AppBuilderModelFieldVersion>()
                .eq(AppBuilderModelFieldVersion::getModelId, modelId)
                .orderByDesc(AppBuilderModelFieldVersion::getVersionNo, AppBuilderModelFieldVersion::getId));
    }

    private void recordFieldVersion(Long modelId, List<AppBuilderModelField> fields) {
        AppBuilderModelFieldVersion latest = fieldVersionMapper.selectOne(new LambdaQueryWrapper<AppBuilderModelFieldVersion>()
                .eq(AppBuilderModelFieldVersion::getModelId, modelId)
                .orderByDesc(AppBuilderModelFieldVersion::getVersionNo)
                .last("LIMIT 1"));
        AppBuilderModelFieldVersion version = new AppBuilderModelFieldVersion();
        version.setModelId(modelId);
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setFieldsSnapshotJson(toJson(fields));
        version.setRemark("保存字段时自动生成");
        fieldVersionMapper.insert(version);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("字段版本快照生成失败", e);
        }
    }
}
