package com.youlai.flowable.controller;

import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.form.TaskApproveForm;
import com.youlai.flowable.model.query.WfTaskPageQuery;
import com.youlai.flowable.model.vo.WfTaskVO;
import com.youlai.flowable.service.WfTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "任务审批接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('workflow:*')")
@RequestMapping("/api/v1/workflow/tasks")
public class WfTaskController {

    private final WfTaskService taskService;

    @Operation(summary = "我的待办")
    @GetMapping("/todo/page")
    public PageResult<WfTaskVO> getTodoPage(@ParameterObject WfTaskPageQuery queryParams) {
        return PageResult.success(taskService.getTodoPage(queryParams));
    }

    @Operation(summary = "我的已办")
    @GetMapping("/done/page")
    public PageResult<WfTaskVO> getDonePage(@ParameterObject WfTaskPageQuery queryParams) {
        return PageResult.success(taskService.getDonePage(queryParams));
    }

    @Operation(summary = "同意")
    @PostMapping("/{taskId}/complete")
    public Result complete(@PathVariable String taskId, @RequestBody TaskApproveForm form) {
        return Result.judge(taskService.complete(taskId, form));
    }

    @Operation(summary = "驳回")
    @PostMapping("/{taskId}/reject")
    public Result reject(@PathVariable String taskId, @RequestBody TaskApproveForm form) {
        return Result.judge(taskService.reject(taskId, form));
    }

    @Operation(summary = "转办")
    @PostMapping("/{taskId}/transfer")
    public Result transfer(@PathVariable String taskId, @RequestBody TaskApproveForm form) {
        return Result.judge(taskService.transfer(taskId, form));
    }

    @Operation(summary = "委派")
    @PostMapping("/{taskId}/delegate")
    public Result delegate(@PathVariable String taskId, @RequestBody TaskApproveForm form) {
        return Result.judge(taskService.delegate(taskId, form));
    }

    @Operation(summary = "加签")
    @PostMapping("/{taskId}/add-sign")
    public Result addSign(@PathVariable String taskId, @RequestBody TaskApproveForm form) {
        return Result.judge(taskService.addSign(taskId, form));
    }

    @Operation(summary = "认领")
    @PostMapping("/{taskId}/claim")
    public Result claim(@PathVariable String taskId) {
        return Result.judge(taskService.claim(taskId));
    }

    @Operation(summary = "取消认领")
    @PostMapping("/{taskId}/unclaim")
    public Result unclaim(@PathVariable String taskId) {
        return Result.judge(taskService.unclaim(taskId));
    }
}
