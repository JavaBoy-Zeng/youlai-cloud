package com.youlai.flowable.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.flowable.mapper.AppBuilderMenuMapper;
import com.youlai.flowable.mapper.AppBuilderPageMapper;
import com.youlai.flowable.model.entity.AppBuilderMenu;
import com.youlai.flowable.model.entity.AppBuilderPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppBuilderMenuService extends ServiceImpl<AppBuilderMenuMapper, AppBuilderMenu> {

    private final AppBuilderPageMapper pageMapper;

    public List<AppBuilderMenu> listRuntimeMenus(Long appId) {
        return this.list(new LambdaQueryWrapper<AppBuilderMenu>()
                .eq(appId != null, AppBuilderMenu::getAppId, appId)
                .eq(AppBuilderMenu::getStatus, "ENABLED")
                .eq(AppBuilderMenu::getVisible, 1)
                .orderByAsc(AppBuilderMenu::getSortOrder, AppBuilderMenu::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void refreshAppMenus(Long appId) {
        Assert.notNull(appId, "应用ID不能为空");
        disableAppMenus(appId);
        List<AppBuilderPage> pages = pageMapper.selectList(new LambdaQueryWrapper<AppBuilderPage>()
                .eq(AppBuilderPage::getAppId, appId)
                .eq(AppBuilderPage::getStatus, "PUBLISHED")
                .orderByAsc(AppBuilderPage::getId));
        for (AppBuilderPage page : pages) {
            publishPageMenu(page);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AppBuilderMenu publishPageMenu(AppBuilderPage page) {
        Assert.notNull(page, "页面不能为空");
        Assert.notNull(page.getId(), "页面ID不能为空");
        Assert.notNull(page.getAppId(), "应用ID不能为空");

        AppBuilderMenu menu = this.getOne(new LambdaQueryWrapper<AppBuilderMenu>()
                .eq(AppBuilderMenu::getPageId, page.getId())
                .last("LIMIT 1"));
        if (menu == null) {
            menu = new AppBuilderMenu();
            menu.setPageId(page.getId());
            menu.setAppId(page.getAppId());
        }
        menu.setMenuName(page.getPageName());
        menu.setRoutePath("/app-builder/runtime/" + page.getId());
        menu.setRouteName("AppBuilderRuntime" + page.getId());
        menu.setComponent("app-builder/runtime/index");
        menu.setPerm("app-builder:runtime:" + page.getId() + ":view");
        menu.setIcon(StrUtil.blankToDefault(menu.getIcon(), "Document"));
        menu.setVisible(1);
        menu.setSortOrder(page.getId().intValue());
        menu.setStatus("PUBLISHED".equals(page.getStatus()) ? "ENABLED" : "DISABLED");
        menu.setRemark("页面发布时自动生成");
        this.saveOrUpdate(menu);
        return menu;
    }

    public void disablePageMenu(Long pageId) {
        AppBuilderMenu menu = this.getOne(new LambdaQueryWrapper<AppBuilderMenu>()
                .eq(AppBuilderMenu::getPageId, pageId)
                .last("LIMIT 1"));
        if (menu != null) {
            menu.setStatus("DISABLED");
            this.updateById(menu);
        }
    }

    public void disableAppMenus(Long appId) {
        List<AppBuilderMenu> menus = this.list(new LambdaQueryWrapper<AppBuilderMenu>()
                .eq(AppBuilderMenu::getAppId, appId));
        for (AppBuilderMenu menu : menus) {
            menu.setStatus("DISABLED");
            this.updateById(menu);
        }
    }
}
