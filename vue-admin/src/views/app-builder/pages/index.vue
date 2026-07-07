<script setup lang="ts">
defineOptions({ name: "AppBuilderPages" });

import {
  deletePageConfig,
  getAppPage,
  listModelFields,
  getModelPage,
  getPageConfigPage,
  publishPageConfig,
  savePageConfig,
  updatePageConfig,
} from "@/api/app-builder";
import type {
  AppBuilderApp,
  AppBuilderModelField,
  AppBuilderModel,
  AppBuilderPage,
  AppBuilderPageQuery,
} from "@/api/app-builder/types";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const total = ref(0);
const apps = ref<AppBuilderApp[]>([]);
const models = ref<AppBuilderModel[]>([]);
const list = ref<AppBuilderPage[]>([]);
const previewFields = ref<AppBuilderModelField[]>([]);
const queryParams = reactive<AppBuilderPageQuery>({
  pageNum: 1,
  pageSize: 10,
  modelId: Number(route.query.modelId) || undefined,
});
const dialog = reactive({ visible: false, title: "新增页面" });
const previewDialog = reactive({ visible: false, title: "页面预览" });
const form = reactive<AppBuilderPage>({
  appId: undefined,
  modelId: undefined,
  pageType: "LIST",
  pageName: "",
  pageSchema: "{}",
  status: "DRAFT",
  remark: "",
});
const previewPage = ref<AppBuilderPage>();

interface PreviewSchema {
  queryFields?: string[];
  tableFields?: string[];
  buttons?: string[];
}

const previewSchema = computed<PreviewSchema>(() => {
  try {
    return JSON.parse(previewPage.value?.pageSchema || "{}");
  } catch {
    return {};
  }
});

const previewQueryFields = computed(() => pickPreviewFields(previewSchema.value.queryFields));
const previewTableFields = computed(() => {
  const selected = pickPreviewFields(previewSchema.value.tableFields);
  return selected.length ? selected : previewFields.value;
});
const previewButtons = computed(() => new Set(previewSchema.value.buttons || ["create", "edit", "delete"]));
const previewRows = computed(() => [
  Object.fromEntries(previewTableFields.value.map(field => [field.fieldCode, field.defaultValue || field.fieldName + "示例"])),
]);

function loadOptions() {
  getAppPage({ pageNum: 1, pageSize: 100 }).then(({ data }) => (apps.value = data.list));
  getModelPage({ pageNum: 1, pageSize: 200 }).then(({ data }) => (models.value = data.list));
}

