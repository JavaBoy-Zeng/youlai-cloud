<script setup lang="ts">
defineOptions({ name: "AppBuilderExtensions" });

import {
  createAppFromTemplate,
  deleteExtension,
  generateAiForm,
  generateAiSql,
  getExtensionPage,
  exportTemplate,
  importTemplate,
  invokeApi,
  previewTemplate,
  publishExtension,
  runReport,
  rollbackVersion,
  saveExtension,
  updateExtension,
} from "@/api/app-builder/extensions";
import * as echarts from "echarts";

type ModuleConfig = {
  name: string;
  label: string;
  keyField: string;
  titleField: string;
  fields: Array<{ prop: string; label: string; type?: "textarea" | "select"; options?: string[] }>;
  publish?: boolean;
};

const modules: ModuleConfig[] = [
  {
    name: "reports",
    label: "报表仪表盘",
    keyField: "reportName",
    titleField: "reportName",
    publish: true,
    fields: [
      { prop: "reportName", label: "报表名称" },
      { prop: "reportType", label: "报表类型", type: "select", options: ["CHART", "TABLE", "CARD"] },
      { prop: "chartType", label: "图表类型", type: "select", options: ["bar", "line", "pie", "card", "table"] },
      { prop: "dataSourceJson", label: "数据源 JSON", type: "textarea" },
      { prop: "chartSchema", label: "图表 Schema", type: "textarea" },
      { prop: "remark", label: "备注", type: "textarea" },
    ],
  },
  {
    name: "apis",
    label: "API 集成",
    keyField: "apiCode",
    titleField: "apiName",
    fields: [
      { prop: "apiName", label: "接口名称" },
      { prop: "apiCode", label: "接口编码" },
      { prop: "method", label: "方法", type: "select", options: ["GET", "POST", "PUT", "DELETE"] },
      { prop: "url", label: "接口地址" },
      { prop: "headersJson", label: "请求头 JSON", type: "textarea" },
      { prop: "paramsJson", label: "参数映射 JSON", type: "textarea" },
      { prop: "bodyTemplate", label: "请求体模板", type: "textarea" },
      { prop: "retryTimes", label: "失败重试次数" },
      { prop: "timeoutMs", label: "超时毫秒" },
    ],
  },
  {
    name: "automations",
    label: "自动化工作流",
    keyField: "ruleName",
    titleField: "ruleName",
    fields: [
      { prop: "ruleName", label: "规则名称" },
      { prop: "triggerType", label: "触发类型", type: "select", options: ["DATA_CREATE", "DATA_UPDATE", "SCHEDULE", "PROCESS_DONE"] },
      { prop: "triggerConfigJson", label: "触发配置 JSON", type: "textarea" },
      { prop: "actionType", label: "动作类型", type: "select", options: ["WEBHOOK", "START_PROCESS", "SEND_MESSAGE", "CALL_API"] },
      { prop: "actionConfigJson", label: "动作配置 JSON", type: "textarea" },
      { prop: "status", label: "状态", type: "select", options: ["ENABLED", "DISABLED"] },
    ],
  },
  {
    name: "templates",
    label: "模板中心",
    keyField: "templateCode",
    titleField: "templateName",
    fields: [
      { prop: "templateName", label: "模板名称" },
      { prop: "templateCode", label: "模板编码" },
      { prop: "category", label: "分类" },
      { prop: "coverUrl", label: "封面地址" },
      { prop: "configJson", label: "模板配置 JSON", type: "textarea" },
      { prop: "remark", label: "备注", type: "textarea" },
    ],
  },
  {
    name: "notifications",
    label: "通知消息",
    keyField: "receiverUsername",
    titleField: "title",
    fields: [],
  },
  {
    name: "versions",
    label: "版本管理",
    keyField: "versionNo",
    titleField: "versionName",
    fields: [
      { prop: "appId", label: "应用ID" },
      { prop: "versionNo", label: "版本号" },
      { prop: "versionName", label: "版本名称" },
      { prop: "configSnapshotJson", label: "配置快照 JSON", type: "textarea" },
      { prop: "publishStatus", label: "发布状态", type: "select", options: ["DRAFT", "PUBLISHED", "ROLLBACK"] },
      { prop: "remark", label: "备注", type: "textarea" },
    ],
  },
  {
    name: "tenants",
    label: "多租户",
    keyField: "tenantCode",
    titleField: "tenantName",
    fields: [
      { prop: "tenantName", label: "租户名称" },
      { prop: "tenantCode", label: "租户编码" },
      { prop: "planCode", label: "套餐编码" },
      { prop: "isolationMode", label: "隔离模式", type: "select", options: ["SHARED_SCHEMA", "DATABASE", "SCHEMA"] },
      { prop: "status", label: "状态", type: "select", options: ["ENABLED", "DISABLED"] },
      { prop: "remark", label: "备注", type: "textarea" },
    ],
  },
  {
    name: "operation-logs",
    label: "操作日志",
    keyField: "moduleName",
    titleField: "operationType",
    fields: [
      { prop: "appId", label: "应用ID" },
      { prop: "moduleName", label: "模块" },
      { prop: "operationType", label: "操作类型" },
      { prop: "operator", label: "操作人" },
      { prop: "contentJson", label: "内容 JSON", type: "textarea" },
      { prop: "remark", label: "备注", type: "textarea" },
    ],
  },
  {
    name: "api-logs",
    label: "API 日志",
    keyField: "apiId",
    titleField: "success",
    fields: [],
  },
];

