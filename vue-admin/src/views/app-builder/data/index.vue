<script setup lang="ts">
defineOptions({ name: "AppBuilderData" });

import {
  createData,
  deleteData,
  exportData,
  getDataPage,
  getModelPage,
  importData,
  listModelFields,
  submitDataApproval,
  updateData,
} from "@/api/app-builder";
import type {
  AppBuilderDataQuery,
  AppBuilderDataRow,
  AppBuilderModel,
  AppBuilderModelField,
} from "@/api/app-builder/types";
import { useRoute } from "vue-router";
import * as XLSX from "xlsx";

const route = useRoute();
const loading = ref(false);
const total = ref(0);
const models = ref<AppBuilderModel[]>([]);
const fields = ref<AppBuilderModelField[]>([]);
const rows = ref<AppBuilderDataRow[]>([]);
const currentModelId = ref<number | undefined>(Number(route.query.modelId) || undefined);
const queryParams = reactive<AppBuilderDataQuery>({
  pageNum: 1,
  pageSize: 10,
  keywords: typeof route.query.keywords === "string" ? route.query.keywords : undefined,
});
const dialog = reactive({ visible: false, title: "新增数据" });
const importDialog = reactive({ visible: false });
const form = reactive<AppBuilderDataRow>({});
const importText = ref("[]");
const currentModel = computed(() => models.value.find(item => item.id === currentModelId.value));
const flowEnabled = computed(() => currentModel.value?.enableFlow === 1 && !!currentModel.value?.processKey);

function loadModels() {
  getModelPage({ pageNum: 1, pageSize: 200 }).then(({ data }) => {
    models.value = data.list;
    if (!currentModelId.value && models.value[0]?.id) {
      currentModelId.value = models.value[0].id;
    }
    loadFields();
    handleQuery();
  });
}

function loadFields() {
  if (!currentModelId.value) return;
  listModelFields(currentModelId.value).then(({ data }) => {
    fields.value = data;
  });
}

function handleModelChange() {
  queryParams.pageNum = 1;
  loadFields();
  handleQuery();
}

function handleQuery() {
  if (!currentModelId.value) return;
  loading.value = true;
  getDataPage(currentModelId.value, queryParams)
    .then(({ data }) => {
      rows.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
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
  if (!currentModelId.value) return;
  const payload = Object.fromEntries(Object.entries(form).filter(([key]) => !key.startsWith("_")));
  const request = form._id ? updateData(form._id, payload) : createData(currentModelId.value, payload);
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

function canSubmit(row: AppBuilderDataRow) {
  return flowEnabled.value && ["DRAFT", "REJECTED", "REVOKED", undefined, ""].includes(row._status);
}

function submitApproval(row: AppBuilderDataRow) {
  if (!row._id) return;
  ElMessageBox.confirm("确认提交该业务数据进入审批流程？", "提示", { type: "warning" }).then(() => {
    submitDataApproval(row._id).then(() => {
      ElMessage.success("已提交审批");
      handleQuery();
    });
  });
}

function statusLabel(status?: string) {
  return {
    DRAFT: "草稿",
    APPROVING: "审批中",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    REVOKED: "已撤回",
    TERMINATED: "已终止",
  }[status || "DRAFT"];
}

function statusType(status?: string) {
  if (status === "APPROVED") return "success";
  if (status === "APPROVING") return "warning";
  if (status === "REJECTED" || status === "TERMINATED") return "danger";
  if (status === "REVOKED") return "info";
  return "info";
}

function submitImport() {
  if (!currentModelId.value) return;
  let data: AppBuilderDataRow[];
  try {
    data = JSON.parse(importText.value);
    if (!Array.isArray(data)) throw new Error();
  } catch {
    ElMessage.error("导入内容必须是 JSON 数组");
    return;
  }
  importData(currentModelId.value, data).then(() => {
    ElMessage.success("导入成功");
    importDialog.visible = false;
    handleQuery();
  });
}

async function handleExport() {
  if (!currentModelId.value) return;
  const { data } = await exportData(currentModelId.value, queryParams);
  await navigator.clipboard.writeText(JSON.stringify(data, null, 2));
  ElMessage.success("已导出到剪贴板");
}

async function handleExcelExport() {
  if (!currentModelId.value) return;
  const { data } = await exportData(currentModelId.value, queryParams);
  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, "业务数据");
  XLSX.writeFile(workbook, `app-builder-data-${currentModelId.value}.xlsx`);
}

function handleExcelImport(file: File) {
  if (!currentModelId.value) return false;
  const reader = new FileReader();
  reader.onload = event => {
    const workbook = XLSX.read(event.target?.result, { type: "array" });
    const sheetName = workbook.SheetNames[0];
    const rows = XLSX.utils.sheet_to_json<AppBuilderDataRow>(workbook.Sheets[sheetName]);
    importData(currentModelId.value!, rows).then(() => {
      ElMessage.success(`导入成功：${rows.length} 条`);
      handleQuery();
    });
  };
  reader.readAsArrayBuffer(file);
  return false;
}

function fieldInputType(field: AppBuilderModelField) {
  if (field.fieldType === "number") return "number";
  if (field.fieldType === "textarea") return "textarea";
  return "text";
}

onMounted(loadModels);
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>业务数据</span>
          <div>
            <el-upload :show-file-list="false" accept=".xlsx,.xls,.csv" :before-upload="handleExcelImport" class="toolbar-upload">
              <el-button>导入 Excel</el-button>
            </el-upload>
            <el-button @click="handleExcelExport">导出 Excel</el-button>
            <el-button @click="importDialog.visible = true">导入 JSON</el-button>
            <el-button @click="handleExport">复制 JSON</el-button>
            <el-button type="primary" :disabled="!currentModelId" @click="openDialog()">新增数据</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="模型">
          <el-select v-model="currentModelId" filterable placeholder="请选择模型" style="width: 220px" @change="handleModelChange">
            <el-option v-for="item in models" :key="item.id" :label="item.modelName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keywords" clearable placeholder="业务键/数据内容" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="_businessKey" label="业务键" min-width="170" />
        <el-table-column v-for="field in fields" :key="field.fieldCode" :prop="field.fieldCode" :label="field.fieldName" min-width="130" show-overflow-tooltip />
        <el-table-column prop="_status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row._status)">{{ statusLabel(row._status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="_updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canSubmit(row)" link type="success" @click="submitApproval(row)">提交审批</el-button>
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

    <el-dialog v-model="importDialog.visible" title="导入 JSON" width="720px">
      <el-input v-model="importText" type="textarea" :rows="14" spellcheck="false" />
      <template #footer>
        <el-button @click="importDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;

  > div {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.search-form {
  margin-bottom: 8px;
}

.toolbar-upload {
  display: inline-flex;
}
</style>
