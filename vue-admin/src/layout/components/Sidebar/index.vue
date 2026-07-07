<template>
  <div :class="{ 'has-logo': sidebarLogo }">
    <!--混合布局-->
    <div class="flex w-full" v-if="layout == 'mix'">
      <SidebarLogo v-if="sidebarLogo" :collapse="!appStore.sidebar.opened" />
      <SidebarMixTopMenu class="flex-1" />
      <NavbarRight />
    </div>
    <!--左侧布局 || 顶部布局 -->
    <template v-else>
      <SidebarLogo v-if="sidebarLogo" :collapse="!appStore.sidebar.opened" />
      <el-scrollbar :class="{ 'has-collapse-toggle': layout === 'left' }">
        <SidebarMenu :menu-list="permissionStore.routes" base-path="" />
      </el-scrollbar>
      <button
        v-if="layout === 'left'"
        class="sidebar-collapse-toggle"
        type="button"
        :title="appStore.sidebar.opened ? '收起菜单' : '展开菜单'"
        @click="toggleSidebar"
      >
        <hamburger :is-active="appStore.sidebar.opened" />
      </button>
      <NavbarRight v-if="layout === 'top'" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { useSettingsStore, usePermissionStore, useAppStore } from "@/store";

const appStore = useAppStore();
const settingsStore = useSettingsStore();
const permissionStore = usePermissionStore();

const sidebarLogo = computed(() => settingsStore.sidebarLogo);
const layout = computed(() => settingsStore.layout);

function toggleSidebar() {
  appStore.toggleSidebar();
}
</script>

<style lang="scss" scoped>
.has-logo {
  .el-scrollbar {
    height: calc(100vh - $navbar-height);
  }
}

.el-scrollbar.has-collapse-toggle {
  height: calc(100vh - $navbar-height - 44px);
}

.sidebar-collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 44px;
  padding: 0;
  color: var(--el-color-primary);
  cursor: pointer;
  background: var(--menu-background);
  border: 0;
  border-top: 1px solid rgb(255 255 255 / 8%);

  :deep(.hamburger) {
    color: currentcolor;
  }

  &:hover {
    background: rgb(255 255 255 / 8%);
  }
}
</style>
