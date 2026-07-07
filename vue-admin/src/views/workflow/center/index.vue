<script setup lang="ts">
defineOptions({
  name: "WorkflowCenter",
  inheritAttrs: false,
});

import {
  activateWorkflowModel,
  deleteWorkflowCategory,
  deleteWorkflowModel,
  getWorkflowModelPage,
  listWorkflowCategories,
  publishWorkflowModel,
  saveWorkflowCategory,
  saveWorkflowModel,
  startWorkflow,
  suspendWorkflowModel,
  updateWorkflowCategory,
  updateWorkflowModel,
} from "@/api/workflow";
import type { StartProcessForm, WfCategory, WfModel, WfModelQuery } from "@/api/workflow/types";
import { getAppBuilderFormByKey, getAppBuilderFormPage } from "@/api/app-builder/form";
import type { AppBuilderForm } from "@/api/app-builder/form/types";
import FormRenderer from "@/views/app-builder/form/renderer.vue";
import type { AppBuilderFormSchema } from "@/views/app-builder/form/types";
import { useRouter } from "vue-router";

const router = useRouter();
const loading = ref(false);
const total = ref(0);
const categories = ref<WfCategory[]>([]);
const modelList = ref<WfModel[]>([]);
const formOptions = ref<AppBuilderForm[]>([]);
const startFormSchema = ref<AppBuilderFormSchema>();
const startFormRenderer = ref<InstanceType<typeof FormRenderer>>();
const startFormLoading = ref(false);

const queryParams = reactive<WfModelQuery>({
  pageNum: 1,
  pageSize: 10,
});

const categoryDialog = reactive({
  visible: false,
  title: "新增分类",
});

const modelDialog = reactive({
  visible: false,
  title: "新增流程",
});

const startDialog = reactive({
  visible: false,
});

const categoryForm = reactive<WfCategory>({
  parentId: 0,
  name: "",
  code: "",
  sort: 1,
  status: 1,
});

const modelForm = reactive<WfModel>({
  categoryId: undefined,
  modelKey: "",
  name: "",
  formKey: "",
  configJson: "{}",
  remark: "",
});

const startForm = reactive<StartProcessForm>({
  modelId: undefined,
  businessKey: "",
  formDataJson: "{}",
  variables: {},
});

const currentModel = ref<WfModel>();

function statusType(status?: string) {
  if (status === "PUBLISHED") return "success";
  if (status === "SUSPENDED") return "warning";
  return "info";
}

function statusLabel(status?: string) {
  return {
    DRAFT: "草稿",
    PUBLISHED: "已发布",
    SUSPENDED: "已停用",
  }[status || "DRAFT"];
}

function loadCategories() {
  listWorkflowCategories().then(({ data }) => {
    categories.value = data;
  });
}

function loadFormOptions() {
  getAppBuilderFormPage({ pageNum: 1, pageSize: 100, status: "PUBLISHED" }).then(({ data }) => {
    formOptions.value = data.list;
  });
}