function handleQuery() {
  loading.value = true;
  getPageConfigPage(queryParams)
    .then(({ data }) => {
      list.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function openDialog(row?: any) {
  dialog.visible = true;
  dialog.title = row?.id ? "编辑页面" : "新增页面";
  Object.assign(
    form,
    row || {
      id: undefined,
      appId: queryParams.appId,
      modelId: queryParams.modelId,
      pageType: "LIST",
      pageName: "",
      pageSchema: JSON.stringify({ queryFields: [], tableFields: [], buttons: ["create", "edit", "delete"] }, null, 2),
      status: "DRAFT",
      remark: "",
    }
  );
}

function submitForm() {
  try {
    JSON.parse(form.pageSchema || "{}");
  } catch {
    ElMessage.error("页面 Schema 不是合法 JSON");
    return;
  }
  const request = form.id ? updatePageConfig(form.id, form) : savePageConfig(form);
  request.then(() => {
    ElMessage.success("保存成功");
    dialog.visible = false;
    handleQuery();
  });
}

function publish(row: any) {
  if (!row.id) return;
  publishPageConfig(row.id).then(() => {
    ElMessage.success("页面已发布");
    handleQuery();
  });
}

function openPreview(row?: Record<string, any>) {
  const source = row || form;
  try {
    JSON.parse(source.pageSchema || "{}");
  } catch {
    ElMessage.error("页面 Schema 不是合法 JSON");
    return;
  }
  previewPage.value = { ...source } as AppBuilderPage;
  previewDialog.title = `${source.pageName || "页面"}预览`;
  previewFields.value = [];
  previewDialog.visible = true;
  if (source.modelId) {
    listModelFields(source.modelId).then(({ data }) => {
      previewFields.value = data;
    });
  }
}

function openRuntime(row: any) {
  if (!row.id) return;
  router.push(`/app-builder/runtime/${row.id}`);
}

function remove(row: any) {
  if (!row.id) return;
  ElMessageBox.confirm("确认删除该页面配置？", "提示", { type: "warning" }).then(() => {
    deletePageConfig(row.id!).then(() => {
      ElMessage.success("删除成功");
      handleQuery();
    });
  });
}

function modelLabel(modelId?: number) {
  return models.value.find(item => item.id === modelId)?.modelName || modelId || "-";
}

function pickPreviewFields(codes?: string[]) {
  if (!codes?.length) return [];
  return previewFields.value.filter(field => codes.includes(field.fieldCode));
}

onMounted(() => {
  loadOptions();
  handleQuery();
});
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>页面配置</span>
          <div>
            <el-button @click="handleQuery">刷新</el-button>
            <el-button type="primary" @click="openDialog()">新增页面</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="应用">
          <el-select v-model="queryParams.appId" clearable filterable placeholder="全部应用" style="width: 180px">
            <el-option v-for="item in apps" :key="item.id" :label="item.appName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="queryParams.modelId" clearable filterable placeholder="全部模型" style="width: 180px">
            <el-option v-for="item in models" :key="item.id" :label="item.modelName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.pageType" clearable placeholder="全部" style="width: 120px">
            <el-option label="列表" value="LIST" />
            <el-option label="详情" value="DETAIL" />
            <el-option label="表单" value="FORM" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="pageName" label="页面名称" min-width="160" />
        <el-table-column label="模型" min-width="140">
          <template #default="{ row }">{{ modelLabel(row.modelId) }}</template>
        </el-table-column>
        <el-table-column prop="pageType" label="类型" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button link type="primary" @click="openRuntime(row)">运行</el-button>
            <el-button link type="success" @click="publish(row)">发布</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属应用">
          <el-select v-model="form.appId" clearable filterable placeholder="请选择应用" style="width: 100%">
            <el-option v-for="item in apps" :key="item.id" :label="item.appName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据模型">
          <el-select v-model="form.modelId" filterable placeholder="请选择模型" style="width: 100%">
            <el-option v-for="item in models" :key="item.id" :label="item.modelName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="页面名称"><el-input v-model="form.pageName" /></el-form-item>
        <el-form-item label="页面类型">
          <el-radio-group v-model="form.pageType">
            <el-radio-button label="LIST">列表</el-radio-button>
            <el-radio-button label="DETAIL">详情</el-radio-button>
            <el-radio-button label="FORM">表单</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="页面 Schema">
          <el-input v-model="form.pageSchema" type="textarea" :rows="12" spellcheck="false" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button @click="openPreview()">预览</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog.visible" :title="previewDialog.title" width="900px">
      <div class="preview-panel">
        <el-form :inline="true" class="search-form">
          <el-form-item v-for="field in previewQueryFields" :key="field.fieldCode" :label="field.fieldName">
            <el-input clearable :placeholder="field.fieldName" />
          </el-form-item>
          <el-form-item v-if="!previewQueryFields.length" label="关键字">
            <el-input clearable placeholder="业务键/数据内容" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary">查询</el-button>
            <el-button>重置</el-button>
          </el-form-item>
        </el-form>

        <div class="preview-actions">
          <el-button v-if="previewButtons.has('create')" type="primary">新增</el-button>
        </div>

        <el-table :data="previewRows" border>
          <el-table-column prop="_businessKey" label="业务键" min-width="150" />
          <el-table-column
            v-for="field in previewTableFields"
            :key="field.fieldCode"
            :prop="field.fieldCode"
            :label="field.fieldName"
            min-width="130"
            show-overflow-tooltip
          />
          <el-table-column prop="_status" label="状态" width="100">
            <template #default>草稿</template>
          </el-table-column>
          <el-table-column v-if="previewButtons.has('edit') || previewButtons.has('delete')" label="操作" width="130">
            <template #default>
              <el-button v-if="previewButtons.has('edit')" link type="primary">编辑</el-button>
              <el-button v-if="previewButtons.has('delete')" link type="danger">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
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

.preview-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
</style>
