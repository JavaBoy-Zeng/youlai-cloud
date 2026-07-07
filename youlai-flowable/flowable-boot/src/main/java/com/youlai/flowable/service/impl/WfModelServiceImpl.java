package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.converter.WorkflowConverter;
import com.youlai.flowable.mapper.WfModelMapper;
import com.youlai.flowable.model.entity.WfModel;
import com.youlai.flowable.model.form.WfModelForm;
import com.youlai.flowable.model.query.WfModelPageQuery;
import com.youlai.flowable.model.vo.WfModelVO;
import com.youlai.flowable.service.WfModelService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WfModelServiceImpl extends ServiceImpl<WfModelMapper, WfModel> implements WfModelService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_SUSPENDED = "SUSPENDED";

    private final RepositoryService repositoryService;

    @Override
    public Page<WfModelVO> getModelPage(WfModelPageQuery queryParams) {
        Page<WfModel> page = this.page(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                new LambdaQueryWrapper<WfModel>()
                        .eq(queryParams.getCategoryId() != null, WfModel::getCategoryId, queryParams.getCategoryId())
                        .eq(StrUtil.isNotBlank(queryParams.getStatus()), WfModel::getStatus, queryParams.getStatus())
                        .and(StrUtil.isNotBlank(queryParams.getKeywords()), wrapper -> wrapper
                                .like(WfModel::getName, queryParams.getKeywords())
                                .or()
                                .like(WfModel::getModelKey, queryParams.getKeywords()))
                        .orderByDesc(WfModel::getUpdateTime, WfModel::getId));
        Page<WfModelVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(WorkflowConverter::toModelVO).toList());
        return result;
    }

    @Override
    public WfModelVO getModel(Long id) {
        WfModel model = this.getById(id);
        Assert.notNull(model, "流程模型不存在");
        return WorkflowConverter.toModelVO(model);
    }

    @Override
    public boolean saveModel(WfModelForm form) {
        Long id = form.getId();
        long count = this.count(new LambdaQueryWrapper<WfModel>()
                .ne(id != null, WfModel::getId, id)
                .eq(WfModel::getModelKey, form.getModelKey()));
        Assert.isTrue(count == 0, "流程编码已存在");

        WfModel model = new WfModel();
        BeanUtils.copyProperties(form, model);
        if (model.getVersion() == null) {
            model.setVersion(1);
        }
        if (StrUtil.isBlank(model.getStatus())) {
            model.setStatus(STATUS_DRAFT);
        }
        if (StrUtil.isBlank(model.getBpmnXml())) {
            model.setBpmnXml(defaultBpmnXml(model.getModelKey(), model.getName()));
        }
        return this.saveOrUpdate(model);
    }

    @Override
    @Transactional
    public WfModelVO publishModel(Long id) {
        WfModel model = this.getById(id);
        Assert.notNull(model, "流程模型不存在");
        Assert.isTrue(StrUtil.isNotBlank(model.getBpmnXml()), "请先保存 BPMN XML");

        String resourceName = model.getModelKey() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(model.getName())
                .key(model.getModelKey())
                .addString(resourceName, model.getBpmnXml())
                .deploy();

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        Assert.notNull(definition, "流程部署失败，未生成流程定义");

        model.setDeploymentId(deployment.getId());
        model.setProcessDefinitionId(definition.getId());
        model.setVersion(definition.getVersion());
        model.setStatus(STATUS_PUBLISHED);
        this.updateById(model);
        return WorkflowConverter.toModelVO(model);
    }

    @Override
    public boolean updateDefinitionState(Long id, boolean suspended) {
        WfModel model = this.getById(id);
        Assert.notNull(model, "流程模型不存在");
        Assert.isTrue(StrUtil.isNotBlank(model.getProcessDefinitionId()), "流程尚未发布");
        if (suspended) {
            repositoryService.suspendProcessDefinitionById(model.getProcessDefinitionId(), true, null);
            model.setStatus(STATUS_SUSPENDED);
        } else {
            repositoryService.activateProcessDefinitionById(model.getProcessDefinitionId(), true, null);
            model.setStatus(STATUS_PUBLISHED);
        }
        return this.updateById(model);
    }

    @Override
    @Transactional
    public boolean deleteModel(Long id) {
        WfModel model = this.getById(id);
        Assert.notNull(model, "流程模型不存在");
        if (StrUtil.isNotBlank(model.getDeploymentId())) {
            repositoryService.deleteDeployment(model.getDeploymentId(), true);
        }
        return this.removeById(id);
    }

    @Override
    public String exportBpmnXml(Long id) {
        WfModel model = this.getById(id);
        Assert.notNull(model, "流程模型不存在");
        return model.getBpmnXml();
    }

    private String defaultBpmnXml(String processKey, String processName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://youlai.com/workflow">
                  <process id="%s" name="%s" isExecutable="true">
                    <startEvent id="startEvent" name="开始"/>
                    <sequenceFlow id="flow_start_approve" sourceRef="startEvent" targetRef="approveTask"/>
                    <userTask id="approveTask" name="审批" flowable:assignee="${initiator}"/>
                    <sequenceFlow id="flow_approve_end" sourceRef="approveTask" targetRef="endEvent"/>
                    <endEvent id="endEvent" name="结束"/>
                  </process>
                </definitions>
                """.formatted(processKey, processName);
    }
}
