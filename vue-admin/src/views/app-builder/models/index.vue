<script setup lang="ts">
defineOptions({ name: "AppBuilderModels" });

import {
  deleteModel,
  getAppPage,
  getModelPage,
  listModelFieldVersions,
  listModelFields,
  publishModel,
  saveModel,
  saveModelFields,
  updateModel,
} from "@/api/app-builder";
import type {
  AppBuilderApp,
  AppBuilderModel,
  AppBuilderModelField,
  AppBuilderModelFieldVersion,
  AppBuilderModelQuery,
} from "@/api/app-builder/types";
import { getAppBuilderFormPage } from "@/api/app-builder/form";
import type { AppBuilderForm } from "@/api/app-builder/form/types";
import { getWorkflowModelPage } from "@/api/workflow";
import type { WfModel } from "@/api/workflow/types";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const total = ref(0);
const list = ref<AppBuilderModel[]>([]);
const apps = ref<AppBuilderApp[]>([]);
const forms = ref<AppBuilderForm[]>([]);
const workflows = ref<WfModel[]>([]);
const queryParams = reactive<AppBuilderModelQuery>({
  pageNum: 1,
  pageSize: 10,
  appId: Number(route.query.appId) || undefined,
});
const dialog = reactive({ visible: false, title: "新增模型" });
const fieldDialog = reactive({ visible: false, title: "字段配置" });
const versionDialog = reactive({ visible: false, title: "字段版本" });
const form = reactive<AppBuilderModel>({
  appId: undefined,
  modelCode: "",
  modelName: "",
  tableName: "",
  mainField: "",
  enableFlow: 0,
  formKey: "",
  processKey: "",
  status: "DRAFT",
  remark: "",
});
const fields = ref<AppBuilderModelField[]>([]);
const fieldVersions = ref<AppBuilderModelFieldVersion[]>([]);
const currentModel = ref<AppBuilderModel>();

const fieldTypes = ["input", "textarea", "number", "select", "radio", "checkbox", "date", "datetime", "switch", "upload"];

function statusLabel(status?: string) {
  return { DRAFT: "草稿", PUBLISHED: "已发布" }[status || "DRAFT"] || status;
}

function loadOptions() {
  getAppPage({ pageNum: 1, pageSize: 100 }).then(({ data }) => (apps.value = data.list));
  getAppBuilderFormPage({ pageNum: 1, pageSize: 100, status: "PUBLISHED" }).then(({ data }) => (forms.value = data.list));
  getWorkflowModelPage({ pageNum: 1, pageSize: 100, status: "PUBLISHED" }).then(({ data }) => (workflows.value = data.list));
}

function handleQuery() {
  loading.value = true;
  getModelPage(queryParams)
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
  dialog.title = row?.id ? "编辑模型" : "新增模型";
  Object.assign(
    form,
    row || {
      id: undefined,
      appId: queryParams.appId,
      modelCode: "",
      modelName: "",
      tableName: "",
      mainField: "",
      enableFlow: 0,
      formKey: "",
      processKey: "",
      status: "DRAFT",
      remark: "",
    }
  );
}

function submitForm() {
  const request = form.id ? updateModel(form.id, form) : saveModel(form);
  request.then(() => {
    ElMessage.success("保存成功");
    dialog.visible = false;
    handleQuery();
  });
}

function publish(row: any) {
  if (!row.id) return;
  publishModel(row.id).then(() => {
    ElMessage.success("模型已发布");
    handleQuery();
  });
}

function remove(row: any) {
  if (!row.id) return;
  ElMessageBox.confirm("确认删除该模型？", "提示", { type: "warning" }).then(() => {
    deleteModel(row.id!).then(() => {
      ElMessage.success("删除成功");
      handleQuery();
    });
  });
}

function openFields(row: any) {
  currentModel.value = row;
  fieldDialog.visible = true;
  fieldDialog.title = `字段配置 - ${row.modelName}`;
  listModelFields(row.id!).then(({ data }) => {
    fields.value = data.length
      ? data
      : [
          {
            fieldCode: "name",
            fieldName: "名称",
            fieldType: "input",
            dbType: "varchar",
            required: 1,
            sortOrder: 1,
          },
        ];
  });
}

function addField() {
  fields.value.push({
    fieldCode: "",
    fieldName: "",
    fieldType: "input",
    dbType: "varchar",
    required: 0,
    sortOrder: fields.value.length + 1,
  });
}

