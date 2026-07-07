<script setup lang="ts">
import {
  getCollectApis,
  getCollectDbSources,
  getCollectErrorData,
  getCollectInstanceMessages,
  getCollectInstances,
  getCollectModel,
  getCollectModels,
  getCollectQualityReport,
  getCollectRawData,
  getCollectTasks,
  previewModelDdl,
  consumeCollectMessages,
  dispatchCollectTask,
  retryCollectInstance,
  runCollectTask,
  saveCollectApi,
  saveCollectDbSource,
  saveCollectModel,
  saveCollectTask,
  testCollectApi,
  testCollectDbSource,
  updateCollectModelStatus,
  updateCollectTaskStatus,
} from "@/api/collect";
import type {
  CollectApi,
  CollectDbSource,
  CollectErrorData,
  CollectInstance,
  CollectModel,
  CollectModelField,
  CollectQualityReport,
  CollectQuery,
  CollectRawData,
  CollectTask,
  CollectTaskMessage,
} from "@/api/collect/types";
import { useRoute } from "vue-router";

defineOptions({
  name: "CollectConsole",
  inheritAttrs: false,
});

const route = useRoute();
const activeTab = ref(tabFromPath(route.path));
const loading = ref(false);

const query = reactive<CollectQuery>({ pageNum: 1, pageSize: 10, keywords: "", status: "" });
const totals = reactive({ models: 0, apis: 0, dbSources: 0, tasks: 0, instances: 0 });
const models = ref<CollectModel[]>([]);
const apis = ref<CollectApi[]>([]);
const dbSources = ref<CollectDbSource[]>([]);
const tasks = ref<CollectTask[]>([]);
const instances = ref<CollectInstance[]>([]);
const messages = ref<CollectTaskMessage[]>([]);
const rawRows = ref<CollectRawData[]>([]);
const errorRows = ref<CollectErrorData[]>([]);
const quality = ref<CollectQualityReport>({});
const syncingDbSourceForm = ref(false);

const dialog = reactive<DialogType>({ visible: false, title: "", type: "" });
const ddlDialog = reactive<DialogType>({ visible: false, title: "建表 SQL 预览" });
const messageDialog = reactive<DialogType>({ visible: false, title: "MQ 消息记录" });
const etlDialog = reactive<DialogType>({ visible: false, title: "ETL 执行详情" });
const ddlText = ref("");

const modelForm = reactive<CollectModel>(blankModel());
const apiForm = reactive<CollectApi>(blankApi());
const dbForm = reactive<CollectDbSource>(blankDbSource());
const taskForm = reactive<CollectTask>(blankTask());

const statusOptions = [
  { label: "启用", value: "enabled", type: "success" },
  { label: "停用", value: "disabled", type: "info" },
  { label: "草稿", value: "draft", type: "warning" },
  { label: "异常", value: "error", type: "danger" },
];
const collectTypeOptions = ["http", "db", "mq"];
const fieldTypeOptions = ["string", "number", "decimal", "date", "datetime", "boolean", "json"];
const scheduleOptions = ["manual", "cron"];
const collectModeOptions = ["full", "increment"];
const insertStrategyOptions = ["insert", "ignore", "upsert", "overwrite"];
const dbTypeOptions = [
  {
    label: "MySQL",
    value: "mysql",
    driverClass: "com.mysql.cj.jdbc.Driver",
    jdbcUrl: "jdbc:mysql://127.0.0.1:3306/database?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
    validationQuery: "SELECT 1",
  },
  {
    label: "PostgreSQL",
    value: "postgresql",
    driverClass: "org.postgresql.Driver",
    jdbcUrl: "jdbc:postgresql://127.0.0.1:5432/database",
    validationQuery: "SELECT 1",
  },
  {
    label: "ClickHouse",
    value: "clickhouse",
    driverClass: "com.clickhouse.jdbc.ClickHouseDriver",
    jdbcUrl: "jdbc:clickhouse://127.0.0.1:8123/default",
    validationQuery: "SELECT 1",
  },
  {
    label: "达梦",
    value: "dm",
    driverClass: "dm.jdbc.driver.DmDriver",
    jdbcUrl: "jdbc:dm://127.0.0.1:5236/DAMENG",
    validationQuery: "SELECT 1 FROM DUAL",
  },
];
const tabTitles: Record<string, string> = {
  models: "模型管理",
  apis: "采集接口",
  dbSources: "DB 数据源",
  tasks: "采集任务",
  instances: "执行实例",
};