function handleQuery() {
  loading.value = true;
  getWorkflowModelPage(queryParams)
    .then(({ data }) => {
      modelList.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  queryParams.pageNum = 1;
  queryParams.keywords = undefined;
  queryParams.categoryId = undefined;
  queryParams.status = undefined;
  handleQuery();
}

function openCategoryDialog(row?: any) {
  categoryDialog.visible = true;
  categoryDialog.title = row?.id ? "修改分类" : "新增分类";
  Object.assign(categoryForm, row || { id: undefined, parentId: 0, name: "", code: "", sort: 1, status: 1 });
}

function submitCategory() {
  const request = categoryForm.id
    ? updateWorkflowCategory(categoryForm.id, categoryForm)
    : saveWorkflowCategory(categoryForm);
  request.then(() => {
    ElMessage.success("保存成功");
    categoryDialog.visible = false;
    loadCategories();
  });
}

function removeCategory(row: any) {
  if (!row.id) return;
  ElMessageBox.confirm("确认删除该流程分类？", "提示", { type: "warning" }).then(() => {
    deleteWorkflowCategory(row.id!).then(() => {
      ElMessage.success("删除成功");
      loadCategories();
    });
  });
}

function openModelDialog(row?: any) {
  modelDialog.visible = true;
  modelDialog.title = row?.id ? "修改流程" : "新增流程";
  Object.assign(
    modelForm,
    row || {
      id: undefined,
      categoryId: queryParams.categoryId,
      modelKey: "",
      name: "",
      formKey: "",
      configJson: "{}",
      remark: "",
    }
  );
}

function submitModel() {
  const request = modelForm.id ? updateWorkflowModel(modelForm.id, modelForm) : saveWorkflowModel(modelForm);
  request.then(() => {
    ElMessage.success("保存成功");
    modelDialog.visible = false;
    handleQuery();
  });
}

function openDesigner(row: any) {
  router.push({ path: "/workflow/designer", query: { id: row.id } });
}

function publish(row: any) {
  if (!row.id) return;
  publishWorkflowModel(row.id).then(() => {
    ElMessage.success("发布成功");
    handleQuery();
  });
}

function updateState(row: any, suspended: boolean) {
  if (!row.id) return;
  const request = suspended ? suspendWorkflowModel(row.id) : activateWorkflowModel(row.id);
  request.then(() => {
    ElMessage.success(suspended ? "已停用" : "已启用");
    handleQuery();
  });
}

function removeModel(row: any) {
  if (!row.id) return;
  ElMessageBox.confirm("确认删除该流程定义？已部署流程会同步删除。", "警告", { type: "warning" }).then(() => {
    deleteWorkflowModel(row.id!).then(() => {
      ElMessage.success("删除成功");
      handleQuery();
    });
  });
}

async function openStartDialog(row: any) {
  currentModel.value = row;
  startForm.modelId = row.id;
  startForm.businessKey = `${row.modelKey}-${Date.now()}`;
  startForm.formDataJson = "{}";
  startForm.variables = {};
  startFormSchema.value = undefined;
  startDialog.visible = true;
  if (!row.formKey) {
    return;
  }
  startFormLoading.value = true;
  try {
    const { data } = await getAppBuilderFormByKey(row.formKey);
    const schema = JSON.parse(data.formSchema) as AppBuilderFormSchema;
    startFormSchema.value = {
      ...schema,
      config: {
        ...schema.config,
        showButtons: false,
      },
    };
  } catch {
    startFormSchema.value = undefined;
  } finally {
    startFormLoading.value = false;
  }
}

async function submitStart() {
  if (startFormSchema.value) {
    const model = await startFormRenderer.value?.validateAndGetModel();
    if (!model) {
      return;
    }
    startForm.formDataJson = JSON.stringify(model);
  }
  startWorkflow(startForm).then(({ data }) => {
    ElMessage.success("流程已发起");
    startDialog.visible = false;
    router.push(`/workflow/detail/${data.processInstanceId}`);
  });
}

onMounted(() => {
  loadCategories();
  loadFormOptions();
  handleQuery();
});
</script>

<template>
  <div class="app-container workflow-center">
    <el-row :gutter="12">
      <el-col :span="5">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>流程分类</span>
              <el-button type="primary" link @click="openCategoryDialog()">新增</el-button>
            </div>
          </template>
          <el-menu :default-active="String(queryParams.categoryId || '')">
            <el-menu-item index="" @click="queryParams.categoryId = undefined; handleQuery()">全部流程</el-menu-item>
            <el-menu-item
              v-for="item in categories"
              :key="item.id"
              :index="String(item.id)"
              @click="queryParams.categoryId = item.id; handleQuery()"
            >
              <span>{{ item.name }}</span>
              <template #title>{{ item.name }}</template>
            </el-menu-item>
          </el-menu>
          <el-table :data="categories" size="small" border class="category-table">
            <el-table-column prop="name" label="名称" />
            <el-table-column label="操作" width="96">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCategoryDialog(row)">改</el-button>
                <el-button link type="danger" @click="removeCategory(row)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="19">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>流程定义</span>
              <div>
                <el-button @click="resetQuery">重置</el-button>
                <el-button type="primary" @click="openModelDialog()">新增流程</el-button>
              </div>
            </div>
          </template>

          <el-form :inline="true" :model="queryParams" class="search-form">
            <el-form-item label="关键字">
              <el-input v-model="queryParams.keywords" clearable placeholder="流程名称/编码" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 140px">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已停用" value="SUSPENDED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="modelList" border>
            <el-table-column prop="name" label="流程名称" min-width="150" />
            <el-table-column prop="modelKey" label="编码" min-width="140" />
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="formKey" label="表单" min-width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="170" />
            <el-table-column label="操作" width="330" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDesigner(row)">设计</el-button>
                <el-button link type="primary" @click="openModelDialog(row)">编辑</el-button>
                <el-button link type="success" @click="publish(row)">发布</el-button>
                <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="updateState(row, true)">停用</el-button>
                <el-button v-if="row.status === 'SUSPENDED'" link type="success" @click="updateState(row, false)">启用</el-button>
                <el-button :disabled="row.status !== 'PUBLISHED'" link type="primary" @click="openStartDialog(row)">发起</el-button>
                <el-button link type="danger" @click="removeModel(row)">删除</el-button>
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
      </el-col>
    </el-row>

    <el-dialog v-model="categoryDialog.visible" :title="categoryDialog.title" width="420px">
      <el-form :model="categoryForm" label-width="90px">
        <el-form-item label="名称"><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="categoryForm.code" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sort" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="modelDialog.visible" :title="modelDialog.title" width="620px">
      <el-form :model="modelForm" label-width="100px">
        <el-form-item label="流程分类">
          <el-select v-model="modelForm.categoryId" clearable placeholder="请选择">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程名称"><el-input v-model="modelForm.name" /></el-form-item>
        <el-form-item label="流程编码"><el-input v-model="modelForm.modelKey" :disabled="Boolean(modelForm.id)" /></el-form-item>
        <el-form-item label="绑定表单">
          <el-select
            v-model="modelForm.formKey"
            allow-create
            clearable
            filterable
            placeholder="请选择已发布表单，也可手动输入 formKey"
            style="width: 100%"
          >
            <el-option
              v-for="item in formOptions"
              :key="item.formKey"
              :label="`${item.formName}（${item.formKey}）`"
              :value="item.formKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="配置 JSON"><el-input v-model="modelForm.configJson" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="modelForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitModel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="startDialog.visible" title="发起流程" width="760px">
      <el-alert :closable="false" type="info" show-icon>
        当前流程：{{ currentModel?.name }}，表单标识：{{ currentModel?.formKey || "未绑定" }}
      </el-alert>
      <el-form :model="startForm" label-width="110px" class="start-form">
        <el-form-item label="业务键"><el-input v-model="startForm.businessKey" /></el-form-item>
        <div v-if="startFormSchema" v-loading="startFormLoading" class="runtime-form">
          <FormRenderer ref="startFormRenderer" :schema="startFormSchema" />
        </div>
        <el-form-item v-else label="表单数据 JSON">
          <el-input v-model="startForm.formDataJson" type="textarea" :rows="8" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitStart">发起</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.workflow-center {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .category-table,
  .start-form {
    margin-top: 12px;
  }

  .runtime-form {
    padding: 12px 0 0;
  }

  .search-form {
    margin-bottom: 8px;
  }
}
</style>
