<script setup lang="ts">
defineOptions({ name: "AppBuilderApps" });

import {
  copyApp,
  deleteApp,
  disableApp,
  getAppPage,
  publishApp,
  refreshRuntimeMenus,
  saveApp,
  updateApp,
} from "@/api/app-builder";
import type { AppBuilderApp, AppBuilderAppQuery } from "@/api/app-builder/types";
import { useRouter } from "vue-router";

const router = useRouter();
const loading = ref(false);
const total = ref(0);
const list = ref<AppBuilderApp[]>([]);
const queryParams = reactive<AppBuilderAppQuery>({ pageNum: 1, pageSize: 10 });
const dialog = reactive({ visible: false, title: "新增应用" });
const form = reactive<AppBuilderApp>({
  appCode: "",
  appName: "",
  appDesc: "",
  appIcon: "project",
  category: "OA",
  status: "DRAFT",
  remark: "",
});

function statusType(status?: string) {
  if (status === "PUBLISHED") return "success";
  if (status === "DISABLED") return "warning";
  return "info";
}

function statusLabel(status?: string) {
  return { DRAFT: "草稿", PUBLISHED: "已发布", DISABLED: "已停用" }[status || "DRAFT"] || status;
}

function handleQuery() {
  loading.value = true;
  getAppPage(queryParams)
    .then(({ data }) => {
      list.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  queryParams.pageNum = 1;
  queryParams.keywords = undefined;
  queryParams.status = undefined;
  handleQuery();
}

function openDialog(row?: any) {
  dialog.visible = true;
  dialog.title = row?.id ? "编辑应用" : "新增应用";
  Object.assign(
    form,
    row || {
      id: undefined,
      appCode: "",
      appName: "",
      appDesc: "",
      appIcon: "project",
      category: "OA",
      status: "DRAFT",
      remark: "",
    }
  );
}

function submitForm() {
  const request = form.id ? updateApp(form.id, form) : saveApp(form);
  request.then(() => {
    ElMessage.success("保存成功");
    dialog.visible = false;
    handleQuery();
  });
}

function publish(row: any) {
  if (!row.id) return;
  publishApp(row.id).then(() => {
    ElMessage.success("应用已发布");
    handleQuery();
  });
}

function disable(row: any) {
  if (!row.id) return;
  disableApp(row.id).then(() => {
    ElMessage.success("应用已停用");
    handleQuery();
  });
}

function copy(row: any) {
  if (!row.id) return;
  copyApp(row.id).then(() => {
    ElMessage.success("已复制为新草稿");
    handleQuery();
  });
}

function refreshMenus(row: any) {
  if (!row.id) return;
  refreshRuntimeMenus(row.id).then(() => {
    ElMessage.success("运行菜单已刷新");
  });
}

function remove(row: any) {
  if (!row.id) return;
  ElMessageBox.confirm("确认删除该应用？", "提示", { type: "warning" }).then(() => {
    deleteApp(row.id!).then(() => {
      ElMessage.success("删除成功");
      handleQuery();
    });
  });
}

onMounted(handleQuery);
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>应用中心</span>
          <div>
            <el-button @click="resetQuery">重置</el-button>
            <el-button type="primary" @click="openDialog()">新增应用</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keywords" clearable placeholder="应用名称/编码" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="appName" label="应用名称" min-width="150" />
        <el-table-column prop="appCode" label="应用编码" min-width="140" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="appDesc" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="router.push({ path: '/app-builder/models', query: { appId: row.id } })">建模</el-button>
            <el-button link type="primary" @click="router.push('/app-builder/form')">表单</el-button>
            <el-button link type="success" @click="publish(row)">发布</el-button>
            <el-button link type="primary" @click="refreshMenus(row)">菜单</el-button>
            <el-button link type="warning" @click="disable(row)">停用</el-button>
            <el-button link type="primary" @click="copy(row)">复制</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-if="total > 0"
        v-model:total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="handleQuery"
      />
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="620px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="应用名称"><el-input v-model="form.appName" /></el-form-item>
        <el-form-item label="应用编码"><el-input v-model="form.appCode" :disabled="Boolean(form.id)" /></el-form-item>
        <el-form-item label="应用图标"><el-input v-model="form.appIcon" /></el-form-item>
        <el-form-item label="应用分类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="应用描述"><el-input v-model="form.appDesc" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.search-form {
  margin-bottom: 8px;
}
</style>
