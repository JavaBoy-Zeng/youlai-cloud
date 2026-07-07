<script setup lang="ts">
defineOptions({
  name: "WorkflowDesigner",
  inheritAttrs: false,
});

import BpmnModeler from "bpmn-js/lib/Modeler";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn.css";
import {
  getWorkflowModel,
  publishWorkflowModel,
  saveWorkflowModel,
  updateWorkflowModel,
} from "@/api/workflow";
import type { WfModel } from "@/api/workflow/types";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const canvasRef = ref<HTMLDivElement>();
const modeler = shallowRef<any>();
const loading = ref(false);

const formData = reactive<WfModel>({
  categoryId: undefined,
  modelKey: "",
  name: "",
  formKey: "",
  configJson: "{}",
  bpmnXml: "",
});

function defaultXml(processKey: string, processName: string) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://youlai.com/workflow">
  <process id="${processKey || "demo_process"}" name="${processName || "示例流程"}" isExecutable="true">
    <startEvent id="startEvent" name="开始"/>
    <sequenceFlow id="flow_start_approve" sourceRef="startEvent" targetRef="approveTask"/>
    <userTask id="approveTask" name="审批" flowable:assignee="\${initiator}"/>
    <sequenceFlow id="flow_approve_end" sourceRef="approveTask" targetRef="endEvent"/>
    <endEvent id="endEvent" name="结束"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${processKey || "demo_process"}">
      <bpmndi:BPMNShape id="startEvent_di" bpmnElement="startEvent">
        <omgdc:Bounds x="180" y="120" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="approveTask_di" bpmnElement="approveTask">
        <omgdc:Bounds x="280" y="98" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endEvent_di" bpmnElement="endEvent">
        <omgdc:Bounds x="450" y="120" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_start_approve_di" bpmnElement="flow_start_approve">
        <omgdi:waypoint x="216" y="138"/>
        <omgdi:waypoint x="280" y="138"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_approve_end_di" bpmnElement="flow_approve_end">
        <omgdi:waypoint x="380" y="138"/>
        <omgdi:waypoint x="450" y="138"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`;
}

async function importXml(xml?: string) {
  if (!modeler.value) return;
  await modeler.value.importXML(xml || defaultXml(formData.modelKey, formData.name));
  modeler.value.get("canvas").zoom("fit-viewport");
}

async function loadModel() {
  const id = Number(route.query.id);
  if (!id) {
    formData.bpmnXml = defaultXml(formData.modelKey, formData.name);
    await importXml(formData.bpmnXml);
    return;
  }
  loading.value = true;
  getWorkflowModel(id)
    .then(async ({ data }) => {
      Object.assign(formData, data);
      await importXml(data.bpmnXml);
    })
    .finally(() => {
      loading.value = false;
    });
}

async function syncXmlFromCanvas() {
  const result = await modeler.value.saveXML({ format: true });
  formData.bpmnXml = result.xml || "";
}

async function saveModel() {
  await syncXmlFromCanvas();
  const request = formData.id ? updateWorkflowModel(formData.id, formData) : saveWorkflowModel(formData);
  request.then(() => {
    ElMessage.success("保存成功");
  });
}

async function publishModel() {
  if (!formData.id) {
    await saveModel();
    ElMessage.warning("新流程已保存，请从流程中心重新进入后发布");
    return;
  }
  await syncXmlFromCanvas();
  updateWorkflowModel(formData.id, formData)
    .then(() => publishWorkflowModel(formData.id!))
    .then(() => {
      ElMessage.success("发布成功");
      router.push("/workflow/center");
    });
}

function resetCanvas() {
  ElMessageBox.confirm("确认重置为默认单节点审批流程？", "提示", { type: "warning" }).then(() => {
    importXml(defaultXml(formData.modelKey, formData.name));
  });
}

onMounted(() => {
  modeler.value = new BpmnModeler({
    container: canvasRef.value,
    keyboard: {
      bindTo: window,
    },
  });
  loadModel();
});

onBeforeUnmount(() => {
  modeler.value?.destroy();
});
</script>

<template>
  <div v-loading="loading" class="workflow-designer">
    <header class="designer-toolbar">
      <div class="title-group">
        <strong>{{ formData.name || "流程设计器" }}</strong>
        <el-tag size="small" type="info">{{ formData.modelKey || "未保存" }}</el-tag>
      </div>
      <div>
        <el-button @click="router.push('/workflow/center')">返回</el-button>
        <el-button @click="resetCanvas">重置画布</el-button>
        <el-button type="primary" @click="saveModel">保存草稿</el-button>
        <el-button type="success" @click="publishModel">发布流程</el-button>
      </div>
    </header>

    <main class="designer-main">
      <section ref="canvasRef" class="bpmn-canvas" />
      <aside class="property-panel">
        <el-scrollbar>
          <el-form :model="formData" label-position="top">
            <el-form-item label="流程名称">
              <el-input v-model="formData.name" />
            </el-form-item>
            <el-form-item label="流程编码">
              <el-input v-model="formData.modelKey" :disabled="Boolean(formData.id)" />
            </el-form-item>
            <el-form-item label="表单标识">
              <el-input v-model="formData.formKey" placeholder="绑定表单设计器 schema/formKey" />
            </el-form-item>
            <el-form-item label="节点配置 JSON">
              <el-input
                v-model="formData.configJson"
                type="textarea"
                :rows="12"
                placeholder='{"approveTask":{"assigneeType":"INITIATOR","buttons":["agree","reject"]}}'
              />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="formData.remark" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
        </el-scrollbar>
      </aside>
    </main>
  </div>
</template>

<style scoped lang="scss">
.workflow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  background: var(--el-bg-color-page);
}

.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.title-group {
  display: flex;
  gap: 10px;
  align-items: center;
}

.designer-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  min-height: 0;
  flex: 1;
}

.bpmn-canvas {
  min-width: 0;
  height: 100%;
  background:
    linear-gradient(90deg, rgb(230 234 240 / 55%) 1px, transparent 1px),
    linear-gradient(rgb(230 234 240 / 55%) 1px, transparent 1px);
  background-size: 16px 16px;
}

.property-panel {
  padding: 14px;
  overflow: hidden;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color-light);
}
</style>
