package com.youlai.flowable.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.entity.AppBuilderPage;
import com.youlai.flowable.service.impl.AppBuilderPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应用搭建-页面配置接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder/pages")
public class AppBuilderPageController {

    private final AppBuilderPageService pageService;

    @Operation(summary = "页面分页")
    @GetMapping("/page")
    public PageResult<AppBuilderPage> getPage(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) Long appId,
                                              @RequestParam(required = false) Long modelId,
                                              @RequestParam(required = false) String pageType,
                                              @RequestParam(required = false) String keywords) {
        return PageResult.success(pageService.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AppBuilderPage>()
                        .eq(appId != null, AppBuilderPage::getAppId, appId)
                        .eq(modelId != null, AppBuilderPage::getModelId, modelId)
                        .eq(StrUtil.isNotBlank(pageType), AppBuilderPage::getPageType, pageType)
                        .like(StrUtil.isNotBlank(keywords), AppBuilderPage::getPageName, keywords)
                        .orderByDesc(AppBuilderPage::getUpdateTime, AppBuilderPage::getId)));
    }

    @Operation(summary = "页面详情")
    @GetMapping("/{id}")
    public Result<AppBuilderPage> getPage(@PathVariable Long id) {
        return Result.success(pageService.getById(id));
    }

    @Operation(summary = "运行态页面详情")
    @GetMapping("/{id}/runtime")
    public Result<AppBuilderPage> getRuntimePage(@PathVariable Long id) {
        return Result.success(pageService.getRuntimePage(id));
    }

    @Operation(summary = "保存页面配置")
    @PostMapping
    public Result<AppBuilderPage> savePage(@RequestBody AppBuilderPage page) {
        return Result.success(pageService.savePage(page));
    }

    @Operation(summary = "修改页面配置")
    @PutMapping("/{id}")
    public Result<AppBuilderPage> updatePage(@PathVariable Long id, @RequestBody AppBuilderPage page) {
        page.setId(id);
        return Result.success(pageService.savePage(page));
    }

    @Operation(summary = "发布页面")
    @PostMapping("/{id}/publish")
    public Result<AppBuilderPage> publishPage(@PathVariable Long id) {
        return Result.success(pageService.publishPage(id));
    }

    @Operation(summary = "删除页面")
    @DeleteMapping("/{id}")
    public Result deletePage(@PathVariable Long id) {
        return Result.judge(pageService.deletePage(id));
    }
}
