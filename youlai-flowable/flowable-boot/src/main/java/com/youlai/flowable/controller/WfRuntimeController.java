package com.youlai.flowable.controller;

import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.identity.WorkflowIdentityService;
import com.youlai.flowable.model.form.StartProcessForm;
import com.youlai.flowable.model.query.WfInstancePageQuery;
import com.youlai.flowable.model.vo.ProcessDiagramVO;
import com.youlai.flowable.model.vo.WfInstanceVO;
import com.youlai.flowable.service.WfRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "流程运行接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('workflow:*')")
@RequestMapping("/api/v1/workflow/instances")
public class WfRuntimeController {

    private final WfRuntimeService runtimeService;
    private final WorkflowIdentityService identityService;

    @Operation(summary = "发起流程")
    @PostMapping("/start")
    public Result<WfInstanceVO> startProcess(@RequestBody StartProcessForm form) {
        return Result.success(runtimeService.startProcess(form));
    }

    @Operation(summary = "流程实例分页")
    @GetMapping("/page")
    public PageResult<WfInstanceVO> getInstancePage(@ParameterObject WfInstancePageQuery queryParams) {
        return PageResult.success(runtimeService.getInstancePage(queryParams));
    }

    @Operation(summary = "我的发起")
    @GetMapping("/my/page")
    public PageResult<WfInstanceVO> getMyStartedPage(@ParameterObject WfInstancePageQuery queryParams) {
        queryParams.setStarterId(identityService.getCurrentUserId());
        return PageResult.success(runtimeService.getInstancePage(queryParams));
    }

    @Operation(summary = "流程实例详情")
    @GetMapping("/{processInstanceId}")
    public Result<WfInstanceVO> getInstanceDetail(@PathVariable String processInstanceId) {
        return Result.success(runtimeService.getInstanceDetail(processInstanceId));
    }

    @Operation(summary = "流程图高亮信息")
    @GetMapping("/{processInstanceId}/diagram")
    public Result<ProcessDiagramVO> getDiagram(@PathVariable String processInstanceId) {
        return Result.success(runtimeService.getDiagram(processInstanceId));
    }

    @Operation(summary = "撤回流程")
    @PostMapping("/{processInstanceId}/revoke")
    public Result revoke(@PathVariable String processInstanceId, @RequestParam(required = false) String reason) {
        return Result.judge(runtimeService.revoke(processInstanceId, reason));
    }

    @Operation(summary = "终止流程")
    @PostMapping("/{processInstanceId}/terminate")
    public Result terminate(@PathVariable String processInstanceId, @RequestParam(required = false) String reason) {
        return Result.judge(runtimeService.terminate(processInstanceId, reason));
    }
}
