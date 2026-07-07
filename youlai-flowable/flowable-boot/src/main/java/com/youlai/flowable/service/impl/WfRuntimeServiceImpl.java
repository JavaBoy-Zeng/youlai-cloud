package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.converter.WorkflowConverter;
import com.youlai.flowable.identity.WorkflowIdentityService;
import com.youlai.flowable.mapper.AppBuilderDataMapper;
import com.youlai.flowable.mapper.WfInstanceMapper;
import com.youlai.flowable.mapper.WfTaskRecordMapper;
import com.youlai.flowable.model.entity.AppBuilderData;
import com.youlai.flowable.model.entity.WfInstance;
import com.youlai.flowable.model.entity.WfModel;
import com.youlai.flowable.model.entity.WfTaskRecord;
import com.youlai.flowable.model.form.StartProcessForm;
import com.youlai.flowable.model.query.WfInstancePageQuery;
import com.youlai.flowable.model.vo.ProcessDiagramVO;
import com.youlai.flowable.model.vo.WfInstanceVO;
import com.youlai.flowable.service.WfModelService;
import com.youlai.flowable.service.WfRuntimeService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WfRuntimeServiceImpl extends ServiceImpl<WfInstanceMapper, WfInstance> implements WfRuntimeService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_TERMINATED = "TERMINATED";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final WfModelService modelService;
    private final WfTaskRecordMapper taskRecordMapper;
    private final WorkflowIdentityService identityService;
    private final AppBuilderDataMapper appBuilderDataMapper;
    private final ObjectProvider<AppBuilderAutomationService> automationServiceProvider;

    @Override
    @Transactional
    public WfInstanceVO startProcess(StartProcessForm form) {
        WfModel model = null;
        if (form.getModelId() != null) {
            model = modelService.getById(form.getModelId());
            Assert.notNull(model, "流程模型不存在");
            form.setProcessDefinitionId(model.getProcessDefinitionId());
        }
        Assert.isTrue(StrUtil.isNotBlank(form.getProcessDefinitionId()), "流程定义ID不能为空");

        String username = identityService.getCurrentUsername();
        Map<String, Object> variables = new HashMap<>(form.getVariables());
        variables.put("initiator", username);
        variables.put("starter", username);
        variables.put("formData", form.getFormDataJson());

        ProcessInstance processInstance = runtimeService.startProcessInstanceById(
                form.getProcessDefinitionId(),
                form.getBusinessKey(),
                variables
        );

        WfInstance instance = new WfInstance();
        instance.setProcessInstanceId(processInstance.getProcessInstanceId());
        instance.setProcessDefinitionId(processInstance.getProcessDefinitionId());
        instance.setBusinessKey(form.getBusinessKey());
        instance.setModelId(model == null ? null : model.getId());
        instance.setModelKey(model == null ? processInstance.getProcessDefinitionKey() : model.getModelKey());
        instance.setModelName(model == null ? processInstance.getProcessDefinitionName() : model.getName());
        instance.setStarterId(identityService.getCurrentUserId());
        instance.setStarterUsername(username);
        instance.setStatus(STATUS_RUNNING);
        instance.setFormKey(model == null ? null : model.getFormKey());
        instance.setFormDataJson(form.getFormDataJson());
        instance.setStartTime(LocalDateTime.now());
        this.save(instance);
        refreshInstanceStatus(processInstance.getProcessInstanceId());
        return getInstanceDetail(processInstance.getProcessInstanceId());
    }

    @Override
    public Page<WfInstanceVO> getInstancePage(WfInstancePageQuery queryParams) {
        Page<WfInstance> page = this.page(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                new LambdaQueryWrapper<WfInstance>()
                        .eq(queryParams.getStarterId() != null, WfInstance::getStarterId, queryParams.getStarterId())
                        .eq(StrUtil.isNotBlank(queryParams.getStatus()), WfInstance::getStatus, queryParams.getStatus())
                        .and(StrUtil.isNotBlank(queryParams.getKeywords()), wrapper -> wrapper
                                .like(WfInstance::getModelName, queryParams.getKeywords())
                                .or()
                                .like(WfInstance::getBusinessKey, queryParams.getKeywords()))
                        .orderByDesc(WfInstance::getStartTime, WfInstance::getId));
        Page<WfInstanceVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(WorkflowConverter::toInstanceVO)
                .map(this::attachBusinessData)
                .toList());
        return result;
    }

    @Override
    public WfInstanceVO getInstanceDetail(String processInstanceId) {
        WfInstance instance = getByProcessInstanceId(processInstanceId);
        WfInstanceVO vo = attachBusinessData(WorkflowConverter.toInstanceVO(instance));
        vo.setRecords(taskRecordMapper.selectList(new LambdaQueryWrapper<WfTaskRecord>()
                        .eq(WfTaskRecord::getProcessInstanceId, processInstanceId)
                        .orderByAsc(WfTaskRecord::getCreateTime, WfTaskRecord::getId))
                .stream()
                .map(WorkflowConverter::toTaskRecordVO)
                .toList());
        return vo;
    }

    private WfInstanceVO attachBusinessData(WfInstanceVO vo) {
        if (vo == null || StrUtil.isBlank(vo.getBusinessKey())) {
            return vo;
        }
        AppBuilderData data = appBuilderDataMapper.selectOne(new LambdaQueryWrapper<AppBuilderData>()
                .eq(AppBuilderData::getBusinessKey, vo.getBusinessKey())
                .last("LIMIT 1"));
        if (data != null) {
            vo.setBusinessDataId(data.getId());
            vo.setBusinessModelId(data.getModelId());
            vo.setBusinessAppId(data.getAppId());
        }
        return vo;
    }

    @Override
    public ProcessDiagramVO getDiagram(String processInstanceId) {
        WfInstance instance = getByProcessInstanceId(processInstanceId);
        ProcessDiagramVO vo = new ProcessDiagramVO();
        vo.setBpmnXml(readBpmnXml(instance));
        vo.setActiveActivityIds(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult() == null ? List.of() : runtimeService.getActiveActivityIds(processInstanceId));
        vo.setFinishedActivityIds(historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .list()
                .stream()
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .toList());
        return vo;
    }

    @Override
    public boolean revoke(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, StrUtil.blankToDefault(reason, "流程撤回"));
        updateEndedStatus(processInstanceId, STATUS_REVOKED);
        return true;
    }

    @Override
    public boolean terminate(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, StrUtil.blankToDefault(reason, "流程终止"));
        updateEndedStatus(processInstanceId, STATUS_TERMINATED);
        return true;
    }

    @Override
    public void refreshInstanceStatus(String processInstanceId) {
        WfInstance instance = getByProcessInstanceId(processInstanceId);
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).active().list();
        if (tasks.isEmpty()) {
            boolean finished = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .finished()
                    .count() > 0;
            if (finished && STATUS_RUNNING.equals(instance.getStatus())) {
                updateEndedStatus(processInstanceId, STATUS_COMPLETED);
            }
            return;
        }
        instance.setCurrentNodeName(tasks.stream().map(Task::getName).distinct().reduce((a, b) -> a + "," + b).orElse(""));
        this.updateById(instance);
        syncBusinessDataStatus(instance, "APPROVING");
    }

    @Override
    public void markRejected(String processInstanceId) {
        updateEndedStatus(processInstanceId, STATUS_REJECTED);
    }

    private WfInstance getByProcessInstanceId(String processInstanceId) {
        WfInstance instance = this.getOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProcessInstanceId, processInstanceId));
        Assert.notNull(instance, "流程实例不存在");
        return instance;
    }

    private void updateEndedStatus(String processInstanceId, String status) {
        WfInstance instance = getByProcessInstanceId(processInstanceId);
        instance.setStatus(status);
        instance.setCurrentNodeName("");
        instance.setEndTime(LocalDateTime.now());
        this.updateById(instance);
        syncBusinessDataStatus(instance, toBusinessStatus(status));
        if (STATUS_COMPLETED.equals(status)) {
            AppBuilderAutomationService automationService = automationServiceProvider.getIfAvailable();
            if (automationService != null) {
                automationService.executeProcessTrigger(instance);
            }
        }
    }

    private String toBusinessStatus(String workflowStatus) {
        return switch (workflowStatus) {
            case STATUS_RUNNING -> "APPROVING";
            case STATUS_COMPLETED -> "APPROVED";
            case STATUS_REJECTED -> "REJECTED";
            case STATUS_REVOKED -> "REVOKED";
            case STATUS_TERMINATED -> "TERMINATED";
            default -> workflowStatus;
        };
    }

    private void syncBusinessDataStatus(WfInstance instance, String status) {
        if (instance == null || StrUtil.isBlank(instance.getBusinessKey()) || StrUtil.isBlank(status)) {
            return;
        }
        List<AppBuilderData> rows = appBuilderDataMapper.selectList(new LambdaQueryWrapper<AppBuilderData>()
                .eq(AppBuilderData::getBusinessKey, instance.getBusinessKey()));
        for (AppBuilderData row : rows) {
            row.setStatus(status);
            appBuilderDataMapper.updateById(row);
        }
    }

    private String readBpmnXml(WfInstance instance) {
        if (instance.getModelId() != null) {
            WfModel model = modelService.getById(instance.getModelId());
            if (model != null && StrUtil.isNotBlank(model.getBpmnXml())) {
                return model.getBpmnXml();
            }
        }
        org.flowable.engine.repository.ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(instance.getProcessDefinitionId())
                .singleResult();
        if (definition == null) {
            return "";
        }
        try (InputStream inputStream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
