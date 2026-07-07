<template>
  <div class="app-container form-manager-page">
    <el-card shadow="never">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="表单标识">
          <el-input
            v-model="queryParams.formKey"
            clearable
            placeholder="请输入表单标识"
            style="width: 180px"
            @keyup.enter="searchQuery"
          />
        </el-form-item>
        <el-form-item label="表单名称">
          <el-input
            v-model="queryParams.formName"
            clearable
            placeholder="请输入表单名称"
            style="width: 180px"
            @keyup.enter="searchQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 128px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="searchQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button :icon="Refresh" @click="handleQuery">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openDesigner()">新增表单</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="formName" label="表单名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="formKey" label="表单标识" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openDesigner(row)">修改</el-button>
            <el-button link type="success" :icon="Promotion" @click="publishRemoteForm(row)">发布</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
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

    <el-dialog
      v-model="designerDialog.visible"
      :title="designerDialog.title"
      width="96vw"
      top="3vh"
      class="form-designer-dialog"
      destroy-on-close
      @opened="initSortable"
      @closed="handleDesignerClosed"
    >
      <div v-loading="designerLoading" class="form-designer-page">
        <aside class="designer-panel component-panel">
          <div class="panel-title">
            <span>表单组件</span>
            <el-tag size="small" type="info">Form Generator</el-tag>
          </div>

          <el-scrollbar>
            <section v-for="group in componentGroups" :key="group.title" class="component-group">
              <h3>{{ group.title }}</h3>
              <div class="component-list">
                <button
                  v-for="preset in group.list"
                  :key="preset.title"
                  class="component-item"
                  draggable="true"
                  @click="addField(preset)"
                  @dragstart="onPresetDragStart($event, preset)"
                >
                  <el-icon>
                    <component :is="preset.icon" />
                  </el-icon>
                  <span>{{ preset.title }}</span>
                </button>
              </div>
            </section>
          </el-scrollbar>
        </aside>

        <main class="canvas-panel">
          <div class="action-bar">
            <div>
              <strong>{{ formConfig.formName }}</strong>
              <span class="muted">
                {{ formMeta.formKey || "未保存" }} · 拖拽组件到画布，配置后可保存、发布或导出
              </span>
            </div>
            <div class="action-buttons">
              <el-button :icon="VideoPlay" @click="previewVisible = true">运行</el-button>
              <el-button :icon="DocumentChecked" type="primary" @click="saveRemoteForm">保存</el-button>
              <el-button
                :disabled="!formMeta.id"
                :icon="Promotion"
                type="success"
                @click="publishRemoteForm()"
              >
                发布
              </el-button>
              <el-button :icon="View" @click="openJsonDialog">查看 JSON</el-button>
              <el-button :icon="DocumentCopy" @click="copyVueCode">复制代码</el-button>
              <el-button :icon="Download" @click="downloadVueFile">导出 Vue</el-button>
              <el-button :icon="Delete" type="danger" plain @click="clearFields">清空</el-button>
            </div>
          </div>

          <el-scrollbar class="canvas-scrollbar">
            <div
              ref="canvasRef"
              class="drawing-board"
              :class="{ empty: !fields.length }"
              @dragover.prevent
              @drop="onCanvasDrop"
            >
              <div v-if="!fields.length" class="empty-state">
                <el-icon><Plus /></el-icon>
                <p>从左侧拖入或点选组件开始设计表单</p>
              </div>

              <div
                v-for="field in fields"
                :key="field.id"
                :data-id="field.id"
                class="drawing-item"
                :class="{ active: activeField?.id === field.id }"
                @click="selectField(field)"
              >
                <div class="drag-handle" title="拖动排序">
                  <el-icon><Rank /></el-icon>
                </div>
                <div class="field-preview">
                  <FormRenderer :schema="{ config: previewConfig, fields: [field] }" @submit="noop" />
                </div>
                <div class="item-actions">
                  <el-button :icon="CopyDocument" circle text @click.stop="copyField(field)" />
                  <el-button :icon="Delete" circle text type="danger" @click.stop="removeField(field.id)" />
                </div>
              </div>
            </div>
          </el-scrollbar>
        </main>

        <aside class="designer-panel property-panel">
          <el-tabs v-model="activeTab" stretch>
            <el-tab-pane label="组件属性" name="field" />
            <el-tab-pane label="表单属性" name="form" />
          </el-tabs>

          <el-scrollbar class="property-scrollbar">
            <el-form v-if="activeTab === 'field' && activeField" label-position="top" class="property-form">
              <el-form-item label="字段标题">
                <el-input v-model="activeField.label" @input="syncPlaceholder" />
              </el-form-item>
              <el-form-item label="字段编码">
                <el-input v-model="activeField.field" />
              </el-form-item>
              <el-form-item v-if="showPlaceholder(activeField.type)" label="占位提示">
                <el-input v-model="activeField.placeholder" />
              </el-form-item>
              <el-form-item label="栅格宽度">
                <el-slider v-model="activeField.span" :min="6" :max="24" :step="6" show-stops />
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="activeField.required">必填</el-checkbox>
                <el-checkbox v-model="activeField.disabled">禁用</el-checkbox>
                <el-checkbox v-if="activeField.clearable !== undefined" v-model="activeField.clearable">
                  可清空
                </el-checkbox>
                <el-checkbox v-if="activeField.type === 'select'" v-model="activeField.multiple">多选</el-checkbox>
              </el-form-item>

              <template v-if="['select', 'radio', 'checkbox'].includes(activeField.type)">
                <div class="sub-title">选项配置</div>
                <div v-for="(option, index) in activeField.options" :key="index" class="option-row">
                  <el-input v-model="option.label" placeholder="选项名" />
                  <el-input v-model="option.value" placeholder="选项值" />
                  <el-button :icon="Delete" circle plain @click="removeOption(index)" />
                </div>
                <el-button class="full-button" :icon="Plus" @click="addOption">添加选项</el-button>
              </template>

              <template v-if="['number', 'slider'].includes(activeField.type)">
                <el-form-item label="最小值">
                  <el-input-number v-model="activeField.min" />
                </el-form-item>
                <el-form-item label="最大值">
                  <el-input-number v-model="activeField.max" />
                </el-form-item>
                <el-form-item label="步长">
                  <el-input-number v-model="activeField.step" :min="1" />
                </el-form-item>
              </template>

              <el-form-item v-if="activeField.type === 'textarea'" label="文本行数">
                <el-input-number v-model="activeField.rows" :min="2" :max="12" />
              </el-form-item>

              <el-form-item v-if="activeField.type === 'rate'" label="最大评分">
                <el-input-number v-model="activeField.max" :min="1" :max="10" />
              </el-form-item>

              <template v-if="activeField.type === 'date' || activeField.type === 'daterange'">
                <el-form-item label="值格式">
                  <el-input v-model="activeField.valueFormat" />
                </el-form-item>
              </template>

              <template v-if="activeField.type === 'upload'">
                <el-form-item label="上传地址">
                  <el-input v-model="activeField.action" />
                </el-form-item>
                <el-form-item label="允许文件类型">
                  <el-input v-model="activeField.accept" placeholder="例如 image/*,.pdf" />
                </el-form-item>
              </template>
            </el-form>

            <div v-else-if="activeTab === 'field'" class="empty-property">选中画布中的组件后配置字段属性。</div>

            <el-form v-else label-position="top" class="property-form">
              <el-form-item label="表单标识">
                <el-input v-model="formMeta.formKey" placeholder="例如 leave_apply" />
              </el-form-item>
              <el-form-item label="表单名称">
                <el-input v-model="formConfig.formName" />
              </el-form-item>
              <el-form-item label="标签宽度">
                <el-input-number v-model="formConfig.labelWidth" :min="60" :max="220" />
              </el-form-item>
              <el-form-item label="标签位置">
                <el-radio-group v-model="formConfig.labelPosition">
                  <el-radio-button label="left">左侧</el-radio-button>
                  <el-radio-button label="right">右侧</el-radio-button>
                  <el-radio-button label="top">顶部</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="组件尺寸">
                <el-radio-group v-model="formConfig.size">
                  <el-radio-button label="large">大</el-radio-button>
                  <el-radio-button label="default">默认</el-radio-button>
                  <el-radio-button label="small">小</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="栅格间距">
                <el-input-number v-model="formConfig.gutter" :min="0" :max="40" />
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="formConfig.showButtons">显示提交按钮</el-checkbox>
              </el-form-item>
              <el-form-item label="提交按钮文字">
                <el-input v-model="formConfig.submitText" />
              </el-form-item>
              <el-form-item label="重置按钮文字">
                <el-input v-model="formConfig.resetText" />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="formMeta.remark" type="textarea" :rows="3" />
              </el-form-item>
            </el-form>
          </el-scrollbar>
        </aside>
      </div>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="运行预览" width="760px">
      <FormRenderer :schema="schema" @submit="onPreviewSubmit" />
    </el-dialog>

    <el-dialog v-model="jsonVisible" title="JSON Schema" width="760px">
      <el-input v-model="jsonText" type="textarea" :rows="18" spellcheck="false" />
      <template #footer>
        <el-button @click="jsonVisible = false">关闭</el-button>
        <el-button type="primary" @click="applyJson">应用 JSON</el-button>
        <el-button @click="downloadJson">导出 JSON</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import Sortable from "sortablejs";
