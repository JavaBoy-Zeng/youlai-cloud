<template>
  <el-icon v-if="icon && icon.startsWith('el-icon')" class="sub-el-icon">
    <component :is="icon.replace('el-icon-', '')" />
  </el-icon>
  <svg-icon v-else-if="icon" :icon-class="icon" />
  <svg-icon v-else icon-class="menu" />
  <span v-if="title" class="menu-title-text ml-1" :title="translatedTitle">{{ translatedTitle }}</span>
  <span v-if="title" class="menu-title-short" :title="translatedTitle">{{ shortTitle }}</span>
</template>

<script setup lang="ts">
import { translateRouteTitle } from "@/utils/i18n";

const props = defineProps({
  icon: {
    type: String,
    default: "",
  },
  title: {
    type: String,
    default: "",
  },
});

const translatedTitle = computed(() => translateRouteTitle(props.title));
const shortTitle = computed(() => {
  const chars = Array.from(translatedTitle.value.trim());
  return chars[0] || "";
});
</script>

<style lang="scss" scoped>
.menu-title-short {
  display: none;
  width: 18px;
  margin-left: 4px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  color: currentcolor;
  text-align: center;
}

.sub-el-icon {
  width: 14px !important;
  margin-right: 0 !important;
  font-size: 14px !important;
  color: currentcolor;
}

:global(.hideSidebar) {
  .el-sub-menu,
  .el-menu-item {
    .svg-icon,
    .sub-el-icon {
      margin-left: 9px;
      margin-right: 0;
    }
  }

  .menu-title-text {
    display: none;
  }

  .menu-title-short {
    display: inline-block;
  }
}
</style>
