package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.flowable.mapper.*;
import com.youlai.flowable.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppBuilderAppService extends ServiceImpl<AppBuilderAppMapper, AppBuilderApp> {

    private final AppBuilderModelMapper modelMapper;
    private final AppBuilderModelFieldMapper fieldMapper;
    private final AppBuilderFormMapper formMapper;
    private final AppBuilderPageMapper pageMapper;
    private final AppBuilderReportMapper reportMapper;
    private final AppBuilderApiMapper apiMapper;
    private final AppBuilderAutomationMapper automationMapper;
    private final AppBuilderVersionMapper versionMapper;
    private final ObjectMapper objectMapper;
    private final AppBuilderOperationLogService operationLogService;
    private final AppBuilderMenuService menuService;

    public AppBuilderApp saveApp(AppBuilderApp app) {
        Assert.isTrue(StrUtil.isNotBlank(app.getAppCode()), "应用编码不能为空");
        Assert.isTrue(StrUtil.isNotBlank(app.getAppName()), "应用名称不能为空");
        String appCode = StrUtil.trim(app.getAppCode());
        long count = this.count(new LambdaQueryWrapper<AppBuilderApp>()
                .ne(app.getId() != null, AppBuilderApp::getId, app.getId())
                .eq(AppBuilderApp::getAppCode, appCode));
        Assert.isTrue(count == 0, "应用编码已存在");
        app.setAppCode(appCode);
        if (StrUtil.isBlank(app.getStatus())) {
            app.setStatus("DRAFT");
        }
        boolean created = app.getId() == null;
        this.saveOrUpdate(app);
        operationLogService.record(app.getId(), "APP", created ? "CREATE" : "UPDATE", app, "保存应用");
        return app;
    }

    public AppBuilderApp copyApp(Long id) {
        AppBuilderApp source = this.getById(id);
        Assert.notNull(source, "应用不存在");
        AppBuilderApp target = new AppBuilderApp();
        target.setAppCode(source.getAppCode() + "_copy_" + System.currentTimeMillis());
        target.setAppName(source.getAppName() + "副本");
        target.setAppDesc(source.getAppDesc());
        target.setAppIcon(source.getAppIcon());
        target.setCategory(source.getCategory());
        target.setStatus("DRAFT");
        target.setRemark(source.getRemark());
        this.save(target);
        operationLogService.record(target.getId(), "APP", "COPY", Map.of("sourceId", id, "targetId", target.getId()), "复制应用");
        return target;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppBuilderApp createFromTemplate(AppBuilderTemplate template) {
        Assert.notNull(template, "模板不存在");
        if (StrUtil.isBlank(template.getConfigJson())) {
            AppBuilderApp app = new AppBuilderApp();
            app.setAppCode(template.getTemplateCode() + "_" + System.currentTimeMillis());
            app.setAppName(template.getTemplateName() + "应用");
            app.setAppDesc(template.getRemark());
            app.setCategory(template.getCategory());
            return saveApp(app);
        }

        Map<String, Object> snapshot = readSnapshot(template.getConfigJson());
        AppBuilderApp app = objectMapper.convertValue(snapshot.get("app"), AppBuilderApp.class);
        if (app == null) {
            app = new AppBuilderApp();
        }
        app.setId(null);
        app.setAppCode(template.getTemplateCode() + "_" + System.currentTimeMillis());
        app.setAppName(template.getTemplateName() + "应用");
        app.setCategory(StrUtil.blankToDefault(template.getCategory(), app.getCategory()));
        app.setStatus("DRAFT");
        saveApp(app);
        cloneSnapshotConfig(app.getId(), snapshot);
        operationLogService.record(app.getId(), "TEMPLATE", "CREATE_APP", Map.of("templateId", template.getId(), "appId", app.getId()), "从模板创建完整应用");
        return app;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppBuilderVersion publishApp(Long id) {
        AppBuilderApp app = this.getById(id);
        Assert.notNull(app, "应用不存在");
        Assert.isTrue(!"DISABLED".equals(app.getStatus()), "已停用应用不能直接发布");
        validatePublish(app);

        AppBuilderVersion version = new AppBuilderVersion();
        version.setAppId(app.getId());
        version.setVersionNo("v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        version.setVersionName(app.getAppName() + "发布快照");
        version.setPublishStatus("PUBLISHED");
        version.setConfigSnapshotJson(toJson(buildSnapshot(app)));
        version.setRemark("应用发布时自动生成");
        versionMapper.insert(version);

        app.setStatus("PUBLISHED");
        this.updateById(app);
        menuService.refreshAppMenus(app.getId());
        operationLogService.record(app.getId(), "APP", "PUBLISH", Map.of("appId", app.getId(), "versionId", version.getId()), "发布应用");
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppBuilderApp disableApp(Long id) {
        AppBuilderApp app = this.getById(id);
        Assert.notNull(app, "应用不存在");
        app.setStatus("DISABLED");
        this.updateById(app);
        menuService.disableAppMenus(app.getId());
        operationLogService.record(app.getId(), "APP", "DISABLE", app, "停用应用");
        return app;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppBuilderVersion rollbackVersion(Long versionId) {
        AppBuilderVersion version = versionMapper.selectById(versionId);
        Assert.notNull(version, "版本不存在");
        Assert.isTrue(StrUtil.isNotBlank(version.getConfigSnapshotJson()), "版本快照为空，不能回滚");

        Map<String, Object> snapshot = readSnapshot(version.getConfigSnapshotJson());
        restoreSnapshot(version.getAppId(), snapshot);
        version.setPublishStatus("ROLLBACK");
        versionMapper.updateById(version);
        operationLogService.record(version.getAppId(), "VERSION", "ROLLBACK", Map.of("versionId", versionId), "回滚版本");
        return version;
    }

    public Map<String, Object> compareVersions(Long sourceVersionId, Long targetVersionId) {
        AppBuilderVersion source = versionMapper.selectById(sourceVersionId);
        AppBuilderVersion target = versionMapper.selectById(targetVersionId);
        Assert.notNull(source, "源版本不存在");
        Assert.notNull(target, "目标版本不存在");
        Assert.isTrue(Objects.equals(source.getAppId(), target.getAppId()), "只能对比同一应用下的版本");

        Map<String, String> sourceFlat = flattenSnapshot(source.getConfigSnapshotJson());
        Map<String, String> targetFlat = flattenSnapshot(target.getConfigSnapshotJson());
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        for (Map.Entry<String, String> entry : targetFlat.entrySet()) {
            String path = entry.getKey();
            if (!sourceFlat.containsKey(path)) {
                added.add(Map.of("path", path, "newValue", entry.getValue()));
            } else if (!Objects.equals(sourceFlat.get(path), entry.getValue())) {
                changed.add(Map.of("path", path, "oldValue", sourceFlat.get(path), "newValue", entry.getValue()));
            }
        }
        for (Map.Entry<String, String> entry : sourceFlat.entrySet()) {
            String path = entry.getKey();
            if (!targetFlat.containsKey(path)) {
                removed.add(Map.of("path", path, "oldValue", entry.getValue()));
            }
        }

        return Map.of(
                "sourceVersion", source,
                "targetVersion", target,
                "added", added,
                "removed", removed,
                "changed", changed
        );
    }

    private void validatePublish(AppBuilderApp app) {
        List<AppBuilderModel> models = modelMapper.selectList(new LambdaQueryWrapper<AppBuilderModel>()
                .eq(AppBuilderModel::getAppId, app.getId()));
        Assert.isTrue(!models.isEmpty(), "请至少创建一个业务模型后再发布");

        List<AppBuilderForm> forms = formMapper.selectList(new LambdaQueryWrapper<AppBuilderForm>()
                .eq(AppBuilderForm::getAppId, app.getId()));
        Set<String> formKeys = new HashSet<>();
        for (AppBuilderForm form : forms) {
            if (StrUtil.isNotBlank(form.getFormKey())) {
                formKeys.add(form.getFormKey());
            }
        }

        for (AppBuilderModel model : models) {
            long fieldCount = fieldMapper.selectCount(new LambdaQueryWrapper<AppBuilderModelField>()
                    .eq(AppBuilderModelField::getModelId, model.getId()));
            Assert.isTrue(fieldCount > 0, "模型「" + model.getModelName() + "」至少需要一个字段");
            if (StrUtil.isNotBlank(model.getFormKey())) {
                Assert.isTrue(formKeys.contains(model.getFormKey()), "模型「" + model.getModelName() + "」绑定的表单不存在");
            }
        }
    }

    private Map<String, Object> buildSnapshot(AppBuilderApp app) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<Map<String, Object>> modelSnapshots = new ArrayList<>();
        List<AppBuilderModel> models = modelMapper.selectList(new LambdaQueryWrapper<AppBuilderModel>()
                .eq(AppBuilderModel::getAppId, app.getId())
                .orderByAsc(AppBuilderModel::getId));
        for (AppBuilderModel model : models) {
            Map<String, Object> modelSnapshot = new LinkedHashMap<>();
            modelSnapshot.put("model", model);
            modelSnapshot.put("fields", fieldMapper.selectList(new LambdaQueryWrapper<AppBuilderModelField>()
                    .eq(AppBuilderModelField::getModelId, model.getId())
                    .orderByAsc(AppBuilderModelField::getSortOrder, AppBuilderModelField::getId)));
            modelSnapshots.add(modelSnapshot);
        }

        snapshot.put("app", app);
        snapshot.put("models", modelSnapshots);
        snapshot.put("forms", formMapper.selectList(new LambdaQueryWrapper<AppBuilderForm>()
                .eq(AppBuilderForm::getAppId, app.getId())
                .orderByAsc(AppBuilderForm::getId)));
        snapshot.put("pages", pageMapper.selectList(new LambdaQueryWrapper<AppBuilderPage>()
                .eq(AppBuilderPage::getAppId, app.getId())
                .orderByAsc(AppBuilderPage::getId)));
        snapshot.put("reports", reportMapper.selectList(new LambdaQueryWrapper<AppBuilderReport>()
                .eq(AppBuilderReport::getAppId, app.getId())
                .orderByAsc(AppBuilderReport::getId)));
        snapshot.put("apis", apiMapper.selectList(new LambdaQueryWrapper<AppBuilderApi>()
                .eq(AppBuilderApi::getAppId, app.getId())
                .orderByAsc(AppBuilderApi::getId)));
        snapshot.put("automations", automationMapper.selectList(new LambdaQueryWrapper<AppBuilderAutomation>()
                .eq(AppBuilderAutomation::getAppId, app.getId())
                .orderByAsc(AppBuilderAutomation::getId)));
        return snapshot;
    }

    private void restoreSnapshot(Long appId, Map<String, Object> snapshot) {
        AppBuilderApp app = objectMapper.convertValue(snapshot.get("app"), AppBuilderApp.class);
        Assert.notNull(app, "版本快照缺少应用信息");
        app.setId(appId);
        app.setStatus("PUBLISHED");
        this.saveOrUpdate(app);

        List<AppBuilderModel> currentModels = modelMapper.selectList(new LambdaQueryWrapper<AppBuilderModel>()
                .eq(AppBuilderModel::getAppId, appId));
        List<Long> modelIds = new ArrayList<>(currentModels.stream().map(AppBuilderModel::getId).filter(Objects::nonNull).toList());
        List<Map<String, Object>> modelSnapshots = objectMapper.convertValue(snapshot.get("models"), new TypeReference<>() {
        });
        for (Map<String, Object> modelSnapshot : modelSnapshots) {
            AppBuilderModel model = objectMapper.convertValue(modelSnapshot.get("model"), AppBuilderModel.class);
            if (model.getId() != null) {
                modelIds.add(model.getId());
            }
        }
        if (!modelIds.isEmpty()) {
            fieldMapper.delete(new LambdaQueryWrapper<AppBuilderModelField>()
                    .in(AppBuilderModelField::getModelId, modelIds));
        }
        modelMapper.delete(new LambdaQueryWrapper<AppBuilderModel>().eq(AppBuilderModel::getAppId, appId));

        formMapper.delete(new LambdaQueryWrapper<AppBuilderForm>().eq(AppBuilderForm::getAppId, appId));
        pageMapper.delete(new LambdaQueryWrapper<AppBuilderPage>().eq(AppBuilderPage::getAppId, appId));
        reportMapper.delete(new LambdaQueryWrapper<AppBuilderReport>().eq(AppBuilderReport::getAppId, appId));
        apiMapper.delete(new LambdaQueryWrapper<AppBuilderApi>().eq(AppBuilderApi::getAppId, appId));
        automationMapper.delete(new LambdaQueryWrapper<AppBuilderAutomation>().eq(AppBuilderAutomation::getAppId, appId));

        for (Map<String, Object> modelSnapshot : modelSnapshots) {
            AppBuilderModel model = objectMapper.convertValue(modelSnapshot.get("model"), AppBuilderModel.class);
            model.setAppId(appId);
            modelMapper.insert(model);
            List<AppBuilderModelField> fields = objectMapper.convertValue(modelSnapshot.get("fields"), new TypeReference<>() {
            });
            for (AppBuilderModelField field : fields) {
                field.setModelId(model.getId());
                fieldMapper.insert(field);
            }
        }
        insertAllForms(appId, snapshot.get("forms"));
        insertAllPages(appId, snapshot.get("pages"));
        insertAllReports(appId, snapshot.get("reports"));
        insertAllApis(appId, snapshot.get("apis"));
        insertAllAutomations(appId, snapshot.get("automations"));
        menuService.refreshAppMenus(appId);
    }

    private void cloneSnapshotConfig(Long appId, Map<String, Object> snapshot) {
        Map<Long, Long> modelIdMap = new HashMap<>();
        List<Map<String, Object>> modelSnapshots = objectMapper.convertValue(snapshot.get("models"), new TypeReference<>() {
        });
        if (modelSnapshots == null) {
            modelSnapshots = List.of();
        }
        for (Map<String, Object> modelSnapshot : modelSnapshots) {
            AppBuilderModel model = objectMapper.convertValue(modelSnapshot.get("model"), AppBuilderModel.class);
            Long oldModelId = model.getId();
            model.setId(null);
            model.setAppId(appId);
            model.setStatus("DRAFT");
            modelMapper.insert(model);
            if (oldModelId != null) {
                modelIdMap.put(oldModelId, model.getId());
            }
            List<AppBuilderModelField> fields = objectMapper.convertValue(modelSnapshot.get("fields"), new TypeReference<>() {
            });
            for (AppBuilderModelField field : fields) {
                field.setId(null);
                field.setModelId(model.getId());
                fieldMapper.insert(field);
            }
        }
        cloneForms(appId, snapshot.get("forms"), modelIdMap);
        clonePages(appId, snapshot.get("pages"), modelIdMap);
        cloneReports(appId, snapshot.get("reports"), modelIdMap);
        cloneApis(appId, snapshot.get("apis"));
        cloneAutomations(appId, snapshot.get("automations"), modelIdMap);
    }

    private void cloneForms(Long appId, Object rows, Map<Long, Long> modelIdMap) {
        if (rows == null) return;
        List<AppBuilderForm> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderForm row : list) {
            row.setId(null);
            row.setAppId(appId);
            row.setModelId(remapModelId(row.getModelId(), modelIdMap));
            row.setStatus("DRAFT");
            formMapper.insert(row);
        }
    }

    private void clonePages(Long appId, Object rows, Map<Long, Long> modelIdMap) {
        if (rows == null) return;
        List<AppBuilderPage> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderPage row : list) {
            row.setId(null);
            row.setAppId(appId);
            row.setModelId(remapModelId(row.getModelId(), modelIdMap));
            row.setStatus("DRAFT");
            pageMapper.insert(row);
        }
    }

    private void cloneReports(Long appId, Object rows, Map<Long, Long> modelIdMap) {
        if (rows == null) return;
        List<AppBuilderReport> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderReport row : list) {
            row.setId(null);
            row.setAppId(appId);
            row.setModelId(remapModelId(row.getModelId(), modelIdMap));
            row.setStatus("DRAFT");
            reportMapper.insert(row);
        }
    }

    private void cloneApis(Long appId, Object rows) {
        if (rows == null) return;
        List<AppBuilderApi> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderApi row : list) {
            row.setId(null);
            row.setAppId(appId);
            apiMapper.insert(row);
        }
    }

    private void cloneAutomations(Long appId, Object rows, Map<Long, Long> modelIdMap) {
        if (rows == null) return;
        List<AppBuilderAutomation> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderAutomation row : list) {
            row.setId(null);
            row.setAppId(appId);
            row.setModelId(remapModelId(row.getModelId(), modelIdMap));
            row.setStatus("DISABLED");
            automationMapper.insert(row);
        }
    }

    private Long remapModelId(Long oldModelId, Map<Long, Long> modelIdMap) {
        return oldModelId == null ? null : modelIdMap.getOrDefault(oldModelId, oldModelId);
    }

    private void insertAllForms(Long appId, Object rows) {
        List<AppBuilderForm> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderForm row : list) {
            row.setAppId(appId);
            formMapper.insert(row);
        }
    }

    private void insertAllPages(Long appId, Object rows) {
        List<AppBuilderPage> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderPage row : list) {
            row.setAppId(appId);
            pageMapper.insert(row);
        }
    }

    private void insertAllReports(Long appId, Object rows) {
        List<AppBuilderReport> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderReport row : list) {
            row.setAppId(appId);
            reportMapper.insert(row);
        }
    }

    private void insertAllApis(Long appId, Object rows) {
        List<AppBuilderApi> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderApi row : list) {
            row.setAppId(appId);
            apiMapper.insert(row);
        }
    }

    private void insertAllAutomations(Long appId, Object rows) {
        List<AppBuilderAutomation> list = objectMapper.convertValue(rows, new TypeReference<>() {
        });
        for (AppBuilderAutomation row : list) {
            row.setAppId(appId);
            automationMapper.insert(row);
        }
    }

    private String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("应用发布快照生成失败", e);
        }
    }

    private Map<String, Object> readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("应用版本快照格式不正确", e);
        }
    }

    private Map<String, String> flattenSnapshot(String json) {
        Assert.isTrue(StrUtil.isNotBlank(json), "版本快照为空");
        try {
            Map<String, String> result = new TreeMap<>();
            flattenJson("", objectMapper.readTree(json), result);
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("应用版本快照格式不正确", e);
        }
    }

    private void flattenJson(String path, JsonNode node, Map<String, String> result) {
        if (node == null || node.isNull()) {
            result.put(path, "null");
            return;
        }
        if (node.isValueNode()) {
            result.put(path, node.asText());
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenJson(path + "[" + i + "]", node.get(i), result);
            }
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String childPath = StrUtil.isBlank(path) ? entry.getKey() : path + "." + entry.getKey();
            flattenJson(childPath, entry.getValue(), result);
        });
    }
}
