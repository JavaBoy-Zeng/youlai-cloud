package com.youlai.flowable.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.entity.AppBuilderData;
import com.youlai.flowable.service.impl.AppBuilderDataService;
import com.youlai.flowable.service.impl.AppBuilderOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "应用搭建-业务数据接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder/data")
public class AppBuilderDataController {

    private final AppBuilderDataService dataService;
    private final AppBuilderOperationLogService operationLogService;

    @Operation(summary = "业务数据分页")
    @PostMapping("/{modelId}/page")
    public PageResult<Map<String, Object>> getDataPage(@PathVariable Long modelId, @RequestBody DataPageRequest request) {
        dataService.assertModelAccessible(modelId);
        Page<AppBuilderData> page = dataService.page(new Page<>(request.getPageNum(), request.getPageSize()),
                new LambdaQueryWrapper<AppBuilderData>()
                        .eq(AppBuilderData::getModelId, modelId)
                        .eq(StrUtil.isNotBlank(request.getStatus()), AppBuilderData::getStatus, request.getStatus())
                        .and(StrUtil.isNotBlank(request.getKeywords()), wrapper -> wrapper
                                .like(AppBuilderData::getBusinessKey, request.getKeywords())
                                .or()
                                .like(AppBuilderData::getDataJson, request.getKeywords()))
                        .orderByDesc(AppBuilderData::getUpdateTime, AppBuilderData::getId));
        Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(dataService.toDataList(page.getRecords()));
        return PageResult.success(result);
    }

    @Operation(summary = "业务数据详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getData(@PathVariable Long id) {
        dataService.assertDataAccessible(id);
        AppBuilderData row = dataService.getById(id);
        Map<String, Object> data = dataService.parseJson(row.getDataJson());
        data.put("_id", row.getId());
        data.put("_businessKey", row.getBusinessKey());
        data.put("_status", row.getStatus());
        return Result.success(data);
    }

    @Operation(summary = "新增业务数据")
    @PostMapping("/{modelId}")
    public Result<AppBuilderData> createData(@PathVariable Long modelId, @RequestBody Map<String, Object> data) {
        return Result.success(dataService.createData(modelId, data));
    }

    @Operation(summary = "修改业务数据")
    @PutMapping("/{id}")
    public Result<AppBuilderData> updateData(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return Result.success(dataService.updateData(id, data));
    }

    @Operation(summary = "提交业务数据审批")
    @PostMapping("/{id}/submit")
    public Result<AppBuilderData> submitData(@PathVariable Long id) {
        return Result.success(dataService.submitForApproval(id));
    }

    @Operation(summary = "删除业务数据")
    @DeleteMapping("/{id}")
    public Result deleteData(@PathVariable Long id) {
        return Result.judge(dataService.deleteData(id));
    }

    @Operation(summary = "批量导入业务数据")
    @PostMapping("/{modelId}/import")
    public Result<Integer> importData(@PathVariable Long modelId, @RequestBody List<Map<String, Object>> rows) {
        dataService.assertModelAccessible(modelId);
        rows.forEach(row -> dataService.createData(modelId, row));
        operationLogService.record(dataService.getAppIdByModelId(modelId), "DATA", "IMPORT",
                Map.of("modelId", modelId, "count", rows.size()), "批量导入业务数据");
        return Result.success(rows.size());
    }

    @Operation(summary = "导出业务数据")
    @PostMapping("/{modelId}/export")
    public Result<List<Map<String, Object>>> exportData(@PathVariable Long modelId, @RequestBody DataPageRequest request) {
        dataService.assertModelAccessible(modelId);
        List<AppBuilderData> rows = dataService.list(new LambdaQueryWrapper<AppBuilderData>()
                .eq(AppBuilderData::getModelId, modelId)
                .like(StrUtil.isNotBlank(request.getKeywords()), AppBuilderData::getDataJson, request.getKeywords())
                .orderByDesc(AppBuilderData::getUpdateTime, AppBuilderData::getId));
        operationLogService.record(dataService.getAppIdByModelId(modelId), "DATA", "EXPORT",
                Map.of("modelId", modelId, "count", rows.size(), "keywords", StrUtil.blankToDefault(request.getKeywords(), "")), "导出业务数据");
        return Result.success(dataService.toDataList(rows));
    }

    @Data
    public static class DataPageRequest {
        private int pageNum = 1;
        private int pageSize = 10;
        private String keywords;
        private String status;
    }
}
