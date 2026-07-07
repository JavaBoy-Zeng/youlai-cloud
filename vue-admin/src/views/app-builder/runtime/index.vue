<script setup lang="ts">
defineOptions({ name: "AppBuilderRuntime" });

import {
  createData,
  deleteData,
  getDataPage,
  getModel,
  getRuntimePageConfig,
  listModelFields,
  updateData,
} from "@/api/app-builder";
import type {
  AppBuilderDataQuery,
  AppBuilderDataRow,
  AppBuilderModel,
  AppBuilderModelField,
  AppBuilderPage,
} from "@/api/app-builder/types";
import { useRoute } from "vue-router";

interface RuntimePageSchema {
  queryFields?: string[];
  tableFields?: string[];
  buttons?: string[];
}

const route = useRoute();
const pageId = Number(route.params.pageId);
const loading = ref(false);
const total = ref(0);
const pageConfig = ref<AppBuilderPage>();
const model = ref<AppBuilderModel>();
const fields = ref<AppBuilderModelField[]>([]);
const rows = ref<AppBuilderDataRow[]>([]);
const queryParams = reactive<AppBuilderDataQuery>({ pageNum: 1, pageSize: 10 });
const dialog = reactive({ visible: false, title: "新增数据" });
const form = reactive<AppBuilderDataRow>({});

const schema = computed<RuntimePageSchema>(() => {
  try {
    return JSON.parse(pageConfig.value?.pageSchema || "{}");
  } catch {
    return {};
  }
});

const queryFields = computed(() => pickFields(schema.value.queryFields));
const tableFields = computed(() => {
  const selected = pickFields(schema.value.tableFields);
  return selected.length ? selected : fields.value;
});
const buttons = computed(() => new Set(schema.value.buttons || ["create", "edit", "delete"]));

function pickFields(codes?: string[]) {
  if (!codes?.length) return [];
  return fields.value.filter(field => codes.includes(field.fieldCode));
}

async function loadPage() {
  if (!pageId) return;
  const { data: page } = await getRuntimePageConfig(pageId);
  pageConfig.value = page;
  if (!page.modelId) return;
  const [{ data: modelData }, { data: fieldData }] = await Promise.all([
    getModel(page.modelId),
    listModelFields(page.modelId),
  ]);
  model.value = modelData;
  fields.value = fieldData;
  handleQuery();
}

function handleQuery() {
  if (!pageConfig.value?.modelId) return;
  loading.value = true;
  getDataPage(pageConfig.value.modelId, queryParams)
    .then(({ data }) => {
      rows.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  queryParams.pageNum = 1;
  queryParams.keywords = undefined;
  handleQuery();
}

function openDialog(row?: AppBuilderDataRow) {
  dialog.visible = true;
  dialog.title = row?._id ? "编辑数据" : "新增数据";
  Object.keys(form).forEach(key => delete form[key]);
  fields.value.forEach(field => {
    form[field.fieldCode] = row?.[field.fieldCode] ?? field.defaultValue ?? "";
  });
  if (row?._id) {
    form._id = row._id;
  }
}

function submitForm() {
  if (!pageConfig.value?.modelId) return;
  const payload = Object.fromEntries(Object.entries(form).filter(([key]) => !key.startsWith("_")));
  const request = form._id ? updateData(form._id, payload) : createData(pageConfig.value.modelId, payload);
  request.then(() => {
    ElMessage.success("保存成功");
    dialog.visible = false;
    handleQuery();
  });
}

function remove(row: AppBuilderDataRow) {
  if (!row._id) return;
  ElMessageBox.confirm("确认删除该业务数据？", "提示", { type: "warning" }).then(() => {
    deleteData(row._id).then(() => {
      ElMessage.success("删除成功");
      handleQuery();
    });
  });
}

function fieldInputType(field: AppBuilderModelField) {
  if (field.fieldType === "number") return "number";
  if (field.fieldType === "textarea") return "textarea";
  return "text";
}

onMounted(loadPage);
</script>

<template>
  <div class="app-container runtime-page">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <div>
            <strong>{{ pageConfig?.pageName || "业务页面" }}</strong>
            <span class="sub-title">{{ model?.modelName }}</span>
          </div>
          <el-button v-if="buttons.has('create')" type="primary" @click="openDialog()">新增</el-button>
        </div>
      </template>

      <el-alert
        v-if="pageConfig && pageConfig.status !== 'PUBLISHED'"
        title="当前页面尚未发布，仅用于配置预览"
        type="warning"
        show-icon
        :closable="false"
        class="runtime-alert"
      />

      <el-form :inline="true" class="search-form">
        <el-form-item v-for="field in queryFields" :key="field.fieldCode" :label="field.fieldName">
          <el-input v-model="queryParams.keywords" clearable :placeholder="field.fieldName" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item v-if="!queryFields.length" label="关键字">
          <el-input v-model="queryParams.keywords" clearable placeholder="业务键/数据内容" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="_businessKey" label="业务键" min-width="170" />
        <el-table-column v-for="field in tableFields" :key="field.fieldCode" :prop="field.fieldCode" :label="field.fieldName" min-width="140" show-overflow-tooltip />
        <el-table-column prop="_status" label="状态" width="110" />
        <el-table-column prop="_updateTime" label="更新时间" width="170" />
        <el-table-column v-if="buttons.has('edit') || buttons.has('delete')" label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="buttons.has('edit')" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="buttons.has('delete')" link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="680px">
      <el-form :model="form" label-width="110px">
        <el-form-item v-for="field in fields" :key="field.fieldCode" :label="field.fieldName" :required="field.required === 1">
          <el-input
            v-model="form[field.fieldCode]"
            :type="fieldInputType(field)"
            :rows="field.fieldType === 'textarea' ? 4 : undefined"
            :placeholder="field.fieldCode"
          />
        </el-form-item>
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

.sub-title {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.runtime-alert {
  margin-bottom: 12px;
}

.search-form {
  margin-bottom: 8px;
}
</style>
