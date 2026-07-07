<script setup lang="ts">
defineOptions({
  name: "WorkflowTasks",
  inheritAttrs: false,
});

import {
  completeTask,
  delegateTask,
  getDoneTaskPage,
  getMyStartedWorkflowPage,
  getTodoTaskPage,
  rejectTask,
  transferTask,
} from "@/api/workflow";
import type {
  TaskApproveForm,
  WfInstance,
  WfInstanceQuery,
  WfTask,
  WfTaskQuery,
} from "@/api/workflow/types";
import { useRouter } from "vue-router";

const router = useRouter();
const activeTab = ref("todo");
const loading = ref(false);
const taskTotal = ref(0);
const instanceTotal = ref(0);
const todoList = ref<WfTask[]>([]);
const doneList = ref<WfTask[]>([]);
const startedList = ref<WfInstance[]>([]);

const taskQuery = reactive<WfTaskQuery>({
  pageNum: 1,
  pageSize: 10,
});

const instanceQuery = reactive<WfInstanceQuery>({
  pageNum: 1,
  pageSize: 10,
});

const approveDialog = reactive({
  visible: false,
  title: "审批",
  action: "complete",
});

const approveForm = reactive<TaskApproveForm>({
  comment: "",
  targetActivityId: "",
  assignee: "",
  variables: {},
});

const currentTask = ref<WfTask>();

function loadTodo() {
  loading.value = true;
  getTodoTaskPage(taskQuery)
    .then(({ data }) => {
      todoList.value = data.list;
      taskTotal.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function loadDone() {
  loading.value = true;
  getDoneTaskPage(taskQuery)
    .then(({ data }) => {
      doneList.value = data.list;
      taskTotal.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function loadStarted() {
  loading.value = true;
  getMyStartedWorkflowPage(instanceQuery)
    .then(({ data }) => {
      startedList.value = data.list;
      instanceTotal.value = data.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleTabChange() {
  if (activeTab.value === "todo") loadTodo();
  if (activeTab.value === "done") loadDone();
  if (activeTab.value === "started") loadStarted();
}

function openApproveDialog(row: any, action: string) {
  currentTask.value = row;
  approveDialog.action = action;
  approveDialog.title = {
    complete: "同意审批",
    reject: "驳回审批",
    transfer: "转办任务",
    delegate: "委派任务",
  }[action] || "审批";
  Object.assign(approveForm, {
    comment: "",
    targetActivityId: "",
    assignee: "",
    variables: {},
  });
  approveDialog.visible = true;
}

function submitApprove() {
  if (!currentTask.value) return;
  const taskId = currentTask.value.taskId;
  const actionMap: Record<string, any> = {
    complete: completeTask,
    reject: rejectTask,
    transfer: transferTask,
    delegate: delegateTask,
  };
  actionMap[approveDialog.action](taskId, approveForm).then(() => {
    ElMessage.success("处理成功");
    approveDialog.visible = false;
    loadTodo();
  });
}

function goDetail(processInstanceId: string) {
  router.push(`/workflow/detail/${processInstanceId}`);
}

onMounted(() => {
  loadTodo();
});
</script>

<template>
  <div class="app-container workflow-tasks">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="我的待办" name="todo">
          <el-table v-loading="loading" :data="todoList" border>
            <el-table-column prop="processName" label="流程名称" min-width="150" />
            <el-table-column prop="taskName" label="当前任务" width="130" />
            <el-table-column prop="businessKey" label="业务键" min-width="160" />
            <el-table-column prop="starterUsername" label="发起人" width="110" />
            <el-table-column prop="createTime" label="到达时间" width="170" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="goDetail(row.processInstanceId)">详情</el-button>
                <el-button link type="success" @click="openApproveDialog(row, 'complete')">同意</el-button>
                <el-button link type="warning" @click="openApproveDialog(row, 'reject')">驳回</el-button>
                <el-dropdown>
                  <el-button link type="primary">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="openApproveDialog(row, 'transfer')">转办</el-dropdown-item>
                      <el-dropdown-item @click="openApproveDialog(row, 'delegate')">委派</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-if="taskTotal > 0"
            v-model:total="taskTotal"
            v-model:page="taskQuery.pageNum"
            v-model:limit="taskQuery.pageSize"
            @pagination="loadTodo"
          />
        </el-tab-pane>

        <el-tab-pane label="我的已办" name="done">
          <el-table v-loading="loading" :data="doneList" border>
            <el-table-column prop="processName" label="流程名称" min-width="150" />
            <el-table-column prop="taskName" label="任务" width="130" />
            <el-table-column prop="businessKey" label="业务键" min-width="160" />
            <el-table-column prop="createTime" label="到达时间" width="170" />
            <el-table-column prop="endTime" label="完成时间" width="170" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="goDetail(row.processInstanceId)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-if="taskTotal > 0"
            v-model:total="taskTotal"
            v-model:page="taskQuery.pageNum"
            v-model:limit="taskQuery.pageSize"
            @pagination="loadDone"
          />
        </el-tab-pane>

        <el-tab-pane label="我的发起" name="started">
          <el-table v-loading="loading" :data="startedList" border>
            <el-table-column prop="modelName" label="流程名称" min-width="150" />
            <el-table-column prop="businessKey" label="业务键" min-width="160" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="currentNodeName" label="当前节点" min-width="140" />
            <el-table-column prop="startTime" label="发起时间" width="170" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="goDetail(row.processInstanceId)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-if="instanceTotal > 0"
            v-model:total="instanceTotal"
            v-model:page="instanceQuery.pageNum"
            v-model:limit="instanceQuery.pageSize"
            @pagination="loadStarted"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="approveDialog.visible" :title="approveDialog.title" width="560px">
      <el-form :model="approveForm" label-width="100px">
        <el-form-item v-if="approveDialog.action === 'reject'" label="驳回节点">
          <el-input v-model="approveForm.targetActivityId" placeholder="为空则按流程条件变量 approved=false 继续流转" />
        </el-form-item>
        <el-form-item v-if="['transfer', 'delegate'].includes(approveDialog.action)" label="处理人">
          <el-input v-model="approveForm.assignee" placeholder="请输入 youlai-system 用户名" />
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.comment" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.workflow-tasks {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}
</style>