type TagType = "primary" | "success" | "warning" | "info" | "danger";

const activeTitle = computed(() => tabTitles[activeTab.value] || "数据采集");

const dbTypeConfig = computed(() => getDbTypeConfig(dbForm.dbType));

const currentRows = computed<any[]>(() => {
  if (activeTab.value === "models") return models.value;
  if (activeTab.value === "apis") return apis.value;
  if (activeTab.value === "dbSources") return dbSources.value;
  if (activeTab.value === "tasks") return tasks.value;
  return instances.value;
});

const activeTotal = computed({
  get() {
    return (totals as Record<string, number>)[activeTab.value] || 0;
  },
  set(value: number) {
    (totals as Record<string, number>)[activeTab.value] = value;
  },
});

function blankModel(): CollectModel {
  return {
    modelName: "",
    modelCode: "",
    targetTableName: "",
    status: "enabled",
    remark: "",
    fields: [blankField()],
  };
}

function blankField(): CollectModelField {
  return {
    fieldName: "",
    fieldCode: "",
    fieldType: "string",
    requiredFlag: 0,
    uniqueFlag: 0,
    lengthLimit: 255,
    sort: 1,
  };
}

function blankApi(): CollectApi {
  return {
    apiName: "",
    apiCode: "",
    collectType: "http",
    sourceName: "",
    timeoutSeconds: 30,
    maxFetchCount: 1000,
    parseConfig: "{}",
    configJson: "{}",
    status: "enabled",
    remark: "",
  };
}

function blankDbSource(): CollectDbSource {
  const defaultDbType = getDbTypeConfig("mysql");
  return {
    sourceName: "",
    dbType: defaultDbType.value,
    jdbcUrl: defaultDbType.jdbcUrl,
    driverClass: defaultDbType.driverClass,
    username: "",
    passwordEncrypt: "",
    connectTimeout: 10,
    queryTimeout: 30,
    poolMinSize: 1,
    poolMaxSize: 5,
    validationQuery: defaultDbType.validationQuery,
    status: "enabled",
  };
}

function blankTask(): CollectTask {
  return {
    taskName: "",
    taskCode: "",
    modelId: undefined,
    apiId: undefined,
    scheduleType: "manual",
    cronExpr: "",
    collectMode: "full",
    insertStrategy: "insert",
    maxFetchCount: 1000,
    mappingJson: "[]",
    transformJson: "[]",
    status: "draft",
    remark: "",
  };
}

function statusTag(status?: string): TagType {
  return (statusOptions.find((item) => item.value === status)?.type || "info") as TagType;
}

function statusLabel(status?: string) {
  return statusOptions.find((item) => item.value === status)?.label || status || "-";
}

function findDbTypeConfig(dbType?: string) {
  const normalized = (dbType || "mysql").toLowerCase();
  return dbTypeOptions.find((item) => item.value === normalized);
}

function getDbTypeConfig(dbType?: string) {
  return findDbTypeConfig(dbType) || dbTypeOptions[0];
}

function dbTypeLabel(dbType?: string) {
  return findDbTypeConfig(dbType)?.label || dbType || "-";
}

