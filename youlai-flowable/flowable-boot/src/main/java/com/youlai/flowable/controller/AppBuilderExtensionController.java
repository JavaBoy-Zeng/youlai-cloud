package com.youlai.flowable.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.mapper.*;
import com.youlai.flowable.model.entity.*;
import com.youlai.flowable.service.impl.AppBuilderApiInvokeService;
import com.youlai.flowable.service.impl.AppBuilderAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "应用搭建-扩展能力接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder")
public class AppBuilderExtensionController {

    private final AppBuilderReportMapper reportMapper;
    private final AppBuilderApiMapper apiMapper;
    private final AppBuilderApiLogMapper apiLogMapper;
    private final AppBuilderDataMapper dataMapper;
    private final AppBuilderAutomationMapper automationMapper;
    private final AppBuilderTemplateMapper templateMapper;
    private final AppBuilderVersionMapper versionMapper;
    private final AppBuilderTenantMapper tenantMapper;
    private final AppBuilderOperationLogMapper operationLogMapper;
    private final AppBuilderNotificationMapper notificationMapper;
    private final AppBuilderAppService appService;
    private final AppBuilderApiInvokeService apiInvokeService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "报表分页")
    @GetMapping("/reports/page")
    public PageResult<AppBuilderReport> getReportPage(QueryRequest query) {
        return PageResult.success(reportMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderReport>()
                        .eq(query.getAppId() != null, AppBuilderReport::getAppId, query.getAppId())
                        .eq(query.getModelId() != null, AppBuilderReport::getModelId, query.getModelId())
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderReport::getReportName, query.getKeywords())
                        .orderByDesc(AppBuilderReport::getUpdateTime, AppBuilderReport::getId)));
    }

    @PostMapping("/reports")
    public Result<AppBuilderReport> saveReport(@RequestBody AppBuilderReport row) {
        if (StrUtil.isBlank(row.getStatus())) row.setStatus("DRAFT");
        if (row.getId() == null) reportMapper.insert(row); else reportMapper.updateById(row);
        return Result.success(row);
    }

    @PutMapping("/reports/{id}")
    public Result<AppBuilderReport> updateReport(@PathVariable Long id, @RequestBody AppBuilderReport row) {
        row.setId(id);
        reportMapper.updateById(row);
        return Result.success(row);
    }

    @PostMapping("/reports/{id}/publish")
    public Result publishReport(@PathVariable Long id) {
        AppBuilderReport row = reportMapper.selectById(id);
        row.setStatus("PUBLISHED");
        return Result.judge(reportMapper.updateById(row) > 0);
    }

    @GetMapping("/reports/{id}/run")
    public Result<Map<String, Object>> runReport(@PathVariable Long id) {
        AppBuilderReport report = reportMapper.selectById(id);
        Map<String, Object> dataSource = parseMap(report.getDataSourceJson());
        Long modelId = toLong(dataSource.getOrDefault("modelId", report.getModelId()));
        String groupBy = String.valueOf(dataSource.getOrDefault("groupBy", ""));
        String valueField = String.valueOf(dataSource.getOrDefault("valueField", ""));
        String aggregate = String.valueOf(dataSource.getOrDefault("aggregate", "count")).toLowerCase();
        String sort = String.valueOf(dataSource.getOrDefault("sort", "desc")).toLowerCase();
        int limit = toInt(dataSource.get("limit"), 0);
        java.util.List<AppBuilderData> dataRows = dataMapper.selectList(new LambdaQueryWrapper<AppBuilderData>()
                .eq(modelId != null, AppBuilderData::getModelId, modelId));

        Map<String, Number> grouped = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (AppBuilderData row : dataRows) {
            Map<String, Object> data = parseMap(row.getDataJson());
            rows.add(data);
            String key = StrUtil.isBlank(groupBy) ? "总计" : String.valueOf(data.getOrDefault(groupBy, "未分组"));
            Number value = "sum".equals(aggregate) ? toNumber(data.get(valueField)) : 1;
            grouped.merge(key, value, (left, right) -> left.doubleValue() + right.doubleValue());
        }
        java.util.stream.Stream<Map.Entry<String, Number>> stream = grouped.entrySet().stream()
                .sorted((left, right) -> "asc".equals(sort)
                        ? Double.compare(left.getValue().doubleValue(), right.getValue().doubleValue())
                        : Double.compare(right.getValue().doubleValue(), left.getValue().doubleValue()));
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        java.util.List<Map<String, Object>> series = stream
                .map(entry -> Map.<String, Object>of("name", entry.getKey(), "value", entry.getValue()))
                .toList();
        java.util.List<Map<String, Object>> rankings = new java.util.ArrayList<>();
        for (int index = 0; index < series.size(); index++) {
            Map<String, Object> item = new LinkedHashMap<>(series.get(index));
            item.put("rank", index + 1);
            rankings.add(item);
        }
        return Result.success(Map.of(
                "reportId", report.getId(),
                "reportName", report.getReportName(),
                "chartType", StrUtil.blankToDefault(report.getChartType(), "bar"),
                "aggregate", aggregate,
                "series", series,
                "rankings", rankings,
                "rows", rows,
                "total", dataRows.size()
        ));
    }

    @DeleteMapping("/reports/{id}")
    public Result deleteReport(@PathVariable Long id) {
        return Result.judge(reportMapper.deleteById(id) > 0);
    }

    @Operation(summary = "API 分页")
    @GetMapping("/apis/page")
    public PageResult<AppBuilderApi> getApiPage(QueryRequest query) {
        return PageResult.success(apiMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderApi>()
                        .eq(query.getAppId() != null, AppBuilderApi::getAppId, query.getAppId())
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderApi::getApiName, query.getKeywords())
                        .orderByDesc(AppBuilderApi::getUpdateTime, AppBuilderApi::getId)));
    }

    @PostMapping("/apis")
    public Result<AppBuilderApi> saveApi(@RequestBody AppBuilderApi row) {
        if (StrUtil.isBlank(row.getMethod())) row.setMethod("GET");
        if (StrUtil.isBlank(row.getStatus())) row.setStatus("ENABLED");
        if (row.getId() == null) apiMapper.insert(row); else apiMapper.updateById(row);
        return Result.success(row);
    }

    @PutMapping("/apis/{id}")
    public Result<AppBuilderApi> updateApi(@PathVariable Long id, @RequestBody AppBuilderApi row) {
        row.setId(id);
        apiMapper.updateById(row);
        return Result.success(row);
    }

    @PostMapping("/apis/{id}/invoke")
    public Result<AppBuilderApiLog> invokeApi(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        AppBuilderApi api = apiMapper.selectById(id);
        if (api == null) {
            throw new IllegalArgumentException("API不存在：" + id);
        }
        if (!"ENABLED".equals(api.getStatus())) {
            throw new IllegalArgumentException("API未启用：" + id);
        }
        return Result.success(apiInvokeService.invoke(api, payload == null ? Map.of() : payload));
    }

    @DeleteMapping("/apis/{id}")
    public Result deleteApi(@PathVariable Long id) {
        return Result.judge(apiMapper.deleteById(id) > 0);
    }

    @GetMapping("/api-logs/page")
    public PageResult<AppBuilderApiLog> getApiLogPage(QueryRequest query) {
        return PageResult.success(apiLogMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderApiLog>()
                        .eq(query.getApiId() != null, AppBuilderApiLog::getApiId, query.getApiId())
                        .orderByDesc(AppBuilderApiLog::getCreateTime, AppBuilderApiLog::getId)));
    }

    @GetMapping("/automations/page")
    public PageResult<AppBuilderAutomation> getAutomationPage(QueryRequest query) {
        return PageResult.success(automationMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderAutomation>()
                        .eq(query.getAppId() != null, AppBuilderAutomation::getAppId, query.getAppId())
                        .eq(query.getModelId() != null, AppBuilderAutomation::getModelId, query.getModelId())
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderAutomation::getRuleName, query.getKeywords())
                        .orderByDesc(AppBuilderAutomation::getUpdateTime, AppBuilderAutomation::getId)));
    }

    @PostMapping("/automations")
    public Result<AppBuilderAutomation> saveAutomation(@RequestBody AppBuilderAutomation row) {
        if (StrUtil.isBlank(row.getStatus())) row.setStatus("ENABLED");
        if (row.getId() == null) automationMapper.insert(row); else automationMapper.updateById(row);
        return Result.success(row);
    }

    @PutMapping("/automations/{id}")
    public Result<AppBuilderAutomation> updateAutomation(@PathVariable Long id, @RequestBody AppBuilderAutomation row) {
        row.setId(id);
        automationMapper.updateById(row);
        return Result.success(row);
    }

    @DeleteMapping("/automations/{id}")
    public Result deleteAutomation(@PathVariable Long id) {
        return Result.judge(automationMapper.deleteById(id) > 0);
    }

    @GetMapping("/notifications/page")
    public PageResult<AppBuilderNotification> getNotificationPage(QueryRequest query) {
        return PageResult.success(notificationMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderNotification>()
                        .eq(query.getAppId() != null, AppBuilderNotification::getAppId, query.getAppId())
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderNotification::getTitle, query.getKeywords())
                        .orderByDesc(AppBuilderNotification::getCreateTime, AppBuilderNotification::getId)));
    }

    @PutMapping("/notifications/{id}/read")
    public Result markNotificationRead(@PathVariable Long id) {
        AppBuilderNotification notification = notificationMapper.selectById(id);
        notification.setStatus("READ");
        return Result.judge(notificationMapper.updateById(notification) > 0);
    }

    @DeleteMapping("/notifications/{id}")
    public Result deleteNotification(@PathVariable Long id) {
        return Result.judge(notificationMapper.deleteById(id) > 0);
    }

    @GetMapping("/templates/page")
    public PageResult<AppBuilderTemplate> getTemplatePage(QueryRequest query) {
        return PageResult.success(templateMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderTemplate>()
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderTemplate::getTemplateName, query.getKeywords())
                        .orderByDesc(AppBuilderTemplate::getUpdateTime, AppBuilderTemplate::getId)));
    }

    @PostMapping("/templates")
    public Result<AppBuilderTemplate> saveTemplate(@RequestBody AppBuilderTemplate row) {
        if (StrUtil.isBlank(row.getStatus())) row.setStatus("PUBLISHED");
        if (row.getId() == null) templateMapper.insert(row); else templateMapper.updateById(row);
        return Result.success(row);
    }

    @PutMapping("/templates/{id}")
    public Result<AppBuilderTemplate> updateTemplate(@PathVariable Long id, @RequestBody AppBuilderTemplate row) {
        row.setId(id);
        templateMapper.updateById(row);
        return Result.success(row);
    }

    @PostMapping("/templates/{id}/create-app")
    public Result<AppBuilderApp> createAppFromTemplate(@PathVariable Long id) {
        AppBuilderTemplate template = templateMapper.selectById(id);
        return Result.success(appService.createFromTemplate(template));
    }

    @GetMapping("/templates/{id}/preview")
    public Result<Map<String, Object>> previewTemplate(@PathVariable Long id) {
        AppBuilderTemplate template = templateMapper.selectById(id);
        Map<String, Object> snapshot = parseMap(template.getConfigJson());
        return Result.success(Map.of(
                "templateId", template.getId(),
                "templateName", template.getTemplateName(),
                "category", StrUtil.blankToDefault(template.getCategory(), ""),
                "models", listSize(snapshot.get("models")),
                "forms", listSize(snapshot.get("forms")),
                "pages", listSize(snapshot.get("pages")),
                "reports", listSize(snapshot.get("reports")),
                "apis", listSize(snapshot.get("apis")),
                "automations", listSize(snapshot.get("automations"))
        ));
    }

    @GetMapping("/templates/{id}/export")
    public Result<AppBuilderTemplate> exportTemplate(@PathVariable Long id) {
        return Result.success(templateMapper.selectById(id));
    }

    @PostMapping("/templates/import")
    public Result<AppBuilderTemplate> importTemplate(@RequestBody AppBuilderTemplate row) {
        row.setId(null);
        if (StrUtil.isBlank(row.getStatus())) row.setStatus("PUBLISHED");
        templateMapper.insert(row);
        return Result.success(row);
    }

    @DeleteMapping("/templates/{id}")
    public Result deleteTemplate(@PathVariable Long id) {
        return Result.judge(templateMapper.deleteById(id) > 0);
    }

    @PostMapping("/ai/generate-form")
    public Result<Map<String, Object>> generateForm(@RequestBody AiRequest request) {
        String name = StrUtil.blankToDefault(request.getPrompt(), "业务表单");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("config", Map.of(
                "formName", name.length() > 20 ? name.substring(0, 20) : name,
                "labelWidth", 100,
                "labelPosition", "right",
                "size", "default",
                "gutter", 16,
                "submitText", "提交",
                "resetText", "重置",
                "showButtons", true
        ));
        schema.put("fields", java.util.List.of(
                field("field_name", "名称", "input", true),
                field("field_type", "类型", "select", false),
                field("field_remark", "说明", "textarea", false)
        ));
        return Result.success(schema);
    }

    @PostMapping("/ai/generate-sql")
    public Result<Map<String, String>> generateSql(@RequestBody AiRequest request) {
        String table = StrUtil.blankToDefault(request.getCode(), "app_builder_demo");
        String sql = "SELECT JSON_EXTRACT(data_json, '$.name') AS name, COUNT(*) AS total FROM app_builder_data WHERE model_id = ? GROUP BY name";
        return Result.success(Map.of("table", table, "sql", sql, "prompt", StrUtil.blankToDefault(request.getPrompt(), "")));
    }

    private Map<String, Object> field(String field, String label, String type, boolean required) {
        return Map.of("id", field, "field", field, "label", label, "type", type, "span", 24, "required", required);
    }

    private Map<String, Object> parseMap(String json) {
        if (StrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("API配置JSON格式不正确", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || StrUtil.isBlank(String.valueOf(value)) ? null : Long.valueOf(String.valueOf(value));
    }

    private Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return 0;
        }
        return Double.valueOf(String.valueOf(value));
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int listSize(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    @GetMapping("/versions/page")
    public PageResult<AppBuilderVersion> getVersionPage(QueryRequest query) {
        return PageResult.success(versionMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderVersion>()
                        .eq(query.getAppId() != null, AppBuilderVersion::getAppId, query.getAppId())
                        .orderByDesc(AppBuilderVersion::getCreateTime, AppBuilderVersion::getId)));
    }

    @PostMapping("/versions")
    public Result<AppBuilderVersion> saveVersion(@RequestBody AppBuilderVersion row) {
        if (StrUtil.isBlank(row.getPublishStatus())) row.setPublishStatus("DRAFT");
        if (row.getId() == null) versionMapper.insert(row); else versionMapper.updateById(row);
        return Result.success(row);
    }

    @PostMapping("/versions/{id}/rollback")
    public Result<AppBuilderVersion> rollbackVersion(@PathVariable Long id) {
        return Result.success(appService.rollbackVersion(id));
    }

    @GetMapping("/versions/compare")
    public Result<Map<String, Object>> compareVersions(@RequestParam Long sourceVersionId,
                                                       @RequestParam Long targetVersionId) {
        return Result.success(appService.compareVersions(sourceVersionId, targetVersionId));
    }

    @DeleteMapping("/versions/{id}")
    public Result deleteVersion(@PathVariable Long id) {
        return Result.judge(versionMapper.deleteById(id) > 0);
    }

    @GetMapping("/tenants/page")
    public PageResult<AppBuilderTenant> getTenantPage(QueryRequest query) {
        return PageResult.success(tenantMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderTenant>()
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderTenant::getTenantName, query.getKeywords())
                        .orderByDesc(AppBuilderTenant::getUpdateTime, AppBuilderTenant::getId)));
    }

    @PostMapping("/tenants")
    public Result<AppBuilderTenant> saveTenant(@RequestBody AppBuilderTenant row) {
        if (StrUtil.isBlank(row.getStatus())) row.setStatus("ENABLED");
        if (row.getId() == null) tenantMapper.insert(row); else tenantMapper.updateById(row);
        return Result.success(row);
    }

    @PutMapping("/tenants/{id}")
    public Result<AppBuilderTenant> updateTenant(@PathVariable Long id, @RequestBody AppBuilderTenant row) {
        row.setId(id);
        tenantMapper.updateById(row);
        return Result.success(row);
    }

    @DeleteMapping("/tenants/{id}")
    public Result deleteTenant(@PathVariable Long id) {
        return Result.judge(tenantMapper.deleteById(id) > 0);
    }

    @GetMapping("/operation-logs/page")
    public PageResult<AppBuilderOperationLog> getOperationLogPage(QueryRequest query) {
        return PageResult.success(operationLogMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<AppBuilderOperationLog>()
                        .eq(query.getAppId() != null, AppBuilderOperationLog::getAppId, query.getAppId())
                        .like(StrUtil.isNotBlank(query.getKeywords()), AppBuilderOperationLog::getModuleName, query.getKeywords())
                        .orderByDesc(AppBuilderOperationLog::getCreateTime, AppBuilderOperationLog::getId)));
    }

    @PostMapping("/operation-logs")
    public Result<AppBuilderOperationLog> saveOperationLog(@RequestBody AppBuilderOperationLog row) {
        if (row.getSuccess() == null) row.setSuccess(1);
        operationLogMapper.insert(row);
        return Result.success(row);
    }

    @Data
    public static class QueryRequest {
        private int pageNum = 1;
        private int pageSize = 10;
        private Long appId;
        private Long modelId;
        private Long apiId;
        private String keywords;
    }

    @Data
    public static class AiRequest {
        private String prompt;
        private String code;
    }
}
