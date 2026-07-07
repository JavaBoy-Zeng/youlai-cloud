package com.youlai.collect.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.collect.model.*;
import com.youlai.collect.service.CollectService;
import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/collect")
public class CollectController {

    private final CollectService collectService;

    public CollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    @GetMapping("/dashboard")
    public Result<CollectDashboard> dashboard() {
        return Result.success(collectService.dashboard());
    }

    @GetMapping("/models")
    public PageResult<CollectModel> pageModels(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageModels(pageNum, pageSize, keywords, status));
    }

    @GetMapping("/models/{id}")
    public Result<CollectModelDetail> getModel(@PathVariable Long id) {
        return Result.success(collectService.getModel(id));
    }

    @PostMapping("/models")
    public Result<Void> saveModel(@Valid @RequestBody CollectModelRequest request) {
        return Result.judge(collectService.saveModel(request));
    }

    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @Valid @RequestBody CollectModelRequest request) {
        return Result.judge(collectService.updateModel(id, request));
    }

    @PostMapping("/models/{id}/enable")
    public Result<Void> enableModel(@PathVariable Long id) {
        return Result.judge(collectService.updateModelStatus(id, "enabled"));
    }

    @PostMapping("/models/{id}/disable")
    public Result<Void> disableModel(@PathVariable Long id) {
        return Result.judge(collectService.updateModelStatus(id, "disabled"));
    }

    @PostMapping("/models/{id}/table/preview")
    public Result<String> previewModelTableDdl(@PathVariable Long id) {
        return Result.success(collectService.previewModelTableDdl(id));
    }

    @GetMapping("/apis")
    public PageResult<CollectApi> pageApis(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String collectType,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageApis(pageNum, pageSize, keywords, collectType, status));
    }

    @PostMapping("/apis")
    public Result<Void> saveApi(@RequestBody CollectApi api) {
        return Result.judge(collectService.saveApi(api));
    }

    @PutMapping("/apis/{id}")
    public Result<Void> updateApi(@PathVariable Long id, @RequestBody CollectApi api) {
        return Result.judge(collectService.updateApi(id, api));
    }

    @PostMapping("/apis/{id}/test")
    public Result<Map<String, Object>> testApi(@PathVariable Long id) {
        return Result.success(collectService.testApi(id));
    }

    @GetMapping("/db-sources")
    public PageResult<CollectDbSource> pageDbSources(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageDbSources(pageNum, pageSize, keywords, status));
    }

    @PostMapping("/db-sources")
    public Result<Void> saveDbSource(@RequestBody CollectDbSource source) {
        return Result.judge(collectService.saveDbSource(source));
    }

    @PutMapping("/db-sources/{id}")
    public Result<Void> updateDbSource(@PathVariable Long id, @RequestBody CollectDbSource source) {
        return Result.judge(collectService.updateDbSource(id, source));
    }

    @PostMapping("/db-sources/{id}/test")
    public Result<Map<String, Object>> testDbSource(@PathVariable Long id) {
        return Result.success(collectService.testDbSource(id));
    }

    @PostMapping("/db-sources/{id}/disable")
    public Result<Void> disableDbSource(@PathVariable Long id) {
        CollectDbSource source = new CollectDbSource();
        source.setStatus("disabled");
        return Result.judge(collectService.updateDbSource(id, source));
    }

    @GetMapping("/tasks")
    public PageResult<CollectTask> pageTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageTasks(pageNum, pageSize, keywords, status));
    }

    @PostMapping("/tasks")
    public Result<Void> saveTask(@RequestBody CollectTask task) {
        return Result.judge(collectService.saveTask(task));
    }

    @PutMapping("/tasks/{id}")
    public Result<Void> updateTask(@PathVariable Long id, @RequestBody CollectTask task) {
        return Result.judge(collectService.updateTask(id, task));
    }

    @PostMapping("/tasks/{id}/enable")
    public Result<Void> enableTask(@PathVariable Long id) {
        return Result.judge(collectService.updateTaskStatus(id, "enabled"));
    }

    @PostMapping("/tasks/{id}/disable")
    public Result<Void> disableTask(@PathVariable Long id) {
        return Result.judge(collectService.updateTaskStatus(id, "disabled"));
    }

    @PostMapping("/tasks/{id}/run")
    public Result<CollectInstance> runTask(@PathVariable Long id, @RequestBody(required = false) CollectRunRequest request) {
        return Result.success(collectService.runTask(id, request));
    }

    @PostMapping("/tasks/{id}/dispatch")
    public Result<CollectInstance> dispatchTask(@PathVariable Long id, @RequestBody(required = false) CollectRunRequest request) {
        return Result.success(collectService.dispatchTask(id, request));
    }

    @PostMapping("/jobs/dataCollectJobHandler")
    public Result<CollectInstance> dataCollectJobHandler(@RequestBody CollectRunRequest request) {
        return Result.success(collectService.triggerXxlJob(request));
    }

    @PostMapping("/messages/consume")
    public Result<Integer> consumePendingMessages(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(collectService.consumePendingMessages(limit));
    }

    @GetMapping("/tasks/{id}/instances")
    public PageResult<CollectInstance> pageTaskInstances(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return PageResult.success(collectService.pageInstances(id, pageNum, pageSize));
    }

    @GetMapping("/instances")
    public PageResult<CollectInstance> pageInstances(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<CollectInstance> page = collectService.pageInstances(null, pageNum, pageSize);
        return PageResult.success(page);
    }

    @GetMapping("/instances/{id}/messages")
    public Result<List<CollectTaskMessage>> listInstanceMessages(@PathVariable Long id) {
        return Result.success(collectService.listInstanceMessages(id));
    }

    @PostMapping("/instances/{id}/retry")
    public Result<CollectInstance> retryInstance(@PathVariable Long id) {
        return Result.success(collectService.retryInstance(id));
    }

    @GetMapping("/instances/{id}/raw-data")
    public PageResult<CollectRawData> pageRawData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return PageResult.success(collectService.pageRawData(id, pageNum, pageSize));
    }

    @GetMapping("/instances/{id}/error-data")
    public PageResult<CollectErrorData> pageErrorData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return PageResult.success(collectService.pageErrorData(id, pageNum, pageSize));
    }

    @GetMapping("/instances/{id}/quality-report")
    public Result<CollectQualityReport> getQualityReport(@PathVariable Long id) {
        return Result.success(collectService.getQualityReport(id));
    }
}