function applyDbTypeDefaults(dbType?: string, overwrite = false) {
  const config = getDbTypeConfig(dbType);
  dbForm.dbType = config.value;
  if (overwrite || !dbForm.jdbcUrl) {
    dbForm.jdbcUrl = config.jdbcUrl;
  }
  if (overwrite || !dbForm.driverClass) {
    dbForm.driverClass = config.driverClass;
  }
  if (overwrite || !dbForm.validationQuery) {
    dbForm.validationQuery = config.validationQuery;
  }
}

function tabFromPath(path: string) {
  if (path.includes("/collect/apis")) return "apis";
  if (path.includes("/collect/db-sources")) return "dbSources";
  if (path.includes("/collect/tasks")) return "tasks";
  if (path.includes("/collect/instances")) return "instances";
  return "models";
}

function resetQuery() {
  query.pageNum = 1;
  query.keywords = "";
  query.status = "";
  loadActive();
}

function loadActive() {
  loading.value = true;
  const params = { ...query };
  const done = () => {
    loading.value = false;
  };
  if (activeTab.value === "models") {
    getCollectModels(params).then(({ data }) => {
      models.value = data.list;
      totals.models = data.total;
    }).finally(done);
  } else if (activeTab.value === "apis") {
    getCollectApis(params).then(({ data }) => {
      apis.value = data.list;
      totals.apis = data.total;
    }).finally(done);
  } else if (activeTab.value === "dbSources") {
    getCollectDbSources(params).then(({ data }) => {
      dbSources.value = data.list;
      totals.dbSources = data.total;
    }).finally(done);
  } else if (activeTab.value === "tasks") {
    getCollectTasks(params).then(({ data }) => {
      tasks.value = data.list;
      totals.tasks = data.total;
    }).finally(done);
  } else {
    getCollectInstances(params).then(({ data }) => {
      instances.value = data.list;
      totals.instances = data.total;
    }).finally(done);
  }
}

function openDialog(type: string, row?: any) {
  dialog.type = type;
  dialog.visible = true;
  dialog.title = `${row?.id ? "编辑" : "新增"}${dialogTitle(type)}`;
  if (type === "model") {
    Object.assign(modelForm, blankModel());
    if (row?.id) {
      getCollectModel(row.id).then(({ data }) => Object.assign(modelForm, data));
    }
  } else if (type === "api") {
    Object.assign(apiForm, blankApi(), row || {});
  } else if (type === "dbSource") {
    syncingDbSourceForm.value = true;
    Object.assign(dbForm, blankDbSource(), row || {});
    applyDbTypeDefaults(dbForm.dbType, !row?.id);
    nextTick(() => {
      syncingDbSourceForm.value = false;
    });
  } else {
    Object.assign(taskForm, blankTask(), row || {});
  }
}

function dialogTitle(type: string) {
  return ({ model: "采集模型", api: "采集接口", dbSource: "DB 数据源", task: "采集任务" } as Record<string, string>)[type];
}

function closeDialog() {
  dialog.visible = false;
}

function addField() {
  modelForm.fields = modelForm.fields || [];
  modelForm.fields.push({ ...blankField(), sort: modelForm.fields.length + 1 });
}

function removeField(index: number) {
  modelForm.fields?.splice(index, 1);
}

function submitDialog() {
  const actions: Record<string, () => Promise<any>> = {
    model: () => saveCollectModel(modelForm, modelForm.id),
    api: () => saveCollectApi(apiForm, apiForm.id),
    dbSource: () => saveCollectDbSource(dbForm, dbForm.id),
    task: () => saveCollectTask(taskForm, taskForm.id),
  };
  actions[dialog.type as string]().then(() => {
    ElMessage.success("保存成功");
    closeDialog();
    loadActive();
  });
}

function toggleModel(row: any) {
  updateCollectModelStatus(row.id!, row.status !== "enabled").then(() => loadActive());
}

function toggleTask(row: any) {
  updateCollectTaskStatus(row.id!, row.status !== "enabled").then(() => loadActive());
}

