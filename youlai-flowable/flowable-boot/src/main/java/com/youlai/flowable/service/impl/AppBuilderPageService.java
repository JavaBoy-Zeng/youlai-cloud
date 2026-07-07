package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.mapper.AppBuilderPageMapper;
import com.youlai.flowable.model.entity.AppBuilderPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppBuilderPageService extends ServiceImpl<AppBuilderPageMapper, AppBuilderPage> {

    private final AppBuilderModelService modelService;
    private final ObjectMapper objectMapper;
    private final AppBuilderOperationLogService operationLogService;
    private final AppBuilderAppService appService;
    private final AppBuilderMenuService menuService;

    public AppBuilderPage savePage(AppBuilderPage page) {
        Assert.notNull(page.getModelId(), "数据模型不能为空");
        var model = modelService.getById(page.getModelId());
        Assert.notNull(model, "模型不存在");
        if (page.getAppId() == null) {
            page.setAppId(model.getAppId());
        }
        Assert.isTrue(StrUtil.isNotBlank(page.getPageType()), "页面类型不能为空");
        Assert.isTrue(StrUtil.isNotBlank(page.getPageName()), "页面名称不能为空");
        if (StrUtil.isBlank(page.getPageSchema())) {
            page.setPageSchema("{}");
        }
        if (StrUtil.isBlank(page.getStatus())) {
            page.setStatus("DRAFT");
        }
        boolean created = page.getId() == null;
        this.saveOrUpdate(page);
        operationLogService.record(page.getAppId(), "PAGE", created ? "CREATE" : "UPDATE", page, "保存页面配置");
        return page;
    }

    public AppBuilderPage publishPage(Long id) {
        AppBuilderPage page = this.getById(id);
        Assert.notNull(page, "页面不存在");
        Assert.notNull(modelService.getById(page.getModelId()), "模型不存在");
        try {
            objectMapper.readValue(StrUtil.blankToDefault(page.getPageSchema(), "{}"), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("页面 Schema 不是合法 JSON", e);
        }
        page.setStatus("PUBLISHED");
        this.updateById(page);
        menuService.publishPageMenu(page);
        operationLogService.record(page.getAppId(), "PAGE", "PUBLISH", page, "发布页面");
        return page;
    }

    public AppBuilderPage getRuntimePage(Long id) {
        AppBuilderPage page = this.getById(id);
        Assert.notNull(page, "页面不存在");
        Assert.isTrue("PUBLISHED".equals(page.getStatus()), "页面未发布或已停用");
        var app = appService.getById(page.getAppId());
        Assert.notNull(app, "应用不存在");
        Assert.isTrue("PUBLISHED".equals(app.getStatus()), "应用未发布或已停用");
        return page;
    }

    public boolean deletePage(Long id) {
        AppBuilderPage page = this.getById(id);
        if (page == null) {
            return false;
        }
        boolean removed = this.removeById(id);
        if (removed) {
            menuService.disablePageMenu(id);
            operationLogService.record(page.getAppId(), "PAGE", "DELETE", page, "删除页面配置");
        }
        return removed;
    }
}
