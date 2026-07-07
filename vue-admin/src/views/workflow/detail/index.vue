<script setup lang="ts">
defineOptions({
  name: "WorkflowDetail",
  inheritAttrs: false,
});

import BpmnViewer from "bpmn-js/lib/Viewer";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn.css";
import {
  getWorkflowDiagram,
  getWorkflowInstance,
  revokeWorkflow,
  terminateWorkflow,
} from "@/api/workflow";
import type { ProcessDiagram, WfInstance } from "@/api/workflow/types";
import { getAppBuilderFormByKey } from "@/api/app-builder/form";
import FormRenderer from "@/views/app-builder/form/renderer.vue";
import type { AppBuilderFormSchema } from "@/views/app-builder/form/types";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const viewer = shallowRef<any>();
const canvasRef = ref<HTMLDivElement>();
const loading = ref(false);
const instance = ref<WfInstance>();
const diagram = ref<ProcessDiagram>();
const detailFormSchema = ref<AppBuilderFormSchema>();
const detailFormModel = ref<Record<string, unknown>>();

const processInstanceId = computed(() => String(route.params.processInstanceId));

function actionLabel(action?: string) {
  return {
    COMPLETE: "同意",
    REJECT: "驳回",
    TRANSFER: "转办",
    DELEGATE: "委派",
    ADD_SIGN: "加签",
  }[action || ""] || action || "-";
}

async function renderDiagram() {
  if (!viewer.value || !diagram.value?.bpmnXml) return;
  await viewer.value.importXML(diagram.value.bpmnXml);
  const canvas = viewer.value.get("canvas");
  canvas.zoom("fit-viewport");
  diagram.value.finishedActivityIds?.forEach((id) => canvas.addMarker(id, "highlight-finished"));
  diagram.value.activeActivityIds?.forEach((id) => canvas.addMarker(id, "highlight-active"));
}

function loadData() {
  loading.value = true;
  Promise.all([getWorkflowInstance(processInstanceId.value), getWorkflowDiagram(processInstanceId.value)])
    .then(async ([instanceResult, diagramResult]) => {
      instance.value = instanceResult.data;
      diagram.value = diagramResult.data;
      detailFormSchema.value = undefined;
      detailFormModel.value = parseFormData(instance.value.formDataJson);
      if (instance.value.formKey) {
        try {
          const { data } = await getAppBuilderFormByKey(instance.value.formKey);
          detailFormSchema.value = JSON.parse(data.formSchema) as AppBuilderFormSchema;
        } catch {
          detailFormSchema.value = undefined;
        }
      }
      await renderDiagram();
    })
    .finally(() => {
      loading.value = false;
    });
}

function parseFormData(formDataJson?: string) {
  if (!formDataJson) {
    return {};
  }
  try {
    return JSON.parse(formDataJson) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function revoke() {
  ElMessageBox.prompt("请输入撤回原因", "撤回流程", {
    inputPlaceholder: "可选",
  }).then(({ value }) => {
    revokeWorkflow(processInstanceId.value, value).then(() => {
      ElMessage.success("已撤回");
      loadData();
    });
  });
}

function terminate() {
  ElMessageBox.prompt("请输入终止原因", "终止流程", {
    inputPlaceholder: "可选",
    type: "warning",
  }).then(({ value }) => {
    terminateWorkflow(processInstanceId.value, value).then(() => {
      ElMessage.success("已终止");
      loadData();
    });
  });
}

function goBusinessData() {
  if (!instance.value?.businessModelId) return;
  router.push({
    path: "/app-builder/data",
    query: {
      modelId: instance.value.businessModelId,
      keywords: instance.value.businessKey,
    },
  });
}

onMounted(() => {
  viewer.value = new BpmnViewer({
    container: canvasRef.value,
  });
  loadData();
});

onBeforeUnmount(() => {
  viewer.value?.destroy();
});
</script>

<template>
  <div v-loading="loading" class="app-container workflow-detail">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>{{ instance?.modelName || "审批详情" }}</strong>
            <el-tag class="status-tag" type="info">{{ instance?.status }}</el-tag>
          </div>
          <div>
            <el-button @click="router.back()">返回</el-button>
            <el-button v-if="instance?.businessModelId" type="primary" plain @click="goBusinessData">业务数据</el-button>
            <el-button v-if="instance?.status === 'RUNNING'" @click="revoke">撤回</el-button>
            <el-button v-if="instance?.status === 'RUNNING'" type="danger" plain @click="terminate">终止</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="流程实例ID">{{ instance?.processInstanceId }}</el-descriptions-item>
        <el-descriptions-item label="业务键">{{ instance?.businessKey || "-" }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ instance?.currentNodeName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ instance?.starterUsername }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ instance?.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ instance?.endTime || "-" }}</el-descriptions-item>
        <el-descriptions-item label="表单标识">{{ instance?.formKey || "-" }}</el-descriptions-item>
        <el-descriptions-item label="流程编码">{{ instance?.modelKey || "-" }}</el-descriptions-item>
        <el-descriptions-item label="流程定义ID">{{ instance?.processDefinitionId }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <template #header>流程图</template>
      <div ref="canvasRef" class="diagram-viewer" />
    </el-card>

    <el-row :gutter="12" class="detail-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>表单数据</template>
          <FormRenderer
            v-if="detailFormSchema"
            :schema="detailFormSchema"
            :initial-model="detailFormModel"
            readonly
          />
          <pre v-else class="json-block">{{ instance?.formDataJson || "{}" }}</pre>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>审批记录</template>
          <el-timeline>
            <el-timeline-item
              v-for="record in instance?.records || []"
              :key="`${record.taskId}-${record.createTime}`"
              :timestamp="record.createTime"
              placement="top"
            >
              <div class="record-title">
                <strong>{{ record.taskName }}</strong>
                <el-tag size="small">{{ actionLabel(record.action) }}</el-tag>
              </div>
              <p>{{ record.operatorUsername }}：{{ record.comment || "无审批意见" }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!instance?.records?.length" description="暂无审批记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.workflow-detail {
  .card-header,
  .record-title {
    display: flex;
    gap: 10px;
    align-items: center;
    justify-content: space-between;
  }

  .status-tag {
    margin-left: 10px;
  }

  .detail-card,
  .detail-row {
    margin-top: 12px;
  }

  .diagram-viewer {
    height: 420px;
    border: 1px solid var(--el-border-color-light);
    border-radius: 4px;
  }

  .json-block {
    min-height: 220px;
    padding: 12px;
    overflow: auto;
    line-height: 1.6;
    color: var(--el-text-color-primary);
    white-space: pre-wrap;
    background: var(--el-fill-color-light);
    border-radius: 4px;
  }

  :deep(.highlight-finished .djs-visual > :nth-child(1)) {
    stroke: #67c23a !important;
    stroke-width: 4px !important;
  }

  :deep(.highlight-active .djs-visual > :nth-child(1)) {
    stroke: #409eff !important;
    stroke-width: 4px !important;
  }
}
</style>