function handlePreviewDdl(row: any) {
  previewModelDdl(row.id!).then(({ data }) => {
    ddlText.value = data;
    ddlDialog.visible = true;
  });
}

function handleTestApi(row: any) {
  testCollectApi(row.id!).then(({ data }) => {
    ElMessage.success(data.message || "测试通过");
  });
}

function handleTestDb(row: any) {
  testCollectDbSource(row.id!).then(({ data }) => {
    if (data.success) {
      ElMessage.success(data.message || "测试完成");
    } else {
      ElMessage.error(data.message || "测试失败");
    }
    loadActive();
  });
}

function handleRunTask(row: any) {
  runCollectTask(row.id!).then(({ data }) => {
    ElMessage.success(`执行完成：实例 #${data.id}，状态 ${data.status}`);
    activeTab.value = "instances";
    loadActive();
  });
}

function handleDispatchTask(row: any) {
  dispatchCollectTask(row.id!).then(({ data }) => {
    ElMessage.success(`已投递内部消息：实例 #${data.id}`);
    activeTab.value = "instances";
    loadActive();
  });
}

function handleConsumeQueue() {
  consumeCollectMessages(10).then(({ data }) => {
    ElMessage.success(`已消费 ${data} 条内部消息`);
    loadActive();
  });
}

function handleRetryInstance(row: any) {
  retryCollectInstance(row.id).then(({ data }) => {
    ElMessage.success(`已重试：新实例 #${data.id}，状态 ${data.status}`);
    loadActive();
  });
}

function openMessages(row: any) {
  getCollectInstanceMessages(row.id).then(({ data }) => {
    messages.value = data;
    messageDialog.visible = true;
  });
}

function openEtlDetail(row: any) {
  etlDialog.visible = true;
  etlDialog.title = `ETL 执行详情 #${row.id}`;
  Promise.all([
    getCollectQualityReport(row.id),
    getCollectInstanceMessages(row.id),
    getCollectRawData(row.id, { pageNum: 1, pageSize: 20 }),
    getCollectErrorData(row.id, { pageNum: 1, pageSize: 20 }),
  ]).then(([qualityResp, messageResp, rawResp, errorResp]) => {
    quality.value = qualityResp.data || {};
    messages.value = messageResp.data || [];
    rawRows.value = rawResp.data.list || [];
    errorRows.value = errorResp.data.list || [];
  });
}

function totalOfActive() {
  return (totals as any)[activeTab.value] || 0;
}

watch(() => route.path, (path) => {
  activeTab.value = tabFromPath(path);
  query.pageNum = 1;
  loadActive();
});

watch(() => dbForm.dbType, (dbType, oldDbType) => {
  if (syncingDbSourceForm.value || !dialog.visible || dialog.type !== "dbSource" || !oldDbType || dbType === oldDbType) {
    return;
  }
  applyDbTypeDefaults(dbType, true);
});

onMounted(() => {
  loadActive();
});
</script>

