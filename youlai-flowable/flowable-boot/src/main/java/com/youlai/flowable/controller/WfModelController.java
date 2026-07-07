package com.youlai.flowable.controller;

import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.form.WfModelForm;
import com.youlai.flowable.model.query.WfModelPageQuery;
import com.youlai.flowable.model.vo.WfModelVO;
import com.youlai.flowable.service.WfModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "流程定义接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('workflow:*')")
@RequestMapping("/api/v1/workflow/models")
public class WfModelController {

    private final WfModelService modelService;

    @Operation(summary = "流程定义分页")
    @GetMapping("/page")
    public PageResult<WfModelVO> getModelPage(@ParameterObject WfModelPageQuery queryParams) {
        return PageResult.success(modelService.getModelPage(queryParams));
    }

    @Operation(summary = "流程定义详情")
    @GetMapping("/{id}")
    public Result<WfModelVO> getModel(@PathVariable Long id) {
        return Result.success(modelService.getModel(id));
    }

    @Operation(summary = "新增流程模型")
    @PostMapping
    public Result saveModel(@Valid @RequestBody WfModelForm form) {
        return Result.judge(modelService.saveModel(form));
    }

    @Operation(summary = "修改流程模型")
    @PutMapping("/{id}")
    public Result updateModel(@PathVariable Long id, @Valid @RequestBody WfModelForm form) {
        form.setId(id);
        return Result.judge(modelService.saveModel(form));
    }

    @Operation(summary = "发布流程定义")
    @PostMapping("/{id}/publish")
    public Result<WfModelVO> publishModel(@PathVariable Long id) {
        return Result.success(modelService.publishModel(id));
    }

    @Operation(summary = "停用流程定义")
    @PutMapping("/{id}/suspend")
    public Result suspendModel(@PathVariable Long id) {
        return Result.judge(modelService.updateDefinitionState(id, true));
    }

    @Operation(summary = "启用流程定义")
    @PutMapping("/{id}/activate")
    public Result activateModel(@PathVariable Long id) {
        return Result.judge(modelService.updateDefinitionState(id, false));
    }

    @Operation(summary = "删除流程定义")
    @DeleteMapping("/{id}")
    public Result deleteModel(@PathVariable Long id) {
        return Result.judge(modelService.deleteModel(id));
    }

    @Operation(summary = "导出 BPMN XML")
    @GetMapping("/{id}/bpmn-xml")
    public Result<String> exportBpmnXml(@PathVariable Long id) {
        return Result.success(modelService.exportBpmnXml(id));
    }
}
