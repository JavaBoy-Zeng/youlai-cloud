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

    /**
     * 注入采集业务服务。
     */
    public CollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    /**
     * 查询采集模块首页统计数据。
     */
    @GetMapping("/dashboard")
    public Result<CollectDashboard> dashboard() {
        return Result.success(collectService.dashboard());
    }

    /**
     * 返回不分页的 HTTP 用户示例数据，用于演示 HTTP 采集配置。
     */
    @GetMapping("/examples/http/users/no-page")
    public Result<Map<String, Object>> exampleHttpUsersNoPage() {
        return Result.success(Map.of(
                "code", 0,
                "data", Map.of("list", exampleHttpUsers()),
                "message", "ok"
        ));
    }

    /**
     * 返回分页的 HTTP 用户示例数据，用于演示分页采集配置。
     */
    @GetMapping("/examples/http/users/page")
    public Result<Map<String, Object>> exampleHttpUsersPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "2") int pageSize
    ) {
        List<Map<String, Object>> users = exampleHttpUsers();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int from = Math.min((safePageNo - 1) * safePageSize, users.size());
        int to = Math.min(from + safePageSize, users.size());
        return Result.success(Map.of(
                "code", 0,
                "data", Map.of(
                        "list", users.subList(from, to),
                        "pageNo", safePageNo,
                        "pageSize", safePageSize,
                        "total", users.size(),
                        "hasNext", to < users.size()
                ),
                "message", "ok"
        ));
    }

    /**
     * 构造 HTTP 示例接口共用的用户数据。
     */
    private List<Map<String, Object>> exampleHttpUsers() {
        return List.of(
                Map.of(
                        "sourceUserId", 1001,
                        "account", "alpha",
                        "displayName", "Alpha User",
                        "mail", "alpha@example.com",
                        "state", "ACTIVE",
                        "updatedAt", "2026-07-08 09:00:00"
                ),
                Map.of(
                        "sourceUserId", 1002,
                        "account", "beta",
                        "displayName", "Beta User",
                        "mail", "beta@example.com",
                        "state", "LOCKED",
                        "updatedAt", "2026-07-08 09:05:00"
                ),
                Map.of(
                        "sourceUserId", 1003,
                        "account", "gamma",
                        "displayName", "Gamma User",
                        "mail", "gamma@example.com",
                        "state", "ACTIVE",
                        "updatedAt", "2026-07-08 09:10:00"
                )
        );
    }

    /**
     * 分页查询采集模型。
     */
    @GetMapping("/models")
    public PageResult<CollectModel> pageModels(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageModels(pageNum, pageSize, keywords, status));
    }

    /**
     * 查询采集模型详情及字段配置。
     */
    @GetMapping("/models/{id}")
    public Result<CollectModelDetail> getModel(@PathVariable Long id) {
        return Result.success(collectService.getModel(id));
    }

    /**
     * 新增采集模型。
     */
    @PostMapping("/models")
    public Result<Void> saveModel(@Valid @RequestBody CollectModelRequest request) {
        return Result.judge(collectService.saveModel(request));
    }

    /**
     * 修改采集模型及字段配置。
     */
    @PutMapping("/models/{id}")
    public Result<Void> updateModel(@PathVariable Long id, @Valid @RequestBody CollectModelRequest request) {
        return Result.judge(collectService.updateModel(id, request));
    }

    /**
     * 启用采集模型。
     */
    @PostMapping("/models/{id}/enable")
    public Result<Void> enableModel(@PathVariable Long id) {
        return Result.judge(collectService.updateModelStatus(id, "enabled"));
    }

    /**
     * 停用采集模型。
     */
    @PostMapping("/models/{id}/disable")
    public Result<Void> disableModel(@PathVariable Long id) {
        return Result.judge(collectService.updateModelStatus(id, "disabled"));
    }

    /**
     * 预览采集模型对应的目标表建表 SQL。
     */
    @PostMapping("/models/{id}/table/preview")
    public Result<String> previewModelTableDdl(@PathVariable Long id) {
        return Result.success(collectService.previewModelTableDdl(id));
    }

    /**
     * 分页查询采集接口配置。
     */
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

    /**
     * 新增采集接口配置。
     */
    @PostMapping("/apis")
    public Result<Void> saveApi(@RequestBody CollectApi api) {
        return Result.judge(collectService.saveApi(api));
    }

    /**
     * 修改采集接口配置。
     */
    @PutMapping("/apis/{id}")
    public Result<Void> updateApi(@PathVariable Long id, @RequestBody CollectApi api) {
        return Result.judge(collectService.updateApi(id, api));
    }

    /**
     * 测试采集接口配置。
     */
    @PostMapping("/apis/{id}/test")
    public Result<Map<String, Object>> testApi(@PathVariable Long id) {
        return Result.success(collectService.testApi(id));
    }

    /**
     * 分页查询模型接入规则。
     */
    @GetMapping("/model-rules")
    public PageResult<CollectModelRule> pageModelRules(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) Long apiId,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageModelRules(pageNum, pageSize, modelId, apiId, keywords, status));
    }

    /**
     * 查询模型接入规则详情。
     */
    @GetMapping("/model-rules/{id}")
    public Result<CollectModelRule> getModelRule(@PathVariable Long id) {
        return Result.success(collectService.getModelRule(id));
    }

    /**
     * 新增模型接入规则。
     */
    @PostMapping("/model-rules")
    public Result<Void> saveModelRule(@RequestBody CollectModelRule rule) {
        return Result.judge(collectService.saveModelRule(rule));
    }

    /**
     * 修改模型接入规则。
     */
    @PutMapping("/model-rules/{id}")
    public Result<Void> updateModelRule(@PathVariable Long id, @RequestBody CollectModelRule rule) {
        return Result.judge(collectService.updateModelRule(id, rule));
    }

    /**
     * 启用模型接入规则。
     */
    @PostMapping("/model-rules/{id}/enable")
    public Result<Void> enableModelRule(@PathVariable Long id) {
        return Result.judge(collectService.updateModelRuleStatus(id, "enabled"));
    }

    /**
     * 停用模型接入规则。
     */
    @PostMapping("/model-rules/{id}/disable")
    public Result<Void> disableModelRule(@PathVariable Long id) {
        return Result.judge(collectService.updateModelRuleStatus(id, "disabled"));
    }

    /**
     * 分页查询 数据源配置。
     */
    @GetMapping({"/data-sources", "/db-sources"})
    public PageResult<CollectDataSource> pageDataSources(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageDataSources(pageNum, pageSize, keywords, status));
    }

    /**
     * 新增 数据源配置。
     */
    @PostMapping({"/data-sources", "/db-sources"})
    public Result<Void> saveDataSource(@RequestBody CollectDataSource source) {
        return Result.judge(collectService.saveDataSource(source));
    }

    /**
     * 修改 数据源配置。
     */
    @PutMapping({"/data-sources/{id}", "/db-sources/{id}"})
    public Result<Void> updateDataSource(@PathVariable Long id, @RequestBody CollectDataSource source) {
        return Result.judge(collectService.updateDataSource(id, source));
    }

    /**
     * 测试 数据源连接。
     */
    @PostMapping({"/data-sources/{id}/test", "/db-sources/{id}/test"})
    public Result<Map<String, Object>> testDataSource(@PathVariable Long id) {
        return Result.success(collectService.testDataSource(id));
    }

    /**
     * 停用 数据源。
     */
    @PostMapping({"/data-sources/{id}/disable", "/db-sources/{id}/disable"})
    public Result<Void> disableDataSource(@PathVariable Long id) {
        return Result.judge(collectService.updateDataSourceStatus(id, "disabled"));
    }

    /**
     * 分页查询采集任务。
     */
    @GetMapping("/tasks")
    public PageResult<CollectTask> pageTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String status
    ) {
        return PageResult.success(collectService.pageTasks(pageNum, pageSize, keywords, status));
    }

    /**
     * 新增采集任务。
     */
    @PostMapping("/tasks")
    public Result<Void> saveTask(@RequestBody CollectTask task) {
        return Result.judge(collectService.saveTask(task));
    }

    /**
     * 修改采集任务。
     */
    @PutMapping("/tasks/{id}")
    public Result<Void> updateTask(@PathVariable Long id, @RequestBody CollectTask task) {
        return Result.judge(collectService.updateTask(id, task));
    }

    /**
     * 启用采集任务。
     */
    @PostMapping("/tasks/{id}/enable")
    public Result<Void> enableTask(@PathVariable Long id) {
        return Result.judge(collectService.updateTaskStatus(id, "enabled"));
    }

    /**
     * 停用采集任务。
     */
    @PostMapping("/tasks/{id}/disable")
    public Result<Void> disableTask(@PathVariable Long id) {
        return Result.judge(collectService.updateTaskStatus(id, "disabled"));
    }

    /**
     * 手动执行采集任务，并同步完成本次 ETL。
     */
    @PostMapping("/tasks/{id}/run")
    public Result<CollectInstance> runTask(@PathVariable Long id, @RequestBody(required = false) CollectRunRequest request) {
        return Result.success(collectService.runTask(id, request));
    }

    /**
     * 投递采集任务消息，等待异步消费者执行。
     */
    @PostMapping("/tasks/{id}/dispatch")
    public Result<CollectInstance> dispatchTask(@PathVariable Long id, @RequestBody(required = false) CollectRunRequest request) {
        return Result.success(collectService.dispatchTask(id, request));
    }

    /**
     * XXL-JOB 回调入口，按任务 ID 投递采集任务。
     */
    @PostMapping("/jobs/dataCollectJobHandler")
    public Result<CollectInstance> dataCollectJobHandler(@RequestBody CollectRunRequest request) {
        return Result.success(collectService.triggerXxlJob(request));
    }

    /**
     * 手动消费内部待处理采集消息。
     */
    @PostMapping("/messages/consume")
    public Result<Integer> consumePendingMessages(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(collectService.consumePendingMessages(limit));
    }

    /**
     * 分页查询指定任务的执行实例。
     */
    @GetMapping("/tasks/{id}/instances")
    public PageResult<CollectInstance> pageTaskInstances(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return PageResult.success(collectService.pageInstances(id, pageNum, pageSize));
    }

    /**
     * 分页查询全部执行实例。
     */
    @GetMapping("/instances")
    public PageResult<CollectInstance> pageInstances(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<CollectInstance> page = collectService.pageInstances(null, pageNum, pageSize);
        return PageResult.success(page);
    }

    /**
     * 查询执行实例关联的内部消息记录。
     */
    @GetMapping("/instances/{id}/messages")
    public Result<List<CollectTaskMessage>> listInstanceMessages(@PathVariable Long id) {
        return Result.success(collectService.listInstanceMessages(id));
    }

    /**
     * 基于失败或历史实例重新发起一次采集任务。
     */
    @PostMapping("/instances/{id}/retry")
    public Result<CollectInstance> retryInstance(@PathVariable Long id) {
        return Result.success(collectService.retryInstance(id));
    }

    /**
     * 分页查询执行实例的原始采集数据。
     */
    @GetMapping("/instances/{id}/raw-data")
    public PageResult<CollectRawData> pageRawData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return PageResult.success(collectService.pageRawData(id, pageNum, pageSize));
    }

    /**
     * 分页查询执行实例的异常数据明细。
     */
    @GetMapping("/instances/{id}/error-data")
    public PageResult<CollectErrorData> pageErrorData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return PageResult.success(collectService.pageErrorData(id, pageNum, pageSize));
    }

    /**
     * 查询执行实例的数据质量报告。
     */
    @GetMapping("/instances/{id}/quality-report")
    public Result<CollectQualityReport> getQualityReport(@PathVariable Long id) {
        return Result.success(collectService.getQualityReport(id));
    }
}
