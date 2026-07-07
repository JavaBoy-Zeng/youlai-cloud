package com.youlai.flowable.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.common.result.PageResult;
import com.youlai.common.result.Result;
import com.youlai.flowable.model.entity.AppBuilderApp;
import com.youlai.flowable.model.entity.AppBuilderVersion;
import com.youlai.flowable.service.impl.AppBuilderAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应用搭建-应用接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder/apps")
public class AppBuilderAppController {

    private final AppBuilderAppService appService;

    @Operation(summary = "应用分页")
    @GetMapping("/page")
    public PageResult<AppBuilderApp> getAppPage(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String keywords,
                                                @RequestParam(required = false) String status) {
        return PageResult.success(appService.page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AppBuilderApp>()
                        .eq(StrUtil.isNotBlank(status), AppBuilderApp::getStatus, status)
                        .and(StrUtil.isNotBlank(keywords), wrapper -> wrapper
                                .like(AppBuilderApp::getAppName, keywords)
                                .or()
                                .like(AppBuilderApp::getAppCode, keywords))
                        .orderByDesc(AppBuilderApp::getUpdateTime, AppBuilderApp::getId)));
    }

    @Operation(summary = "应用详情")
    @GetMapping("/{id}")
    public Result<AppBuilderApp> getApp(@PathVariable Long id) {
        return Result.success(appService.getById(id));
    }

    @Operation(summary = "新增应用")
    @PostMapping
    public Result<AppBuilderApp> saveApp(@RequestBody AppBuilderApp app) {
        return Result.success(appService.saveApp(app));
    }

    @Operation(summary = "修改应用")
    @PutMapping("/{id}")
    public Result<AppBuilderApp> updateApp(@PathVariable Long id, @RequestBody AppBuilderApp app) {
        app.setId(id);
        return Result.success(appService.saveApp(app));
    }

    @Operation(summary = "发布应用")
    @PostMapping("/{id}/publish")
    public Result<AppBuilderVersion> publishApp(@PathVariable Long id) {
        return Result.success(appService.publishApp(id));
    }

    @Operation(summary = "停用应用")
    @PostMapping("/{id}/disable")
    public Result<AppBuilderApp> disableApp(@PathVariable Long id) {
        return Result.success(appService.disableApp(id));
    }

    @Operation(summary = "复制应用")
    @PostMapping("/{id}/copy")
    public Result<AppBuilderApp> copyApp(@PathVariable Long id) {
        return Result.success(appService.copyApp(id));
    }

    @Operation(summary = "删除应用")
    @DeleteMapping("/{id}")
    public Result deleteApp(@PathVariable Long id) {
        return Result.judge(appService.removeById(id));
    }
}
