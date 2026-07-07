package com.youlai.flowable.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.entity.AppBuilderModel;
import com.youlai.flowable.model.entity.AppBuilderModelField;
import com.youlai.flowable.model.entity.AppBuilderModelFieldVersion;
import com.youlai.flowable.service.impl.AppBuilderModelFieldService;
import com.youlai.flowable.service.impl.AppBuilderModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "应用搭建-数据模型接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder/models")
public class AppBuilderModelController {

    private final AppBuilderModelService modelService;
    private final AppBuilderModelFieldService fieldService;

    @Operation(summary = "模型分页")
    @GetMapping("/page")
    public PageResult<AppBuilderModel> getModelPage(@RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    @RequestParam(required = false) Long appId,
                                                    @RequestParam(required = false) String keywords,
                                                    @RequestParam(required = false) String status) {
        return PageResult.success(modelService.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AppBuilderModel>()
                        .eq(appId != null, AppBuilderModel::getAppId, appId)
                        .eq(StrUtil.isNotBlank(status), AppBuilderModel::getStatus, status)
                        .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                                .like(AppBuilderModel::getModelName, keywords)
                                .or()
                                .like(AppBuilderModel::getModelCode, keywords))
                        .orderByDesc(AppBuilderModel::getUpdateTime, AppBuilderModel::getId)));
    }

    @Operation(summary = "模型详情")
    @GetMapping("/{id}")
    public Result<AppBuilderModel> getModel(@PathVariable Long id) {
        return Result.success(modelService.getById(id));
    }

    @Operation(summary = "新增模型")
    @PostMapping
    public Result<AppBuilderModel> saveModel(@RequestBody AppBuilderModel model) {
        return Result.success(modelService.saveModel(model));
    }

    @Operation(summary = "修改模型")
    @PutMapping("/{id}")
    public Result<AppBuilderModel> updateModel(@PathVariable Long id, @RequestBody AppBuilderModel model) {
        model.setId(id);
        return Result.success(modelService.saveModel(model));
    }

    @Operation(summary = "发布模型")
    @PostMapping("/{id}/publish")
    public Result<AppBuilderModel> publishModel(@PathVariable Long id) {
        return Result.success(modelService.publishModel(id));
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    public Result deleteModel(@PathVariable Long id) {
        return Result.judge(modelService.deleteModel(id));
    }

    @Operation(summary = "字段列表")
    @GetMapping("/{modelId}/fields")
    public Result<List<AppBuilderModelField>> listFields(@PathVariable Long modelId) {
        return Result.success(fieldService.listByModelId(modelId));
    }

    @Operation(summary = "保存字段配置")
    @PutMapping("/{modelId}/fields")
    public Result saveFields(@PathVariable Long modelId, @RequestBody List<AppBuilderModelField> fields) {
        return Result.judge(fieldService.saveFields(modelId, fields));
    }

    @Operation(summary = "字段版本列表")
    @GetMapping("/{modelId}/field-versions")
    public Result<List<AppBuilderModelFieldVersion>> listFieldVersions(@PathVariable Long modelId) {
        return Result.success(fieldService.listVersions(modelId));
    }
}