import {
  CopyDocument,
  Delete,
  DocumentChecked,
  DocumentCopy,
  Download,
  Edit,
  Plus,
  Promotion,
  Rank,
  Refresh,
  Search,
  VideoPlay,
  View,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  deleteAppBuilderForm,
  getAppBuilderForm,
  getAppBuilderFormByKey,
  getAppBuilderFormPage,
  publishAppBuilderForm,
  saveAppBuilderForm,
  updateAppBuilderForm,
} from "@/api/app-builder/form";
import type { AppBuilderForm, AppBuilderFormQuery } from "@/api/app-builder/form/types";
import FormRenderer from "./renderer.vue";
import { componentGroups, createField, defaultFormConfig } from "./schema";
import { downloadText, generateVueCode } from "./generator";
import { cloneSerializable } from "./clone";
import type { AppBuilderField, AppBuilderFormConfig, AppBuilderFormSchema, ComponentPreset } from "./types";

defineOptions({
  name: "AppBuilderFormDesigner",
});

const STORAGE_KEY = "youlai-app-builder-form-designer";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const designerLoading = ref(false);
const total = ref(0);
const list = ref<AppBuilderForm[]>([]);
const queryParams = reactive<AppBuilderFormQuery>({
  pageNum: 1,
  pageSize: 10,
  formKey: "",
  formName: "",
  status: "",
});
const designerDialog = reactive({
  visible: false,
  title: "新增表单",
});
const fields = ref<AppBuilderField[]>([]);
const formConfig = reactive<AppBuilderFormConfig>(cloneSerializable(defaultFormConfig));
const formMeta = reactive({
  id: undefined as number | undefined,
  formKey: "",
  status: "DRAFT",
  remark: "",
});
const activeField = ref<AppBuilderField>();
const activeTab = ref<"field" | "form">("field");
const previewVisible = ref(false);
const jsonVisible = ref(false);
const jsonText = ref("");
const draggedPreset = ref<ComponentPreset>();
const canvasRef = ref<HTMLElement>();
let sortable: Sortable | undefined;