const activeModule = ref("reports");
const loading = ref(false);
const total = ref(0);
const rows = ref<Record<string, any>[]>([]);
const queryParams = reactive({ pageNum: 1, pageSize: 10, keywords: "" });
const dialog = reactive({ visible: false, title: "" });
const reportDialog = reactive({ visible: false, title: "" });
const templateDialog = reactive({ visible: false, title: "模板预览" });
const importDialog = reactive({ visible: false });
const form = reactive<Record<string, any>>({});
const aiPrompt = ref("请假申请：请假类型、开始日期、结束日期、请假原因");
const aiResult = ref("");
const reportResult = ref<Record<string, any>>();
const templatePreview = ref<Record<string, any>>();
const importText = ref("{}");
const chartRef = ref<HTMLDivElement>();

const currentConfig = computed(() => modules.find(item => item.name === activeModule.value) || modules[0]);

function loadData() {
  loading.value = true;
  getExtensionPage(activeModule.value, queryParams)
    .then(({ data }) => {
      rows.value = data.list;
      total.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleTabChange() {
  queryParams.pageNum = 1;
  loadData();
}

function openDialog(row?: Record<string, any>) {
  Object.keys(form).forEach(key => delete form[key]);
  currentConfig.value.fields.forEach(field => {
    form[field.prop] = row?.[field.prop] ?? defaultValue(field.prop);
  });
  if (row?.id) form.id = row.id;
  dialog.title = row?.id ? `编辑${currentConfig.value.label}` : `新增${currentConfig.value.label}`;
  dialog.visible = true;
}

function defaultValue(prop: string) {
  if (prop.endsWith("Json")) return "{}";
  if (prop === "method") return "GET";
  if (prop === "status") return "ENABLED";
  if (prop === "publishStatus") return "DRAFT";
  if (prop === "retryTimes") return 0;
  if (prop === "timeoutMs") return 10000;
  return "";
}

function submitForm() {
  const request = form.id ? updateExtension(activeModule.value, form.id, form) : saveExtension(activeModule.value, form);
  request.then(() => {
    ElMessage.success("保存成功");
    dialog.visible = false;
    loadData();
  });
}

function remove(row: Record<string, any>) {
  if (!row.id) return;
  ElMessageBox.confirm("确认删除该记录？", "提示", { type: "warning" }).then(() => {
    deleteExtension(activeModule.value, row.id).then(() => {
      ElMessage.success("删除成功");
      loadData();
    });
  });
}

function publish(row: Record<string, any>) {
  publishExtension(activeModule.value, row.id).then(() => {
    ElMessage.success("已发布");
    loadData();
  });
}

async function previewReport(row: Record<string, any>) {
  const { data } = await runReport(row.id);
  reportResult.value = data;
  reportDialog.title = data.reportName || "报表预览";
  reportDialog.visible = true;
  await nextTick();
  renderReportChart();
}

function renderReportChart() {
  if (!chartRef.value || !reportResult.value) return;
  const chart = echarts.init(chartRef.value);
  const series = reportResult.value.series || [];
  const chartType = reportResult.value.chartType || "bar";
  chart.setOption({
    tooltip: {},
    xAxis: chartType === "pie" ? undefined : { type: "category", data: series.map((item: any) => item.name) },
    yAxis: chartType === "pie" ? undefined : { type: "value" },
    series: [
      {
        type: chartType === "line" ? "line" : chartType === "pie" ? "pie" : "bar",
        data: chartType === "pie" ? series : series.map((item: any) => item.value),
      },
    ],
  });
}

function invoke(row: Record<string, any>) {
  invokeApi(row.id, {}).then(({ data }) => {
    ElMessage.success(data.success ? "调用完成" : "调用失败，已记录日志");
  });
}

function createFromTemplate(row: Record<string, any>) {
  createAppFromTemplate(row.id).then(() => ElMessage.success("已从模板创建应用草稿"));
}

async function previewTemplateRow(row: Record<string, any>) {
  const { data } = await previewTemplate(row.id);
  templatePreview.value = data;
  templateDialog.title = `${data.templateName || "模板"}预览`;
  templateDialog.visible = true;
}

async function exportTemplateRow(row: Record<string, any>) {
  const { data } = await exportTemplate(row.id);
  await navigator.clipboard.writeText(JSON.stringify(data, null, 2));
  ElMessage.success("模板 JSON 已复制到剪贴板");
}

function submitImportTemplate() {
  let payload: Record<string, any>;
  try {
    payload = JSON.parse(importText.value);
  } catch {
    ElMessage.error("导入内容必须是合法 JSON");
    return;
  }
  importTemplate(payload).then(() => {
    ElMessage.success("模板导入成功");
    importDialog.visible = false;
    loadData();
  });
}

function rollback(row: Record<string, any>) {
  rollbackVersion(row.id).then(() => {
    ElMessage.success("已标记回滚");
    loadData();
  });
}

async function aiGenerate(type: "form" | "sql") {
  const { data } = type === "form" ? await generateAiForm(aiPrompt.value) : await generateAiSql(aiPrompt.value);
  aiResult.value = JSON.stringify(data, null, 2);
}

onMounted(loadData);
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>扩展中心</span>
          <div>
            <el-input v-model="queryParams.keywords" clearable placeholder="关键字" @keyup.enter="loadData" />
            <el-button @click="loadData">查询</el-button>
            <el-button v-if="activeModule === 'templates'" @click="importDialog.visible = true">导入模板</el-button>
            <el-button v-if="!['api-logs', 'ai'].includes(activeModule)" type="primary" @click="openDialog()">新增</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeModule" @tab-change="handleTabChange">
        <el-tab-pane v-for="module in modules" :key="module.name" :label="module.label" :name="module.name" />
        <el-tab-pane label="AI 辅助" name="ai" />
      </el-tabs>

      <template v-if="activeModule === 'ai'">
        <el-row :gutter="12">
          <el-col :span="10">
            <el-input v-model="aiPrompt" type="textarea" :rows="12" />
            <div class="ai-actions">
              <el-button type="primary" @click="aiGenerate('form')">生成表单 Schema</el-button>
              <el-button @click="aiGenerate('sql')">生成统计 SQL</el-button>
            </div>
          </el-col>
          <el-col :span="14">
            <el-input v-model="aiResult" type="textarea" :rows="16" spellcheck="false" />
          </el-col>
        </el-row>
      </template>

      <template v-else>
        <el-table v-loading="loading" :data="rows" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column :prop="currentConfig.titleField" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column :prop="currentConfig.keyField" label="编码/类型" min-width="140" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="updateTime" label="更新时间" width="170" />
          <el-table-column label="操作" width="420" fixed="right">
            <template #default="{ row }">
              <el-button v-if="currentConfig.fields.length" link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button v-if="currentConfig.publish" link type="success" @click="publish(row)">发布</el-button>
              <el-button v-if="activeModule === 'reports'" link type="primary" @click="previewReport(row)">预览</el-button>
              <el-button v-if="activeModule === 'apis'" link type="primary" @click="invoke(row)">调用</el-button>
              <el-button v-if="activeModule === 'templates'" link type="primary" @click="previewTemplateRow(row)">预览</el-button>
              <el-button v-if="activeModule === 'templates'" link type="primary" @click="exportTemplateRow(row)">导出</el-button>
              <el-button v-if="activeModule === 'templates'" link type="primary" @click="createFromTemplate(row)">创建应用</el-button>
              <el-button v-if="activeModule === 'versions'" link type="warning" @click="rollback(row)">回滚</el-button>
              <el-button v-if="!['api-logs'].includes(activeModule)" link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-if="total > 0"
          v-model:total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="loadData"
        />
      </template>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px">
      <el-form :model="form" label-width="120px">
        <el-form-item v-for="field in currentConfig.fields" :key="field.prop" :label="field.label">
          <el-select v-if="field.type === 'select'" v-model="form[field.prop]" clearable filterable style="width: 100%">
            <el-option v-for="option in field.options || []" :key="option" :label="option" :value="option" />
          </el-select>
          <el-input v-else v-model="form[field.prop]" :type="field.type === 'textarea' ? 'textarea' : 'text'" :rows="field.type === 'textarea' ? 5 : undefined" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportDialog.visible" :title="reportDialog.title" width="860px" @opened="renderReportChart">
      <div ref="chartRef" class="report-chart"></div>
      <el-table :data="reportResult?.series || []" border>
        <el-table-column prop="name" label="维度" />
        <el-table-column prop="value" label="数值" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="templateDialog.visible" :title="templateDialog.title" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="分类">{{ templatePreview?.category || "-" }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ templatePreview?.models || 0 }}</el-descriptions-item>
        <el-descriptions-item label="表单">{{ templatePreview?.forms || 0 }}</el-descriptions-item>
        <el-descriptions-item label="页面">{{ templatePreview?.pages || 0 }}</el-descriptions-item>
        <el-descriptions-item label="报表">{{ templatePreview?.reports || 0 }}</el-descriptions-item>
        <el-descriptions-item label="API">{{ templatePreview?.apis || 0 }}</el-descriptions-item>
        <el-descriptions-item label="自动化">{{ templatePreview?.automations || 0 }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="importDialog.visible" title="导入模板" width="720px">
      <el-input v-model="importText" type="textarea" :rows="16" spellcheck="false" />
      <template #footer>
        <el-button @click="importDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitImportTemplate">导入</el-button>
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

.ai-actions {
  margin-top: 12px;
}

.report-chart {
  width: 100%;
  height: 360px;
  margin-bottom: 12px;
}
</style>
