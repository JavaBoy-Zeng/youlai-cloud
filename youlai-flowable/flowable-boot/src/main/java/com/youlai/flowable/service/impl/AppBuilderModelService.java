package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.mapper.*;
import com.youlai.flowable.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppBuilderModelService extends ServiceImpl<AppBuilderModelMapper, AppBuilderModel> {

    private final AppBuilderAppService appService;
    private final AppBuilderSchemaService schemaService;
    private final AppBuilderOperationLogService operationLogService;
    private final AppBuilderModelFieldMapper fieldMapper;
    private final AppBuilderDataMapper dataMapper;
    private final AppBuilderFormMapper formMapper;
    private final AppBuilderPageMapper pageMapper;
    private final AppBuilderReportMapper reportMapper;
    private final AppBuilderAutomationMapper automationMapper;

    public AppBuilderModel saveModel(AppBuilderModel model) {
        Assert.notNull(model.getAppId(), "所属应用不能为空");
        Assert.notNull(appService.getById(model.getAppId()), "应用不存在");
        Assert.isTrue(StrUtil.isNotBlank(model.getModelCode()), "模型编码不能为空");
        Assert.isTrue(StrUtil.isNotBlank(model.getModelName()), "模型名称不能为空");
        String modelCode = StrUtil.trim(model.getModelCode());
        long count = this.count(new LambdaQueryWrapper<AppBuilderModel>()
                .ne(model.getId() != null, AppBuilderModel::getId, model.getId())
                .eq(AppBuilderModel::getAppId, model.getAppId())
                .eq(AppBuilderModel::getModelCode, modelCode));
        Assert.isTrue(count == 0, "模型编码已存在");
        model.setModelCode(modelCode);
        if (StrUtil.isBlank(model.getTableName())) {
            model.setTableName("biz_" + modelCode);
        }
        if (model.getEnableFlow() == null) {
            model.setEnableFlow(0);
        }
        if (StrUtil.isBlank(model.getStatus())) {
            model.setStatus("DRAFT");
        }
        boolean created = model.getId() == null;
        this.saveOrUpdate(model);
        operationLogService.record(model.getAppId(), "MODEL", created ? "CREATE" : "UPDATE", model, "保存模型");
        return model;
    }

    public AppBuilderModel publishModel(Long id) {
        AppBuilderModel model = this.getById(id);
        Assert.notNull(model, "模型不存在");
        schemaService.syncModelTable(id);
        model.setStatus("PUBLISHED");
        this.updateById(model);
        operationLogService.record(model.getAppId(), "MODEL", "PUBLISH", model, "发布模型并同步物理表");
        return model;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteModel(Long id) {
        AppBuilderModel model = this.getById(id);
        Assert.notNull(model, "模型不存在");
        List<String> dependencies = getDeleteDependencies(model);
        Assert.isTrue(dependencies.isEmpty(), "模型已被使用，不能删除：" + String.join("、", dependencies));

        fieldMapper.delete(new LambdaQueryWrapper<AppBuilderModelField>()
                .eq(AppBuilderModelField::getModelId, id));
        boolean removed = this.removeById(id);
        operationLogService.record(model.getAppId(), "MODEL", "DELETE", model, "删除模型及字段配置");
        return removed;
    }

    private List<String> getDeleteDependencies(AppBuilderModel model) {
        List<String> dependencies = new ArrayList<>();
        if (dataMapper.selectCount(new LambdaQueryWrapper<AppBuilderData>().eq(AppBuilderData::getModelId, model.getId())) > 0) {
            dependencies.add("业务数据");
        }
        if (formMapper.selectCount(new LambdaQueryWrapper<AppBuilderForm>().eq(AppBuilderForm::getModelId, model.getId())) > 0) {
            dependencies.add("表单");
        }
        if (pageMapper.selectCount(new LambdaQueryWrapper<AppBuilderPage>().eq(AppBuilderPage::getModelId, model.getId())) > 0) {
            dependencies.add("页面");
        }
        if (reportMapper.selectCount(new LambdaQueryWrapper<AppBuilderReport>().eq(AppBuilderReport::getModelId, model.getId())) > 0) {
            dependencies.add("报表");
        }
        if (automationMapper.selectCount(new LambdaQueryWrapper<AppBuilderAutomation>().eq(AppBuilderAutomation::getModelId, model.getId())) > 0) {
            dependencies.add("自动化规则");
        }
        if (Integer.valueOf(1).equals(model.getEnableFlow()) && StrUtil.isNotBlank(model.getProcessKey())) {
            dependencies.add("审批流程绑定");
        }
        return dependencies;
    }
}
