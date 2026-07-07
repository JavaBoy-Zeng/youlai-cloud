package com.youlai.flowable.controller;

import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.form.AppBuilderFormForm;
import com.youlai.flowable.model.query.AppBuilderFormPageQuery;
import com.youlai.flowable.model.vo.AppBuilderFormVO;
import com.youlai.flowable.service.AppBuilderFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应用搭建-表单接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder/forms")
public class AppBuilderFormController {

    private final AppBuilderFormService formService;

    @Operation(summary = "表单分页")
    @GetMapping("/page")
    public PageResult<AppBuilderFormVO> getFormPage(@ParameterObject AppBuilderFormPageQuery queryParams) {
        return PageResult.success(formService.getFormPage(queryParams));
    }

    @Operation(summary = "表单详情")
    @GetMapping("/{id}")
    public Result<AppBuilderFormVO> getForm(@PathVariable Long id) {
        return Result.success(formService.getForm(id));
    }

    @Operation(summary = "按表单标识获取详情")
    @GetMapping("/key/{formKey}")
    public Result<AppBuilderFormVO> getFormByKey(@PathVariable String formKey) {
        return Result.success(formService.getFormByKey(formKey));
    }

    @Operation(summary = "新增表单")
    @PostMapping
    public Result<AppBuilderFormVO> saveForm(@Valid @RequestBody AppBuilderFormForm form) {
        return Result.success(formService.saveForm(form));
    }

    @Operation(summary = "修改表单")
    @PutMapping("/{id}")
    public Result<AppBuilderFormVO> updateForm(@PathVariable Long id, @Valid @RequestBody AppBuilderFormForm form) {
        form.setId(id);
        return Result.success(formService.saveForm(form));
    }

    @Operation(summary = "发布表单")
    @PostMapping("/{id}/publish")
    public Result<AppBuilderFormVO> publishForm(@PathVariable Long id) {
        return Result.success(formService.publishForm(id));
    }

    @Operation(summary = "删除表单")
    @DeleteMapping("/{id}")
    public Result deleteForm(@PathVariable Long id) {
        return Result.judge(formService.deleteForm(id));
    }
}
