package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.identity.WorkflowIdentityService;
import com.youlai.flowable.mapper.WfTaskRecordMapper;
import com.youlai.flowable.model.entity.WfInstance;
import com.youlai.flowable.model.entity.WfTaskRecord;
import com.youlai.flowable.model.form.TaskApproveForm;
import com.youlai.flowable.model.query.WfTaskPageQuery;
import com.youlai.flowable.model.vo.WfTaskVO;
import com.youlai.flowable.service.WfRuntimeService;
import com.youlai.flowable.service.WfTaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WfTaskServiceImpl extends ServiceImpl<WfTaskRecordMapper, WfTaskRecord> implements WfTaskService {

    private final TaskService taskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final IdentityService flowableIdentityService;
    private final WfRuntimeService runtimeRecordService;
    private final WorkflowIdentityService identityService;

    @Override
    public Page<WfTaskVO> getTodoPage(WfTaskPageQuery queryParams) {
        String username = identityService.getCurrentUsername();
        org.flowable.task.api.TaskQuery taskQuery = taskService.createTaskQuery()
                .taskCandidateOrAssigned(username)
                .active()
                .orderByTaskCreateTime()
                .desc();
        long total = taskQuery.count();
        List<Task> tasks = taskQuery.listPage(firstResult(queryParams), queryParams.getPageSize());
        Page<WfTaskVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize(), total);
        page.setRecords(tasks.stream().map(this::toTaskVO).toList());
        return page;
    }

    @Override
    public Page<WfTaskVO> getDonePage(WfTaskPageQuery queryParams) {
        String username = identityService.getCurrentUsername();
        org.flowable.task.api.history.HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(username)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc();
        long total = taskQuery.count();
        List<HistoricTaskInstance> tasks = taskQuery.listPage(firstResult(queryParams), queryParams.getPageSize());
        Page<WfTaskVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize(), total);
        page.setRecords(tasks.stream().map(this::toTaskVO).toList());
        return page;
    }

    @Override
    @Transactional
    public boolean complete(String taskId, TaskApproveForm form) {
        Task task = getActiveTask(taskId);
        String username = identityService.getCurrentUsername();
        claimIfNeeded(task, username);
        addComment(task, form.getComment());
        Map<String, Object> variables = new HashMap<>(form.getVariables());
        variables.put("approved", true);
        taskService.complete(taskId, variables);
        saveRecord(task, "COMPLETE", form);
        runtimeRecordService.refreshInstanceStatus(task.getProcessInstanceId());
        return true;
    }

    @Override
    @Transactional
    public boolean reject(String taskId, TaskApproveForm form) {
        Task task = getActiveTask(taskId);
        String username = identityService.getCurrentUsername();
        claimIfNeeded(task, username);
        addComment(task, form.getComment());
        boolean backToActivity = StrUtil.isNotBlank(form.getTargetActivityId());
        if (backToActivity) {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdTo(task.getTaskDefinitionKey(), form.getTargetActivityId())
                    .changeState();
        } else {
            Map<String, Object> variables = new HashMap<>(form.getVariables());
            variables.put("approved", false);
            taskService.complete(taskId, variables);
        }
        saveRecord(task, "REJECT", form);
        if (backToActivity) {
            runtimeRecordService.refreshInstanceStatus(task.getProcessInstanceId());
        } else {
            runtimeRecordService.markRejected(task.getProcessInstanceId());
        }
        return true;
    }

    @Override
    public boolean transfer(String taskId, TaskApproveForm form) {
        Assert.isTrue(StrUtil.isNotBlank(form.getAssignee()), "转办人不能为空");
        Task task = getActiveTask(taskId);
        taskService.setAssignee(taskId, form.getAssignee());
        saveRecord(task, "TRANSFER", form);
        runtimeRecordService.refreshInstanceStatus(task.getProcessInstanceId());
        return true;
    }

    @Override
    public boolean delegate(String taskId, TaskApproveForm form) {
        Assert.isTrue(StrUtil.isNotBlank(form.getAssignee()), "委派人不能为空");
        Task task = getActiveTask(taskId);
        taskService.delegateTask(taskId, form.getAssignee());
        saveRecord(task, "DELEGATE", form);
        runtimeRecordService.refreshInstanceStatus(task.getProcessInstanceId());
        return true;
    }

    @Override
    public boolean addSign(String taskId, TaskApproveForm form) {
        Assert.isTrue(StrUtil.isNotBlank(form.getAssignee()), "加签人不能为空");
        Task task = getActiveTask(taskId);
        taskService.addUserIdentityLink(taskId, form.getAssignee(), "candidate");
        saveRecord(task, "ADD_SIGN", form);
        return true;
    }

    @Override
    public boolean claim(String taskId) {
        taskService.claim(taskId, identityService.getCurrentUsername());
        return true;
    }

    @Override
    public boolean unclaim(String taskId) {
        taskService.unclaim(taskId);
        return true;
    }

    private Task getActiveTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        Assert.notNull(task, "任务不存在或已处理");
        return task;
    }

    private void claimIfNeeded(Task task, String username) {
        if (StrUtil.isBlank(task.getAssignee())) {
            taskService.claim(task.getId(), username);
        }
    }

    private void addComment(Task task, String comment) {
        if (StrUtil.isBlank(comment)) {
            return;
        }
        flowableIdentityService.setAuthenticatedUserId(identityService.getCurrentUsername());
        taskService.addComment(task.getId(), task.getProcessInstanceId(), comment);
    }

    private void saveRecord(Task task, String action, TaskApproveForm form) {
        WfTaskRecord record = new WfTaskRecord();
        record.setTaskId(task.getId());
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskName(task.getName());
        record.setOperatorId(identityService.getCurrentUserId());
        record.setOperatorUsername(identityService.getCurrentUsername());
        record.setAction(action);
        record.setComment(form.getComment());
        record.setAttachmentJson(form.getAttachmentJson());
        this.save(record);
    }

    private WfTaskVO toTaskVO(Task task) {
        WfTaskVO vo = new WfTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setAssignee(task.getAssignee());
        vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
        fillInstanceInfo(vo);
        return vo;
    }

    private WfTaskVO toTaskVO(HistoricTaskInstance task) {
        WfTaskVO vo = new WfTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setAssignee(task.getAssignee());
        vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
        vo.setEndTime(toLocalDateTime(task.getEndTime()));
        fillInstanceInfo(vo);
        return vo;
    }

    private void fillInstanceInfo(WfTaskVO vo) {
        WfInstance instance = runtimeRecordService.getOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProcessInstanceId, vo.getProcessInstanceId()));
        if (instance == null) {
            return;
        }
        vo.setBusinessKey(instance.getBusinessKey());
        vo.setProcessName(instance.getModelName());
        vo.setStarterUsername(instance.getStarterUsername());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private int firstResult(WfTaskPageQuery queryParams) {
        return Math.max(queryParams.getPageNum() - 1, 0) * queryParams.getPageSize();
    }
}
