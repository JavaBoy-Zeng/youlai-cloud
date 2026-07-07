package com.youlai.flowable.controller;

import com.youlai.common.result.Result;
import com.youlai.flowable.model.entity.AppBuilderMenu;
import com.youlai.flowable.service.impl.AppBuilderMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "应用搭建-运行菜单接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('app-builder:*')")
@RequestMapping("/api/v1/app-builder/menus")
public class AppBuilderMenuController {

    private final AppBuilderMenuService menuService;

    @Operation(summary = "运行菜单清单")
    @GetMapping
    public Result<List<AppBuilderMenu>> listRuntimeMenus(@RequestParam(required = false) Long appId) {
        return Result.success(menuService.listRuntimeMenus(appId));
    }

    @Operation(summary = "刷新应用运行菜单")
    @PostMapping("/refresh/{appId}")
    public Result<Void> refreshAppMenus(@PathVariable Long appId) {
        menuService.refreshAppMenus(appId);
        return Result.success();
    }
}