<template>
  <div class="app-container collect-console">
    <div class="search-container">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键字">
          <el-input v-model="query.keywords" clearable placeholder="名称 / 编码" @keyup.enter="loadActive" />
        </el-form-item>
        <el-form-item label="状态" v-if="activeTab !== 'instances'">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadActive"><i-ep-search />搜索</el-button>
          <el-button @click="resetQuery"><i-ep-refresh />重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card shadow="never">
      <template #header>
        <span class="collect-page-title">{{ activeTitle }}</span>
        <el-button v-if="activeTab === 'models'" type="success" @click="openDialog('model')"><i-ep-plus />新增模型</el-button>
        <el-button v-if="activeTab === 'apis'" type="success" @click="openDialog('api')"><i-ep-plus />新增接口</el-button>
        <el-button v-if="activeTab === 'dbSources'" type="success" @click="openDialog('dbSource')"><i-ep-plus />新增数据源</el-button>
        <el-button v-if="activeTab === 'tasks'" type="success" @click="openDialog('task')"><i-ep-plus />新增任务</el-button>
        <el-button v-if="activeTab === 'instances'" type="primary" @click="handleConsumeQueue"><i-ep-video-play />消费队列</el-button>
      </template>

      <el-table v-loading="loading" :data="currentRows" border>
        <template v-if="activeTab === 'models'">
          <el-table-column prop="modelName" label="模型名称" min-width="160" />
          <el-table-column prop="modelCode" label="模型编码" min-width="160" />
          <el-table-column prop="targetTableName" label="目标表" min-width="160" />
          <el-table-column prop="fieldCount" label="字段数" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="260">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog('model', row)"><i-ep-edit />编辑</el-button>
              <el-button link type="primary" @click="handlePreviewDdl(row)"><i-ep-document />DDL</el-button>
              <el-button link type="primary" @click="toggleModel(row)">{{ row.status === "enabled" ? "停用" : "启用" }}</el-button>
            </template>
          </el-table-column>
        </template>

        <template v-else-if="activeTab === 'apis'">
          <el-table-column prop="apiName" label="接口名称" min-width="160" />
          <el-table-column prop="apiCode" label="接口编码" min-width="160" />
          <el-table-column prop="collectType" label="方式" width="100" />
          <el-table-column prop="sourceName" label="来源" min-width="140" />
          <el-table-column prop="maxFetchCount" label="最大采集量" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog('api', row)"><i-ep-edit />编辑</el-button>
              <el-button link type="primary" @click="handleTestApi(row)"><i-ep-video-play />测试</el-button>
            </template>
          </el-table-column>
        </template>

        <template v-else-if="activeTab === 'dbSources'">
          <el-table-column prop="sourceName" label="数据源" min-width="160" />
          <el-table-column label="数据库类型" width="130">
            <template #default="{ row }">{{ dbTypeLabel(row.dbType) }}</template>
          </el-table-column>
          <el-table-column prop="jdbcUrl" label="JDBC URL" min-width="260" show-overflow-tooltip />
          <el-table-column prop="lastTestStatus" label="最近测试" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog('dbSource', row)"><i-ep-edit />编辑</el-button>
              <el-button link type="primary" @click="handleTestDb(row)"><i-ep-connection />连接</el-button>
            </template>
          </el-table-column>
        </template>

        <template v-else-if="activeTab === 'tasks'">
          <el-table-column prop="taskName" label="任务名称" min-width="160" />
          <el-table-column prop="taskCode" label="任务编码" min-width="160" />
          <el-table-column prop="scheduleType" label="调度" width="100" />
          <el-table-column prop="cronExpr" label="Cron" min-width="150" />
          <el-table-column prop="insertStrategy" label="入库策略" width="110" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="320">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog('task', row)"><i-ep-edit />编辑</el-button>
              <el-button link type="primary" @click="handleDispatchTask(row)"><i-ep-message />投递</el-button>
              <el-button link type="primary" @click="handleRunTask(row)"><i-ep-video-play />执行</el-button>
              <el-button link type="primary" @click="toggleTask(row)">{{ row.status === "enabled" ? "停用" : "启用" }}</el-button>
            </template>
          </el-table-column>
        </template>

        <template v-else>
          <el-table-column prop="id" label="实例 ID" width="100" />
          <el-table-column prop="taskId" label="任务 ID" width="100" />
          <el-table-column prop="traceId" label="TraceId" min-width="220" show-overflow-tooltip />
          <el-table-column prop="triggerType" label="触发" width="100" />
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column prop="startTime" label="开始时间" min-width="180" />
          <el-table-column prop="errorMessage" label="异常" min-width="180" show-overflow-tooltip />
          <el-table-column fixed="right" label="操作" width="220">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEtlDetail(row)"><i-ep-document />详情</el-button>
              <el-button link type="primary" @click="openMessages(row)"><i-ep-message />消息</el-button>
              <el-button link type="primary" @click="handleRetryInstance(row)"><i-ep-refresh />重试</el-button>
            </template>
          </el-table-column>
        </template>
      </el-table>

      <pagination
        v-if="totalOfActive() > 0"
        v-model:total="activeTotal"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        @pagination="loadActive"
      />
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="920px" @close="closeDialog">
      <el-form label-width="110px">
        <template v-if="dialog.type === 'model'">
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="模型名称"><el-input v-model="modelForm.modelName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="模型编码"><el-input v-model="modelForm.modelCode" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="目标表"><el-input v-model="modelForm.targetTableName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="状态"><el-select v-model="modelForm.status"><el-option label="启用" value="enabled" /><el-option label="停用" value="disabled" /></el-select></el-form-item></el-col>
          </el-row>
          <el-table :data="modelForm.fields" border>
            <el-table-column label="字段名" min-width="140"><template #default="{ row }"><el-input v-model="row.fieldName" /></template></el-table-column>
            <el-table-column label="字段编码" min-width="150"><template #default="{ row }"><el-input v-model="row.fieldCode" /></template></el-table-column>
            <el-table-column label="类型" width="130"><template #default="{ row }"><el-select v-model="row.fieldType"><el-option v-for="item in fieldTypeOptions" :key="item" :label="item" :value="item" /></el-select></template></el-table-column>
            <el-table-column label="必填" width="80"><template #default="{ row }"><el-checkbox v-model="row.requiredFlag" :true-label="1" :false-label="0" /></template></el-table-column>
            <el-table-column label="唯一" width="80"><template #default="{ row }"><el-checkbox v-model="row.uniqueFlag" :true-label="1" :false-label="0" /></template></el-table-column>
            <el-table-column label="长度" width="110"><template #default="{ row }"><el-input-number v-model="row.lengthLimit" :min="1" :controls="false" /></template></el-table-column>
            <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button link type="danger" @click="removeField($index)">删除</el-button></template></el-table-column>
          </el-table>
          <el-button class="field-add" @click="addField"><i-ep-plus />新增字段</el-button>
        </template>

        <template v-else-if="dialog.type === 'api'">
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="接口名称"><el-input v-model="apiForm.apiName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="接口编码"><el-input v-model="apiForm.apiCode" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="采集方式"><el-select v-model="apiForm.collectType"><el-option v-for="item in collectTypeOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="来源名称"><el-input v-model="apiForm.sourceName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="超时秒数"><el-input-number v-model="apiForm.timeoutSeconds" :min="1" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="最大采集量"><el-input-number v-model="apiForm.maxFetchCount" :min="1" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="解析配置">
            <el-input v-model="apiForm.parseConfig" type="textarea" :rows="4" placeholder='{"rootPath":"data.list"}' />
          </el-form-item>
          <el-form-item label="来源配置">
            <el-input
              v-model="apiForm.configJson"
              type="textarea"
              :rows="6"
              placeholder='HTTP: {"url":"https://api.example.com/list","method":"GET","headers":{},"params":{}}；DB: {"dbSourceId":1,"sql":"select id,name from t","params":[]}；MQ配置消息: {"messages":[{"id":1,"name":"demo"}]}'
            />
          </el-form-item>
        </template>

        <template v-else-if="dialog.type === 'dbSource'">
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="数据源名称"><el-input v-model="dbForm.sourceName" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="数据库类型">
                <el-select v-model="dbForm.dbType" placeholder="请选择数据库类型">
                  <el-option v-for="item in dbTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="JDBC URL">
                <el-input v-model="dbForm.jdbcUrl" :placeholder="dbTypeConfig.jdbcUrl" />
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="驱动类"><el-input v-model="dbForm.driverClass" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="账号"><el-input v-model="dbForm.username" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="密码"><el-input v-model="dbForm.passwordEncrypt" type="password" show-password /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="校验 SQL"><el-input v-model="dbForm.validationQuery" /></el-form-item></el-col>
          </el-row>
        </template>

        <template v-else>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="任务名称"><el-input v-model="taskForm.taskName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="任务编码"><el-input v-model="taskForm.taskCode" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="模型 ID"><el-input-number v-model="taskForm.modelId" :min="1" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="接口 ID"><el-input-number v-model="taskForm.apiId" :min="1" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="调度类型"><el-select v-model="taskForm.scheduleType"><el-option v-for="item in scheduleOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Cron"><el-input v-model="taskForm.cronExpr" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="采集模式"><el-select v-model="taskForm.collectMode"><el-option v-for="item in collectModeOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="入库策略"><el-select v-model="taskForm.insertStrategy"><el-option v-for="item in insertStrategyOptions" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
          </el-row>
          <el-form-item label="字段映射">
            <el-input v-model="taskForm.mappingJson" type="textarea" :rows="4" placeholder='[{"source":"company.name","target":"company_name","defaultValue":"未知"}] 或 {"company_name":"company.name"}' />
          </el-form-item>
          <el-form-item label="转换规则">
            <el-input v-model="taskForm.transformJson" type="textarea" :rows="4" placeholder='[{"field":"company_name","type":"trim"},{"field":"status","type":"dict","map":{"1":"启用","0":"停用"}}]' />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" @click="submitDialog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ddlDialog.visible" :title="ddlDialog.title" width="760px">
      <el-input v-model="ddlText" type="textarea" :rows="16" readonly />
    </el-dialog>

    <el-dialog v-model="messageDialog.visible" :title="messageDialog.title" width="960px">
      <el-table :data="messages" border>
        <el-table-column prop="mqTopic" label="Topic" min-width="160" />
        <el-table-column prop="mqMessageId" label="Message ID" min-width="180" />
        <el-table-column prop="sendStatus" label="投递" width="90" />
        <el-table-column prop="consumeStatus" label="消费" width="90" />
        <el-table-column prop="messageBody" label="消息体" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-dialog v-model="etlDialog.visible" :title="etlDialog.title" width="1080px">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="总数">{{ quality.totalCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="有效">{{ quality.validCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="异常">{{ quality.invalidCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="重复">{{ quality.duplicateCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="新增">{{ quality.insertedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="更新">{{ quality.updatedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败">{{ quality.failedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="TraceId">{{ quality.traceId || "-" }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs class="etl-tabs">
        <el-tab-pane label="原始数据">
          <el-table :data="rawRows" border max-height="320">
            <el-table-column prop="dataIndex" label="#" width="70" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="rawBody" label="原始内容" min-width="420" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="异常数据">
          <el-table :data="errorRows" border max-height="320">
            <el-table-column prop="dataIndex" label="#" width="70" />
            <el-table-column prop="errorType" label="类型" width="150" />
            <el-table-column prop="errorMessage" label="原因" min-width="240" show-overflow-tooltip />
            <el-table-column prop="rawBody" label="原始内容" min-width="360" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="字段完整率">
          <el-input :model-value="quality.fieldCompletenessJson" type="textarea" :rows="8" readonly />
        </el-tab-pane>
        <el-tab-pane label="消息">
          <el-table :data="messages" border max-height="320">
            <el-table-column prop="mqTopic" label="Topic" min-width="160" />
            <el-table-column prop="mqMessageId" label="Message ID" min-width="180" />
            <el-table-column prop="sendStatus" label="投递" width="90" />
            <el-table-column prop="consumeStatus" label="消费" width="90" />
            <el-table-column prop="messageBody" label="消息体" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<style scoped>
.collect-console {
  --collect-border: #e5e7eb;
}

.field-add {
  margin-top: 12px;
}

.collect-page-title {
  color: #111827;
  font-size: 16px;
  font-weight: 650;
}

.etl-tabs {
  margin-top: 16px;
}

:deep(.el-card__header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

</style>