const schema = computed<AppBuilderFormSchema>(() => ({
  config: cloneSerializable(formConfig),
  fields: cloneSerializable(fields.value),
}));

const previewConfig = computed<AppBuilderFormConfig>(() => ({
  ...cloneSerializable(formConfig),
  showButtons: false,
}));

onMounted(() => {
  handleQuery();
  if (route.query.id || route.query.formKey) {
    openDesigner();
  }
});

onBeforeUnmount(() => {
  sortable?.destroy();
});

watch(
  schema,
  (value) => {
    if (designerDialog.visible && !formMeta.id) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
    }
  },
  { deep: true }
);

function statusLabel(status?: string) {
  return { DRAFT: "草稿", PUBLISHED: "已发布" }[status || "DRAFT"] || status;
}

function handleQuery() {
  loading.value = true;
  getAppBuilderFormPage(buildQueryParams())
    .then(({ data }) => {
      list.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function searchQuery() {
  queryParams.pageNum = 1;
  handleQuery();
}

function resetQuery() {
  queryParams.pageNum = 1;
  queryParams.pageSize = 10;
  queryParams.formKey = "";
  queryParams.formName = "";
  queryParams.status = "";
  handleQuery();
}

function buildQueryParams(): AppBuilderFormQuery {
  return {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    formKey: queryParams.formKey?.trim() || undefined,
    formName: queryParams.formName?.trim() || undefined,
    status: queryParams.status || undefined,
  };
}

async function openDesigner(row?: Partial<AppBuilderForm>) {
  resetDesigner();
  designerDialog.visible = true;
  designerDialog.title = row?.id ? `修改表单 - ${row.formName}` : "新增表单";

  const id = row?.id || Number(route.query.id);
  const formKey = row?.formKey || String(route.query.formKey || "");
  if (id || formKey) {
    await loadRemoteForm(id || undefined, formKey);
  } else {
    restoreSchema();
    activeTab.value = "form";
  }
}

function resetDesigner() {
  sortable?.destroy();
  sortable = undefined;
  fields.value = [];
  Object.assign(formConfig, cloneSerializable(defaultFormConfig));
  Object.assign(formMeta, {
    id: undefined,
    formKey: "",
    status: "DRAFT",
    remark: "",
  });
  activeField.value = undefined;
  activeTab.value = "field";
  previewVisible.value = false;
  jsonVisible.value = false;
  draggedPreset.value = undefined;
}

function initSortable() {
  sortable?.destroy();
  if (!canvasRef.value) {
    return;
  }

  sortable = Sortable.create(canvasRef.value, {
    animation: 180,
    handle: ".drag-handle",
    draggable: ".drawing-item",
    onEnd: (event) => {
      if (event.oldIndex === undefined || event.newIndex === undefined || event.oldIndex === event.newIndex) {
        return;
      }

      const next = [...fields.value];
      const [moved] = next.splice(event.oldIndex, 1);
      next.splice(event.newIndex, 0, moved);
      fields.value = next;
    },
  });
}

function handleDesignerClosed() {
  sortable?.destroy();
  sortable = undefined;
  router.replace({ path: route.path, query: {} });
}

function restoreSchema() {
  const cached = localStorage.getItem(STORAGE_KEY);
  if (!cached) {
    return;
  }

  try {
    const parsed = JSON.parse(cached) as AppBuilderFormSchema;
    applySchema(parsed);
  } catch {
    localStorage.removeItem(STORAGE_KEY);
  }
}

function applySchema(nextSchema: AppBuilderFormSchema) {
  if (!nextSchema?.config || !Array.isArray(nextSchema.fields)) {
    throw new Error("JSON Schema 格式不正确");
  }

  Object.assign(formConfig, defaultFormConfig, nextSchema.config);
  fields.value = nextSchema.fields;
  activeField.value = fields.value[0];
}

async function loadRemoteForm(id?: number, formKey?: string) {
  designerLoading.value = true;
  try {
    const { data } = id ? await getAppBuilderForm(id) : await getAppBuilderFormByKey(formKey || "");
    formMeta.id = data.id;
    formMeta.formKey = data.formKey;
    formMeta.status = data.status || "DRAFT";
    formMeta.remark = data.remark || "";
    applySchema(JSON.parse(data.formSchema) as AppBuilderFormSchema);
    activeTab.value = "form";
  } finally {
    designerLoading.value = false;
  }
}

async function saveRemoteForm() {
  if (!formMeta.formKey.trim()) {
    ElMessage.warning("请先填写表单标识");
    activeTab.value = "form";
    return;
  }
  if (!formConfig.formName.trim()) {
    ElMessage.warning("请先填写表单名称");
    activeTab.value = "form";
    return;
  }

  const payload = {
    id: formMeta.id,
    formKey: formMeta.formKey.trim(),
    formName: formConfig.formName.trim(),
    formSchema: JSON.stringify(schema.value, null, 2),
    status: formMeta.status,
    remark: formMeta.remark,
  };
  const { data } = formMeta.id ? await updateAppBuilderForm(formMeta.id, payload) : await saveAppBuilderForm(payload);
  formMeta.id = data.id;
  formMeta.formKey = data.formKey;
  formMeta.status = data.status || "DRAFT";
  formMeta.remark = data.remark || "";
  designerDialog.title = `修改表单 - ${data.formName}`;
  localStorage.removeItem(STORAGE_KEY);
  ElMessage.success("表单已保存");
  handleQuery();
}

async function publishRemoteForm(row?: Partial<AppBuilderForm>) {
  if (row?.id) {
    await publishAppBuilderForm(row.id);
    ElMessage.success("表单已发布");
    handleQuery();
    return;
  }

  if (!formMeta.id) {
    await saveRemoteForm();
  }
  if (!formMeta.id) {
    return;
  }
  const { data } = await publishAppBuilderForm(formMeta.id);
  formMeta.status = data.status || "PUBLISHED";
  ElMessage.success("表单已发布");
  handleQuery();
}

function remove(row: Partial<AppBuilderForm>) {
  if (!row.id) {
    return;
  }
  ElMessageBox.confirm("确认删除该表单？被模型或流程引用时需要先解绑。", "提示", { type: "warning" }).then(() => {
    deleteAppBuilderForm(row.id!).then(() => {
      ElMessage.success("删除成功");
      handleQuery();
    });
  });
}

function addField(preset: ComponentPreset) {
  const field = createField(preset.template, fields.value.length + 1);
  fields.value.push(field);
  selectField(field);
  nextTick(() => {
    canvasRef.value?.querySelector(`[data-id="${field.id}"]`)?.scrollIntoView({ block: "center" });
  });
}

function onPresetDragStart(event: DragEvent, preset: ComponentPreset) {
  draggedPreset.value = preset;
  event.dataTransfer?.setData("application/json", JSON.stringify(preset));
  event.dataTransfer?.setData("text/plain", preset.title);
}

function onCanvasDrop(event: DragEvent) {
  let preset = draggedPreset.value;

  if (!preset) {
    const raw = event.dataTransfer?.getData("application/json");
    preset = raw ? (JSON.parse(raw) as ComponentPreset) : undefined;
  }

  if (preset) {
    addField(preset);
  }

  draggedPreset.value = undefined;
}

function selectField(field: AppBuilderField) {
  activeField.value = field;
  activeTab.value = "field";
}

function copyField(field: AppBuilderField) {
  const fieldIndex = fields.value.findIndex((item) => item.id === field.id);
  const nextField = {
    ...cloneSerializable(field),
    id: `field_${Date.now().toString(36)}`,
    field: `${field.field}_copy`,
  };
  fields.value.splice(fieldIndex + 1, 0, nextField);
  selectField(nextField);
}

function removeField(id: string) {
  const index = fields.value.findIndex((field) => field.id === id);
  if (index < 0) {
    return;
  }

  fields.value.splice(index, 1);
  activeField.value = fields.value[Math.min(index, fields.value.length - 1)];
}

async function clearFields() {
  if (!fields.value.length) {
    return;
  }

  try {
    await ElMessageBox.confirm("确认清空当前表单设计吗？", "提示", {
      type: "warning",
    });
  } catch {
    return;
  }

  fields.value = [];
  activeField.value = undefined;
}

function addOption() {
  activeField.value?.options?.push({
    label: `选项${(activeField.value.options?.length ?? 0) + 1}`,
    value: `${(activeField.value.options?.length ?? 0) + 1}`,
  });
}

function removeOption(index: number) {
  activeField.value?.options?.splice(index, 1);
}

function syncPlaceholder() {
  if (activeField.value && showPlaceholder(activeField.value.type)) {
    activeField.value.placeholder = activeField.value.type === "select" ? "请选择" : "请输入";
  }
}

function showPlaceholder(type: AppBuilderField["type"]) {
  return ["input", "textarea", "password", "select", "date", "time"].includes(type);
}

function openJsonDialog() {
  jsonText.value = JSON.stringify(schema.value, null, 2);
  jsonVisible.value = true;
}

function applyJson() {
  try {
    applySchema(JSON.parse(jsonText.value) as AppBuilderFormSchema);
    jsonVisible.value = false;
    ElMessage.success("JSON 已应用");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "JSON 格式不正确");
  }
}

function downloadJson() {
  downloadText(`${formConfig.formName || "form-schema"}.json`, JSON.stringify(schema.value, null, 2));
}

async function copyVueCode() {
  await navigator.clipboard.writeText(generateVueCode(schema.value));
  ElMessage.success("Vue 代码已复制");
}

function downloadVueFile() {
  downloadText(`${formConfig.formName || "AppBuilderForm"}.vue`, generateVueCode(schema.value));
}

function onPreviewSubmit(model: Record<string, unknown>) {
  ElMessage.success(`提交数据：${JSON.stringify(model)}`);
}

function noop() {
  // Preview cards do not submit independently.
}
</script>

<style scoped lang="scss">
.form-manager-page {
  .search-form {
    margin-bottom: 12px;

    :deep(.el-form-item:last-child .el-form-item__content) {
      gap: 8px;
    }

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }
}

:deep(.form-designer-dialog) {
  .el-dialog__body {
    padding: 0;
  }
}

.form-designer-page {
  display: grid;
  grid-template-columns: 280px minmax(420px, 1fr) 320px;
  height: calc(94vh - 78px);
  min-height: 640px;
  background: #f5f7fb;
}

.designer-panel,
.canvas-panel {
  min-height: 0;
  background: #fff;
}

.component-panel {
  border-right: 1px solid #e5e7eb;
}

.property-panel {
  border-left: 1px solid #e5e7eb;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 15px;
  font-weight: 700;
}

.component-group {
  padding: 14px 16px 4px;

  h3 {
    margin: 0 0 10px;
    color: #64748b;
    font-size: 13px;
    font-weight: 700;
  }
}

.component-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.component-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  text-align: left;
  cursor: grab;

  &:hover {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.canvas-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
  padding: 10px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.muted {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.canvas-scrollbar {
  flex: 1;
}

.drawing-board {
  min-height: calc(94vh - 190px);
  margin: 16px;
  padding: 16px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background:
    linear-gradient(#fff, #fff) padding-box,
    repeating-linear-gradient(45deg, #eef2ff 0, #eef2ff 8px, #f8fafc 8px, #f8fafc 16px) border-box;
}

.drawing-board.empty {
  display: grid;
  place-items: center;
}

.empty-state {
  color: #94a3b8;
  text-align: center;

  .el-icon {
    font-size: 36px;
  }
}

.drawing-item {
  position: relative;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 8px;
  margin-bottom: 12px;
  padding: 14px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(15 23 42 / 8%);

  &.active {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px rgb(64 158 255 / 14%);
  }
}

.drag-handle {
  display: grid;
  place-items: center;
  color: #94a3b8;
  cursor: move;
}

.field-preview {
  min-width: 0;
}

.item-actions {
  display: flex;
  align-items: flex-start;
}

.property-scrollbar {
  height: calc(94vh - 142px);
}

.property-form,
.empty-property {
  padding: 16px;
}

.empty-property {
  color: #94a3b8;
}

.sub-title {
  margin: 18px 0 10px;
  color: #334155;
  font-weight: 700;
}

.option-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 32px;
  gap: 8px;
  margin-bottom: 8px;
}

.full-button {
  width: 100%;
}

@media (max-width: 1200px) {
  .form-designer-page {
    grid-template-columns: 220px minmax(360px, 1fr) 280px;
  }
}
</style>