function removeField(index: number) {
  fields.value.splice(index, 1);
}

function submitFields() {
  if (!currentModel.value?.id) return;
  saveModelFields(currentModel.value.id, fields.value).then(() => {
    ElMessage.success("字段已保存");
    fieldDialog.visible = false;
  });
}

function openFieldVersions() {
  if (!currentModel.value?.id) return;
  versionDialog.visible = true;
  versionDialog.title = `字段版本 - ${currentModel.value.modelName}`;
  listModelFieldVersions(currentModel.value.id).then(({ data }) => {
    fieldVersions.value = data;
  });
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
          <span>数据模型</span>
          <div>
            <el-button @click="handleQuery">刷新</el-button>
            <el-button type="primary" @click="openDialog()">新增模型</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="应用">
          <el-select v-model="queryParams.appId" clearable filterable placeholder="全部应用" style="width: 180px" @change="handleQuery">
            <el-option v-for="item in apps" :key="item.id" :label="item.appName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keywords" clearable placeholder="模型名称/编码" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="modelName" label="模型名称" min-width="150" />
        <el-table-column prop="modelCode" label="模型编码" min-width="130" />
        <el-table-column prop="tableName" label="逻辑表" min-width="130" />
        <el-table-column prop="formKey" label="表单" min-width="130" />
        <el-table-column prop="processKey" label="流程" min-width="130" />
        <el-table-column label="流程" width="80">
          <template #default="{ row }">{{ row.enableFlow ? "启用" : "关闭" }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag>{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="openFields(row)">字段</el-button>
            <el-button link type="primary" @click="router.push({ path: '/app-builder/pages', query: { modelId: row.id } })">页面</el-button>
            <el-button link type="primary" @click="router.push({ path: '/app-builder/data', query: { modelId: row.id } })">数据</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="680px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属应用">
          <el-select v-model="form.appId" filterable placeholder="请选择应用" style="width: 100%">
            <el-option v-for="item in apps" :key="item.id" :label="item.appName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="模型编码"><el-input v-model="form.modelCode" :disabled="Boolean(form.id)" /></el-form-item>
        <el-form-item label="主显示字段"><el-input v-model="form.mainField" placeholder="例如 name" /></el-form-item>
        <el-form-item label="绑定表单">
          <el-select v-model="form.formKey" allow-create clearable filterable placeholder="请选择或输入 formKey" style="width: 100%">
            <el-option v-for="item in forms" :key="item.formKey" :label="`${item.formName}（${item.formKey}）`" :value="item.formKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用流程"><el-switch v-model="form.enableFlow" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="绑定流程">
          <el-select v-model="form.processKey" allow-create clearable filterable placeholder="请选择或输入流程编码" style="width: 100%">
            <el-option v-for="item in workflows" :key="item.modelKey" :label="`${item.name}（${item.modelKey}）`" :value="item.modelKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldDialog.visible" :title="fieldDialog.title" width="960px">
      <el-table :data="fields" border>
        <el-table-column label="字段名称" min-width="130">
          <template #default="{ row }"><el-input v-model="row.fieldName" /></template>
        </el-table-column>
        <el-table-column label="字段编码" min-width="130">
          <template #default="{ row }"><el-input v-model="row.fieldCode" /></template>
        </el-table-column>
        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            <el-select v-model="row.fieldType">
              <el-option v-for="item in fieldTypes" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="必填" width="80">
          <template #default="{ row }"><el-switch v-model="row.required" :active-value="1" :inactive-value="0" /></template>
        </el-table-column>
        <el-table-column label="默认值" min-width="120">
          <template #default="{ row }"><el-input v-model="row.defaultValue" /></template>
        </el-table-column>
        <el-table-column label="排序" width="90">
          <template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }"><el-button link type="danger" @click="removeField($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-button class="add-field" @click="addField">添加字段</el-button>
      <template #footer>
        <el-button @click="fieldDialog.visible = false">取消</el-button>
        <el-button @click="openFieldVersions">版本</el-button>
        <el-button type="primary" @click="submitFields">保存字段</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionDialog.visible" :title="versionDialog.title" width="760px">
      <el-table :data="fieldVersions" border>
        <el-table-column prop="versionNo" label="版本" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="字段快照" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fieldsSnapshotJson }}</template>
        </el-table-column>
      </el-table>
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

.add-field {
  margin-top: 12px;
}
</style>
