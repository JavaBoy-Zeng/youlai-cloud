<script setup lang="ts">
import {
  Cpu,
  DocumentChecked,
  Plus,
  Refresh,
  Search,
  VideoPlay,
} from "@element-plus/icons-vue";
import {
  createPublishRequest,
  executeDecision,
  getAdvancedAssets,
  getDecisionExecutionLogs,
  getDecisionHitDetailLogs,
  getDecisionOverview,
  getDecisionVersions,
  getFlows,
  getRuleSets,
  getRules,
  getScenes,
  getVariables,
  saveAdvancedAsset,
  saveFlow,
  saveRule,
  saveRuleSet,
  saveScene,
  saveVariable,
  submitPublishRequest,
  testDecisionFlow,
  testDecisionRule,
} from "@/api/decision-engine";
import type {
  AdvancedAsset,
  AdvancedAssetPath,
  DashboardOverview,
  DecisionExecuteLog,
  DecisionFlow,
  DecisionHitDetailLog,
  DecisionResponse,
  DecisionRule,
  DecisionRuleRequest,
  DecisionRuleSet,
  DecisionScene,
  DecisionVariable,
  ExpressionType,
  MatchMode,
  PublishRequestRecord,
  TargetType,
} from "@/api/decision-engine/types";

defineOptions({
  name: "DecisionEngineConsole",
  inheritAttrs: false,
});

type AssetPath =
  | "scenes"
  | "variables"
  | "rules"
  | "rule-sets"
  | "flows"
  | AdvancedAssetPath;

type AssetTab = {
  label: string;
  path: AssetPath;
  targetType: TargetType;
};

type GenericForm = {
  id?: number;
  code: string;
  name: string;
  sceneCode: string;
  category: string;
  status: string;
  type: string;
  provider: string;
  hitPolicy: string;
  owner: string;
  remark: string;
  jsonText: string;
  itemsText: string;
  mappingText: string;
  rowsText: string;
  nodesText: string;
  edgesText: string;
};

type FlowNodeItem = {
  id: string;
  type: string;
  code: string;
  label: string;
  enabled: boolean;
  sort: number;
  x: number;
  y: number;
  [key: string]: any;
};

type FlowEdgeItem = {
  id: string;
  source: string;
  target: string;
  branch?: string;
  label?: string;
  [key: string]: any;
};

const assetTabs: AssetTab[] = [
  { label: "场景", path: "scenes", targetType: "SCENE" },
  { label: "变量", path: "variables", targetType: "VARIABLE" },
  { label: "规则", path: "rules", targetType: "RULE" },
  { label: "规则集", path: "rule-sets", targetType: "RULE_SET" },
  { label: "决策流", path: "flows", targetType: "FLOW" },
  { label: "数据源", path: "data-sources", targetType: "DATA_SOURCE" },
  { label: "模型", path: "models", targetType: "MODEL" },
  { label: "评分卡", path: "score-cards", targetType: "SCORE_CARD" },
  { label: "决策表", path: "decision-tables", targetType: "DECISION_TABLE" },
];

const statusOptions = ["草稿", "已启用", "已发布", "已停用"];
const expressionTypeOptions: ExpressionType[] = ["STRUCTURED", "AVIATOR", "MIXED"];
const matchModeOptions: MatchMode[] = ["BOOLEAN", "DEGREE"];
const variableSourceOptions = ["REQUEST", "CONSTANT", "LOCAL", "HARE", "MODEL", "DATA_SOURCE"];
const variableTypeOptions = ["STRING", "NUMBER", "BOOLEAN", "DATE", "OBJECT", "ARRAY"];
const flowNodePalette = [
  { type: "START", label: "开始节点", group: "输入输出" },
  { type: "END", label: "结束节点", group: "输入输出" },
  { type: "RULE", label: "条件分支", group: "数据处理" },
  { type: "RULE_SET", label: "赋值节点", group: "数据处理" },
  { type: "SCORE_CARD", label: "简单评分卡", group: "数据处理" },
  { type: "DECISION_TABLE", label: "决策表", group: "数据处理" },
  { type: "TREE", label: "决策树", group: "数据处理" },
  { type: "MODEL", label: "机器学习模型", group: "数据处理" },
];

const activeView = ref("assets");
const activeAsset = ref<AssetPath>("rules");
const activeRuleTab = ref("basic");
const activeGenericTab = ref("config");
const loading = ref(false);
const testing = ref(false);
const publishing = ref(false);
const flowCanvasRef = ref<HTMLElement>();
const overview = ref<DashboardOverview>({});
const scenes = ref<DecisionScene[]>([]);
const variables = ref<DecisionVariable[]>([]);
const rules = ref<DecisionRule[]>([]);
const ruleSets = ref<DecisionRuleSet[]>([]);
const flows = ref<DecisionFlow[]>([]);
const advancedRows = ref<Record<AdvancedAssetPath, AdvancedAsset[]>>({
  "data-sources": [],
  models: [],
  "score-cards": [],
  "decision-tables": [],
});
const executionLogs = ref<DecisionExecuteLog[]>([]);
const hitLogs = ref<DecisionHitDetailLog[]>([]);
const versions = ref<any[]>([]);
const assetFilter = reactive({ keywords: "", sceneCode: "", status: "" });
const selectedRow = ref<Record<string, any>>();
const genericForm = reactive<GenericForm>(blankGenericForm());
const ruleForm = reactive({
  id: undefined as number | undefined,
  sceneCode: "trade_risk",
  code: "",
  name: "",
  priority: 0,
  expressionType: "STRUCTURED" as ExpressionType,
  matchMode: "BOOLEAN" as MatchMode,
  requiredMatch: 0,
  conditionExpression: "",
  conditionsText: pretty(defaultConditions()),
  actionsText: pretty(defaultActions()),
  fallbackActionText: "{}",
  status: "草稿",
  owner: "",
  remark: "",
});
const testMode = ref<"decision" | "rule" | "flow">("decision");
const testForm = reactive({
  sceneCode: "trade_risk",
  ruleCode: "RISK_001",
  flowCode: "trade_risk_flow",
  eventId: "EVT_TEST_001",
  userId: "10001",
  paramsText: pretty({ orderAmount: 12000, city: "重庆" }),
});
const testResult = ref<DecisionResponse | Record<string, any>>({});
const publishForm = reactive({
  targetType: "RULE" as TargetType,
  targetId: undefined as number | undefined,
  code: "",
  applicant: "console",
  workflowModelId: undefined as number | undefined,
  remark: "提交发布审批",
});
const flowDraft = reactive({
  nodeType: "RULE",
  nodeCode: "",
  edgeSource: "",
  edgeTarget: "",
  edgeBranch: "",
});
const flowDrag = reactive({
  nodeId: "",
  offsetX: 0,
  offsetY: 0,
});
const flowConnect = reactive({
  sourceId: "",
  startX: 0,
  startY: 0,
  endX: 0,
  endY: 0,
});
const selectedFlowEdgeId = ref("");

const currentTab = computed(() => assetTabs.find(item => item.path === activeAsset.value) || assetTabs[2]);
const isAdvanced = computed(() => ["data-sources", "models", "score-cards", "decision-tables"].includes(activeAsset.value));
const currentRows = computed<Record<string, any>[]>(() => {
  const rows = rowsFor(activeAsset.value);
  const keyword = assetFilter.keywords.trim().toLowerCase();
  return rows.filter(row => {
    const matchKeyword =
      !keyword ||
      String(row.code || "").toLowerCase().includes(keyword) ||
      String(row.name || "").toLowerCase().includes(keyword);
    const matchScene = !assetFilter.sceneCode || row.sceneCode === assetFilter.sceneCode;
    const matchStatus = !assetFilter.status || row.status === assetFilter.status;
    return matchKeyword && matchScene && matchStatus;
  });
});
const generatedExpression = computed(() => {
  if (ruleForm.expressionType !== "STRUCTURED" && ruleForm.conditionExpression.trim()) {
    return ruleForm.conditionExpression.trim();
  }
  return buildExpression(parseJson<Record<string, any>>(ruleForm.conditionsText, {}));
});
const resultDistribution = computed(() =>
  Object.entries(overview.value.resultDistribution || {}).map(([name, value]) => ({ name, value }))
);
const flowNodes = computed<FlowNodeItem[]>(() =>
  normalizeFlowNodes(parseJson<Array<Record<string, any>>>(genericForm.nodesText, []))
);
const flowEdges = computed<FlowEdgeItem[]>(() =>
  normalizeFlowEdges(parseJson<Array<Record<string, any>>>(genericForm.edgesText, []))
);
const flowNodeMap = computed(() => new Map(flowNodes.value.map(node => [node.id, node])));
const flowCanvasSize = computed(() => {
  const maxX = Math.max(640, ...flowNodes.value.map(node => node.x + 220));
  const maxY = Math.max(360, ...flowNodes.value.map(node => node.y + 120));
  return { width: maxX + 64, height: maxY + 64 };
});
const flowEdgeLines = computed(() =>
  flowEdges.value
    .map(edge => {
      const source = flowNodeMap.value.get(edge.source);
      const target = flowNodeMap.value.get(edge.target);
      if (!source || !target) return null;
      const x1 = source.x + 180;
      const y1 = source.y + 32;
      const x2 = target.x;
      const y2 = target.y + 32;
      const mid = Math.max(48, Math.abs(x2 - x1) / 2);
      return {
        ...edge,
        d: `M ${x1} ${y1} C ${x1 + mid} ${y1}, ${x2 - mid} ${y2}, ${x2} ${y2}`,
        labelX: (x1 + x2) / 2,
        labelY: (y1 + y2) / 2 - 8,
      };
    })
    .filter((edge): edge is NonNullable<typeof edge> => Boolean(edge))
);
const flowConnectPath = computed(() => {
  if (!flowConnect.sourceId) return "";
  const mid = Math.max(48, Math.abs(flowConnect.endX - flowConnect.startX) / 2);
  return `M ${flowConnect.startX} ${flowConnect.startY} C ${flowConnect.startX + mid} ${flowConnect.startY}, ${flowConnect.endX - mid} ${flowConnect.endY}, ${flowConnect.endX} ${flowConnect.endY}`;
});

onMounted(() => {
  loadAll();
});

watch(activeAsset, () => {
  selectedRow.value = undefined;
  versions.value = [];
  if (activeAsset.value === "rules") {
    resetRuleForm();
  } else {
    resetGenericForm();
    activeGenericTab.value = activeAsset.value === "flows" ? "canvas" : "config";
  }
});

function blankGenericForm(): GenericForm {
  return {
    code: "",
    name: "",
    sceneCode: "trade_risk",
    category: "",
    status: "草稿",
    type: "",
    provider: "",
    hitPolicy: "FIRST",
    owner: "",
    remark: "",
    jsonText: "{}",
    itemsText: "[]",
    mappingText: "[]",
    rowsText: "[]",
    nodesText: pretty([
      { id: "start", type: "START", code: "start", label: "开始", enabled: true, sort: 1 },
      { id: "rule_set", type: "RULE_SET", code: "trade_risk_default_set", label: "默认规则集", enabled: true, sort: 2 },
      { id: "end", type: "END", code: "end", label: "结束", enabled: true, sort: 3 },
    ]),
    edgesText: pretty([
      { id: "edge_start_rule_set", source: "start", target: "rule_set" },
      { id: "edge_rule_set_end", source: "rule_set", target: "end" },
    ]),
  };
}

function defaultConditions() {
  return {
    logic: "AND",
    items: [{ field: "orderAmount", operator: ">", value: 10000 }],
  };
}

function defaultActions() {
  return {
    decisionResult: "REVIEW",
    riskLevel: "MEDIUM",
    score: 40,
    tags: ["人工审核"],
    reason: "命中规则",
    outputs: {},
  };
}

function pretty(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

function parseJson<T>(text: string, fallback: T): T {
  try {
    return JSON.parse(text || pretty(fallback)) as T;
  } catch {
    return fallback;
  }
}

function parseJsonStrict<T>(text: string, label: string): T | undefined {
  try {
    return JSON.parse(text || "{}") as T;
  } catch {
    ElMessage.error(`${label} 不是合法 JSON`);
    return undefined;
  }
}

function rowsFor(path: AssetPath): Record<string, any>[] {
  if (path === "scenes") return scenes.value;
  if (path === "variables") return variables.value;
  if (path === "rules") return rules.value;
  if (path === "rule-sets") return ruleSets.value;
  if (path === "flows") return flows.value;
  return advancedRows.value[path];
}

async function loadAll() {
  loading.value = true;
  try {
    const [
      overviewRes,
      sceneRes,
      variableRes,
      ruleRes,
      ruleSetRes,
      flowRes,
      dataSourceRes,
      modelRes,
      scoreCardRes,
      tableRes,
      executionRes,
      hitRes,
    ] = await Promise.all([
      getDecisionOverview(),
      getScenes(),
      getVariables(),
      getRules(),
      getRuleSets(),
      getFlows(),
      getAdvancedAssets("data-sources"),
      getAdvancedAssets("models"),
      getAdvancedAssets("score-cards"),
      getAdvancedAssets("decision-tables"),
      getDecisionExecutionLogs(),
      getDecisionHitDetailLogs(),
    ]);
    overview.value = unwrap(overviewRes, {});
    scenes.value = unwrap(sceneRes, []);
    variables.value = unwrap(variableRes, []);
    rules.value = unwrap(ruleRes, []);
    ruleSets.value = unwrap(ruleSetRes, []);
    flows.value = unwrap(flowRes, []);
    advancedRows.value["data-sources"] = unwrap(dataSourceRes, []);
    advancedRows.value.models = unwrap(modelRes, []);
    advancedRows.value["score-cards"] = unwrap(scoreCardRes, []);
    advancedRows.value["decision-tables"] = unwrap(tableRes, []);
    executionLogs.value = unwrap(executionRes, []);
    hitLogs.value = unwrap(hitRes, []);
  } finally {
    loading.value = false;
  }
}

function unwrap<T>(response: any, fallback: T): T {
  return (response?.data ?? fallback) as T;
}

function resetGenericForm() {
  Object.assign(genericForm, blankGenericForm());
  activeGenericTab.value = activeAsset.value === "flows" ? "canvas" : "config";
}

function resetRuleForm() {
  Object.assign(ruleForm, {
    id: undefined,
    sceneCode: assetFilter.sceneCode || "trade_risk",
    code: "",
    name: "",
    priority: 0,
    expressionType: "STRUCTURED",
    matchMode: "BOOLEAN",
    requiredMatch: 0,
    conditionExpression: "",
    conditionsText: pretty(defaultConditions()),
    actionsText: pretty(defaultActions()),
    fallbackActionText: "{}",
    status: "草稿",
    owner: "",
    remark: "",
  });
  activeRuleTab.value = "basic";
}

function selectRow(row: Record<string, any> | null) {
  if (!row) return;
  selectedRow.value = row;
  publishForm.targetType = currentTab.value.targetType;
  publishForm.targetId = row.id;
  publishForm.code = row.code || "";
  loadVersions(row);
  if (activeAsset.value === "rules") {
    fillRuleForm(row as DecisionRule);
  } else {
    fillGenericForm(row);
  }
}

function fillRuleForm(row: DecisionRule) {
  Object.assign(ruleForm, {
    id: row.id,
    sceneCode: row.sceneCode || "trade_risk",
    code: row.code || "",
    name: row.name || "",
    priority: row.priority || 0,
    expressionType: row.expressionType || "STRUCTURED",
    matchMode: row.matchMode || "BOOLEAN",
    requiredMatch: row.requiredMatch || 0,
    conditionExpression: row.conditionExpression || "",
    conditionsText: pretty(readJsonField(row.conditions || row.conditionsJson, defaultConditions())),
    actionsText: pretty(readJsonField(row.actions || row.actionsJson, defaultActions())),
    fallbackActionText: pretty(readJsonField(row.fallbackAction || row.fallbackActionJson, {})),
    status: row.status || "草稿",
    owner: row.owner || "",
    remark: row.remark || "",
  });
}

function fillGenericForm(row: Record<string, any>) {
  resetGenericForm();
  Object.assign(genericForm, {
    id: row.id,
    code: row.code || "",
    name: row.name || "",
    sceneCode: row.sceneCode || "trade_risk",
    category: row.category || "",
    status: row.status || "草稿",
    type: row.type || "",
    provider: row.provider || "",
    hitPolicy: row.hitPolicy || "FIRST",
    owner: row.owner || "",
    remark: row.remark || "",
    jsonText: pretty(readJsonField(row.inputSchemaJson || row.sourceConfigJson || row.configJson, {})),
    itemsText: pretty(readJsonField(row.outputSchemaJson || row.itemsJson, [])),
    mappingText: pretty(readJsonField(row.mappingJson, [])),
    rowsText: pretty(readJsonField(row.ruleCodesJson || row.rowsJson, [])),
    nodesText: pretty(readJsonField(row.nodes, parseJson(genericForm.nodesText, []))),
    edgesText: pretty(readJsonField(row.edges, parseJson(genericForm.edgesText, []))),
  });
  activeGenericTab.value = activeAsset.value === "flows" ? "canvas" : "config";
}

function selectFlowById(id?: number) {
  if (!id) {
    resetGenericForm();
    return;
  }
  const flow = flows.value.find(item => item.id === id);
  if (flow) {
    selectRow(flow as unknown as Record<string, any>);
  }
}

function readJsonField(value: unknown, fallback: unknown) {
  if (typeof value !== "string") return value ?? fallback;
  return parseJson(value, fallback);
}

async function saveCurrent() {
  if (activeAsset.value === "rules") {
    await saveRuleEditor();
    return;
  }
  const id = genericForm.id;
  const json = parseJsonStrict<Record<string, any>>(genericForm.jsonText, "JSON 配置");
  const items = parseJsonStrict<Array<Record<string, any>>>(genericForm.itemsText, "items");
  const mapping = parseJsonStrict<Array<Record<string, any>>>(genericForm.mappingText, "mapping");
  const rows = parseJsonStrict<Array<Record<string, any>>>(genericForm.rowsText, "rows/ruleCodes");
  if (!json || !items || !mapping || !rows) return;
  if (activeAsset.value === "scenes") {
    await saveScene(
      {
        code: genericForm.code,
        name: genericForm.name,
        category: genericForm.category,
        status: genericForm.status,
        inputs: Array.isArray(json) ? json : [],
        outputs: items,
        owner: genericForm.owner,
        remark: genericForm.remark,
      },
      id
    );
  } else if (activeAsset.value === "variables") {
    await saveVariable(
      {
        sceneCode: genericForm.sceneCode,
        code: genericForm.code,
        name: genericForm.name,
        type: genericForm.type || "STRING",
        source: genericForm.provider || "REQUEST",
        sourceConfig: json,
        defaultValue: items.length ? items[0] : undefined,
        status: genericForm.status,
        remark: genericForm.remark,
      },
      id
    );
  } else if (activeAsset.value === "rule-sets") {
    await saveRuleSet(
      {
        sceneCode: genericForm.sceneCode,
        code: genericForm.code,
        name: genericForm.name,
        strategy: genericForm.type || "ANY",
        requiredMatch: Number(genericForm.hitPolicy || 0),
        shortCircuit: false,
        ruleCodes: rows as unknown as string[],
        status: genericForm.status,
        remark: genericForm.remark,
      },
      id
    );
  } else if (activeAsset.value === "flows") {
    const nodes = parseJsonStrict<Array<Record<string, any>>>(genericForm.nodesText, "nodes");
    const edges = parseJsonStrict<Array<Record<string, any>>>(genericForm.edgesText, "edges");
    if (!nodes || !edges) return;
    await saveFlow(
      {
        sceneCode: genericForm.sceneCode,
        code: genericForm.code,
        name: genericForm.name,
        status: genericForm.status,
        nodes,
        edges,
        remark: genericForm.remark,
      },
      id
    );
  } else if (isAdvanced.value) {
    await saveAdvancedAsset(
      activeAsset.value as AdvancedAssetPath,
      {
        sceneCode: genericForm.sceneCode,
        code: genericForm.code,
        name: genericForm.name,
        type: genericForm.type,
        provider: genericForm.provider,
        hitPolicy: genericForm.hitPolicy,
        config: json,
        items,
        mapping,
        rows,
        status: genericForm.status,
        remark: genericForm.remark,
      },
      id
    );
  }
  ElMessage.success("已保存");
  await loadAll();
}

async function saveRuleEditor() {
  const conditions = parseJsonStrict<Record<string, any>>(ruleForm.conditionsText, "条件配置");
  const actions = parseJsonStrict<Record<string, any>>(ruleForm.actionsText, "动作配置");
  const fallbackAction = parseJsonStrict<Record<string, any>>(ruleForm.fallbackActionText, "兜底动作");
  if (!conditions || !actions || !fallbackAction) return;
  const payload: DecisionRuleRequest = {
    sceneCode: ruleForm.sceneCode,
    code: ruleForm.code,
    name: ruleForm.name,
    priority: Number(ruleForm.priority || 0),
    expressionType: ruleForm.expressionType,
    matchMode: ruleForm.matchMode,
    requiredMatch: Number(ruleForm.requiredMatch || 0),
    conditionExpression: ruleForm.conditionExpression || generatedExpression.value,
    conditions,
    actions,
    fallbackAction,
    status: ruleForm.status,
    owner: ruleForm.owner,
    remark: ruleForm.remark,
  };
  await saveRule(payload, ruleForm.id);
  ElMessage.success("规则已保存");
  await loadAll();
}

async function runTest() {
  const params = parseJsonStrict<Record<string, any>>(testForm.paramsText, "测试参数");
  if (!params) return;
  testing.value = true;
  try {
    const response =
      testMode.value === "decision"
        ? await executeDecision({
            sceneCode: testForm.sceneCode,
            eventId: testForm.eventId,
            userId: testForm.userId,
            params,
          })
        : testMode.value === "rule"
          ? await testDecisionRule({ ruleCode: testForm.ruleCode, params })
          : await testDecisionFlow({
              flowCode: testForm.flowCode,
              eventId: testForm.eventId,
              userId: testForm.userId,
              params,
            });
    testResult.value = unwrap(response, {});
    await loadAll();
  } finally {
    testing.value = false;
  }
}

async function loadVersions(row = selectedRow.value) {
  if (!row?.id) {
    versions.value = [];
    return;
  }
  const response = await getDecisionVersions(currentTab.value.targetType, row.id);
  versions.value = unwrap(response, []);
}

async function submitPublish() {
  if (!publishForm.targetId) {
    ElMessage.warning("请选择发布对象");
    return;
  }
  publishing.value = true;
  try {
    const created = await createPublishRequest({ ...publishForm });
    const request = unwrap<PublishRequestRecord>(created, { targetType: publishForm.targetType });
    if (!request.id) {
      throw new Error("发布申请创建失败");
    }
    await submitPublishRequest(request.id);
    ElMessage.success("发布申请已提交");
  } finally {
    publishing.value = false;
  }
}

function normalizeFlowNodes(nodes: Array<Record<string, any>>): FlowNodeItem[] {
  return nodes.map((node, index) => {
    const id = String(node.id || node.nodeKey || node.code || `node_${index + 1}`);
    const type = String(node.type || "RULE").toUpperCase();
    return {
      ...node,
      id,
      type,
      code: String(node.code || id),
      label: String(node.label || node.name || node.code || id),
      enabled: node.enabled !== false,
      sort: Number(node.sort || index + 1),
      x: Number(node.x ?? 80 + index * 220),
      y: Number(node.y ?? 110),
    };
  });
}

function normalizeFlowEdges(edges: Array<Record<string, any>>): FlowEdgeItem[] {
  return edges.map((edge, index) => ({
    ...edge,
    id: String(edge.id || edge.edgeKey || `edge_${index + 1}`),
    source: String(edge.source || edge.sourceKey || ""),
    target: String(edge.target || edge.targetKey || ""),
    branch: edge.branch ? String(edge.branch) : "",
    label: edge.label ? String(edge.label) : "",
  }));
}

function writeFlowNodes(nodes: FlowNodeItem[]) {
  genericForm.nodesText = pretty(nodes.map((node, index) => ({ ...node, sort: index + 1 })));
}

function writeFlowEdges(edges: FlowEdgeItem[]) {
  genericForm.edgesText = pretty(edges);
}

function flowNodeClass(type: string) {
  return `type-${type.toLowerCase().replace(/_/g, "-")}`;
}

function addFlowNode() {
  const nodes = [...flowNodes.value];
  const type = flowDraft.nodeType || "RULE";
  const baseCode = flowDraft.nodeCode.trim() || `${type.toLowerCase()}_${nodes.length + 1}`;
  const id = uniqueFlowId(baseCode, nodes);
  nodes.push({
    id,
    type,
    code: baseCode,
    label: baseCode,
    enabled: true,
    sort: nodes.length + 1,
    x: 80 + (nodes.length % 4) * 220,
    y: 80 + Math.floor(nodes.length / 4) * 130,
  });
  flowDraft.nodeCode = "";
  writeFlowNodes(nodes);
}

function addFlowNodeFromPalette(type: string, label: string) {
  const nodes = [...flowNodes.value];
  const normalizedType = type || "RULE";
  const baseCode = `${normalizedType.toLowerCase()}_${nodes.length + 1}`;
  const id = uniqueFlowId(baseCode, nodes);
  const viewport = flowCanvasRef.value;
  nodes.push({
    id,
    type: normalizedType,
    code: baseCode,
    label,
    enabled: true,
    sort: nodes.length + 1,
    x: viewport ? viewport.scrollLeft + 120 : 120,
    y: viewport ? viewport.scrollTop + 120 + (nodes.length % 5) * 28 : 120 + (nodes.length % 5) * 28,
  });
  writeFlowNodes(nodes);
}

function uniqueFlowId(seed: string, nodes: FlowNodeItem[]) {
  const taken = new Set(nodes.map(node => node.id));
  const normalized = seed.replace(/[^\w-]/g, "_") || "node";
  let candidate = normalized;
  let index = 1;
  while (taken.has(candidate)) {
    candidate = `${normalized}_${index++}`;
  }
  return candidate;
}

function removeFlowNode(nodeId: string) {
  writeFlowNodes(flowNodes.value.filter(node => node.id !== nodeId));
  writeFlowEdges(flowEdges.value.filter(edge => edge.source !== nodeId && edge.target !== nodeId));
}

function addFlowEdge() {
  if (!flowDraft.edgeSource || !flowDraft.edgeTarget) {
    ElMessage.warning("请选择连线的起点和终点");
    return;
  }
  if (flowDraft.edgeSource === flowDraft.edgeTarget) {
    ElMessage.warning("起点和终点不能相同");
    return;
  }
  appendFlowEdge(flowDraft.edgeSource, flowDraft.edgeTarget, flowDraft.edgeBranch);
  flowDraft.edgeBranch = "";
}

function appendFlowEdge(source: string, target: string, branch = "") {
  if (!source || !target || source === target) return;
  const edges = [...flowEdges.value];
  edges.push({
    id: uniqueEdgeId(source, target, edges),
    source,
    target,
    branch,
    label: branch,
  });
  writeFlowEdges(edges);
  selectedFlowEdgeId.value = edges[edges.length - 1].id;
}

function uniqueEdgeId(source: string, target: string, edges: FlowEdgeItem[]) {
  const taken = new Set(edges.map(edge => edge.id));
  const seed = `edge_${source}_${target}`.replace(/[^\w-]/g, "_");
  let candidate = seed;
  let index = 1;
  while (taken.has(candidate)) {
    candidate = `${seed}_${index++}`;
  }
  return candidate;
}

function removeFlowEdge(edgeId: string) {
  writeFlowEdges(flowEdges.value.filter(edge => edge.id !== edgeId));
  if (selectedFlowEdgeId.value === edgeId) {
    selectedFlowEdgeId.value = "";
  }
}

function selectFlowEdge(edgeId: string) {
  selectedFlowEdgeId.value = edgeId;
}

function autoLayoutFlow() {
  const order = ["START", "RULE_SET", "RULE", "MODEL", "DATA_SOURCE", "END"];
  const nodes = [...flowNodes.value].sort((a, b) => {
    const typeDiff = order.indexOf(a.type) - order.indexOf(b.type);
    return typeDiff === 0 ? a.sort - b.sort : typeDiff;
  });
  nodes.forEach((node, index) => {
    node.x = 80 + index * 220;
    node.y = 120 + (index % 2) * 52;
  });
  writeFlowNodes(nodes);
}

function startFlowDrag(event: MouseEvent, node: FlowNodeItem) {
  selectedFlowEdgeId.value = "";
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  flowDrag.nodeId = node.id;
  flowDrag.offsetX = event.clientX - rect.left;
  flowDrag.offsetY = event.clientY - rect.top;
}

function moveFlowCanvas(event: MouseEvent) {
  if (flowConnect.sourceId) {
    const point = canvasPoint(event);
    flowConnect.endX = point.x;
    flowConnect.endY = point.y;
    return;
  }
  moveFlowDrag(event);
}

function moveFlowDrag(event: MouseEvent) {
  if (!flowDrag.nodeId || !flowCanvasRef.value) return;
  const rect = flowCanvasRef.value.getBoundingClientRect();
  const x = Math.max(24, event.clientX - rect.left + flowCanvasRef.value.scrollLeft - flowDrag.offsetX);
  const y = Math.max(24, event.clientY - rect.top + flowCanvasRef.value.scrollTop - flowDrag.offsetY);
  const nodes = flowNodes.value.map(node => (node.id === flowDrag.nodeId ? { ...node, x: Math.round(x), y: Math.round(y) } : node));
  writeFlowNodes(nodes);
}

function stopFlowInteraction() {
  flowDrag.nodeId = "";
  flowConnect.sourceId = "";
}

function startFlowConnect(event: MouseEvent, node: FlowNodeItem) {
  selectedFlowEdgeId.value = "";
  flowDrag.nodeId = "";
  const start = nodeOutputPoint(node);
  const current = canvasPoint(event);
  Object.assign(flowConnect, {
    sourceId: node.id,
    startX: start.x,
    startY: start.y,
    endX: current.x,
    endY: current.y,
  });
}

function finishFlowConnect(node: FlowNodeItem) {
  if (!flowConnect.sourceId) return;
  const source = flowConnect.sourceId;
  flowConnect.sourceId = "";
  if (source === node.id) return;
  appendFlowEdge(source, node.id);
}

function addFlowNodeAt(event: MouseEvent) {
  const target = event.target as HTMLElement;
  if (target !== event.currentTarget && !target.classList.contains("flow-edges")) return;
  const point = canvasPoint(event);
  const nodes = [...flowNodes.value];
  const type = flowDraft.nodeType || "RULE";
  const baseCode = flowDraft.nodeCode.trim() || `${type.toLowerCase()}_${nodes.length + 1}`;
  const id = uniqueFlowId(baseCode, nodes);
  nodes.push({
    id,
    type,
    code: baseCode,
    label: baseCode,
    enabled: true,
    sort: nodes.length + 1,
    x: Math.max(24, Math.round(point.x - 90)),
    y: Math.max(24, Math.round(point.y - 32)),
  });
  flowDraft.nodeCode = "";
  writeFlowNodes(nodes);
}

function canvasPoint(event: MouseEvent) {
  if (!flowCanvasRef.value) return { x: 0, y: 0 };
  const rect = flowCanvasRef.value.getBoundingClientRect();
  return {
    x: event.clientX - rect.left + flowCanvasRef.value.scrollLeft,
    y: event.clientY - rect.top + flowCanvasRef.value.scrollTop,
  };
}

function nodeOutputPoint(node: FlowNodeItem) {
  return {
    x: node.x + 180,
    y: node.y + 32,
  };
}

function statusTagType(status?: string) {
  if (status === "已发布" || status === "已启用") return "success";
  if (status === "已停用") return "danger";
  return "info";
}

function resultTagType(result?: string) {
  if (result === "PASS") return "success";
  if (result === "REJECT") return "danger";
  if (result === "REVIEW") return "warning";
  return "info";
}

function buildExpression(condition: Record<string, any>): string {
  if (!condition || !Object.keys(condition).length) return "true";
  if (Array.isArray(condition.items)) {
    const joiner = condition.logic === "OR" ? " || " : " && ";
    return `(${condition.items.map(item => buildExpression(item)).join(joiner) || "true"})`;
  }
  if (condition.expression) return String(condition.expression);
  const field = String(condition.field || "nil");
  const operator = normalizeOperator(String(condition.operator || "=="));
  const value = condition.value;
  if (operator === "in") return `inList(${field}, ${quoteList(value)})`;
  if (operator === "between") return `betweenNum(${field}, ${literal(value?.[0])}, ${literal(value?.[1])})`;
  return `${field} ${operator} ${literal(value)}`;
}

function normalizeOperator(operator: string) {
  const lower = operator.toLowerCase();
  if (["=", "eq"].includes(lower)) return "==";
  if (["!=", "ne"].includes(lower)) return "!=";
  if (["gt", ">"].includes(lower)) return ">";
  if (["gte", ">="].includes(lower)) return ">=";
  if (["lt", "<"].includes(lower)) return "<";
  if (["lte", "<="].includes(lower)) return "<=";
  return lower;
}

function literal(value: any) {
  if (value === undefined || value === null) return "nil";
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return `'${String(value).replace(/'/g, "\\'")}'`;
}

function quoteList(value: any) {
  return literal(Array.isArray(value) ? value.join(";") : value);
}
</script>

<template>
  <div class="decision-console" v-loading="loading">
    <div class="console-header">
      <div>
        <h2>决策引擎</h2>
        <div class="header-meta">
          <span>资产 {{ overview.artifactCount || 0 }}</span>
          <span>调用 {{ overview.totalCalls || 0 }}</span>
          <span>均耗时 {{ overview.avgElapsedMs || 0 }}ms</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="DocumentChecked" :loading="publishing" @click="submitPublish">提交发布</el-button>
      </div>
    </div>

    <el-tabs v-model="activeView" class="main-tabs">
      <el-tab-pane label="资产配置" name="assets">
        <div class="workbench">
          <aside class="asset-nav">
            <button
              v-for="item in assetTabs"
              :key="item.path"
              class="asset-nav-item"
              :class="{ active: activeAsset === item.path }"
              type="button"
              @click="activeAsset = item.path"
            >
              {{ item.label }}
            </button>
          </aside>

          <template v-if="activeAsset === 'flows'">
            <section class="flow-designer-shell">
              <div class="flow-designer-header">
                <div class="flow-title-group">
                  <el-select
                    :model-value="genericForm.id"
                    class="flow-select"
                    clearable
                    filterable
                    placeholder="选择决策流"
                    @change="selectFlowById"
                  >
                    <el-option v-for="flow in flows" :key="flow.id || flow.code" :label="flow.name" :value="Number(flow.id)" />
                  </el-select>
                  <el-input v-model="genericForm.name" class="flow-name-input" placeholder="决策流名称" />
                  <el-input v-model="genericForm.code" class="flow-code-input" placeholder="决策流编码" />
                </div>
                <div class="flow-header-actions">
                  <el-select v-model="genericForm.sceneCode" class="flow-scene-select" filterable placeholder="场景">
                    <el-option v-for="scene in scenes" :key="scene.code" :label="scene.name" :value="scene.code" />
                  </el-select>
                  <el-select v-model="genericForm.status" class="flow-status-select">
                    <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
                  </el-select>
                  <el-button :icon="Plus" @click="resetGenericForm">新建</el-button>
                  <el-button :icon="Refresh" @click="autoLayoutFlow">整理</el-button>
                  <el-button type="primary" :icon="DocumentChecked" @click="saveCurrent">保存</el-button>
                </div>
              </div>

              <div class="flow-designer-body">
                <aside class="flow-palette">
                  <div class="flow-palette-group">输入输出</div>
                  <button
                    v-for="item in flowNodePalette.filter(node => node.group === '输入输出')"
                    :key="item.type"
                    class="flow-palette-item"
                    type="button"
                    @click="addFlowNodeFromPalette(item.type, item.label)"
                  >
                    <span class="flow-palette-icon">{{ item.label.slice(0, 1) }}</span>
                    <span>{{ item.label }}</span>
                  </button>

                  <div class="flow-palette-group">数据处理</div>
                  <button
                    v-for="item in flowNodePalette.filter(node => node.group === '数据处理')"
                    :key="item.type"
                    class="flow-palette-item"
                    type="button"
                    @click="addFlowNodeFromPalette(item.type, item.label)"
                  >
                    <span class="flow-palette-icon">{{ item.label.slice(0, 1) }}</span>
                    <span>{{ item.label }}</span>
                  </button>
                </aside>

                <section class="flow-canvas-stage">
                  <div class="flow-canvas-toolbar">
                    <span>双击空白处添加节点</span>
                    <span>右键从节点拖到节点创建连线</span>
                    <span>点击连线后点 × 删除</span>
                  </div>

                  <div ref="flowCanvasRef" class="flow-canvas-wrap flow-canvas-wrap-large">
                    <div
                      class="flow-canvas"
                      :style="{ width: `${flowCanvasSize.width}px`, height: `${flowCanvasSize.height}px` }"
                      @contextmenu.prevent
                      @dblclick="addFlowNodeAt"
                      @mousemove="moveFlowCanvas"
                      @mouseup="stopFlowInteraction"
                      @mouseleave="stopFlowInteraction"
                    >
                      <svg class="flow-edges" :width="flowCanvasSize.width" :height="flowCanvasSize.height">
                        <defs>
                          <marker id="flow-arrow-designer" markerHeight="8" markerWidth="8" orient="auto" refX="7" refY="4">
                            <path d="M0,0 L8,4 L0,8 Z" fill="#64748b" />
                          </marker>
                        </defs>
                        <g v-for="edge in flowEdgeLines" :key="edge.id" class="flow-edge-group">
                          <path
                            :d="edge.d"
                            class="flow-edge-path"
                            :class="{ selected: selectedFlowEdgeId === edge.id }"
                            marker-end="url(#flow-arrow-designer)"
                          />
                          <path :d="edge.d" class="flow-edge-hit" @click.stop="selectFlowEdge(edge.id)" />
                          <text v-if="edge.label || edge.branch" :x="edge.labelX" :y="edge.labelY" class="flow-edge-label">
                            {{ edge.label || edge.branch }}
                          </text>
                          <g
                            v-if="selectedFlowEdgeId === edge.id"
                            class="flow-edge-delete"
                            :transform="`translate(${edge.labelX}, ${edge.labelY + 20})`"
                            @click.stop="removeFlowEdge(edge.id)"
                          >
                            <circle r="11" />
                            <text text-anchor="middle" dominant-baseline="central">×</text>
                          </g>
                        </g>
                        <path v-if="flowConnectPath" :d="flowConnectPath" class="flow-edge-draft" marker-end="url(#flow-arrow-designer)" />
                      </svg>

                      <div
                        v-for="node in flowNodes"
                        :key="node.id"
                        class="flow-node flow-node-designer"
                        :class="flowNodeClass(node.type)"
                        :style="{ transform: `translate(${node.x}px, ${node.y}px)` }"
                        @mousedown.left.prevent="startFlowDrag($event, node)"
                        @mousedown.right.prevent.stop="startFlowConnect($event, node)"
                        @mouseup.right.prevent.stop="finishFlowConnect(node)"
                        @contextmenu.prevent
                      >
                        <div class="flow-node-type">{{ node.type }}</div>
                        <div class="flow-node-title">{{ node.label }}</div>
                        <div class="flow-node-code">{{ node.code }}</div>
                        <button class="flow-node-remove" type="button" @click.stop="removeFlowNode(node.id)">×</button>
                      </div>
                    </div>
                  </div>

                  <div class="flow-minimap">
                    <div
                      v-for="node in flowNodes"
                      :key="node.id"
                      class="flow-minimap-node"
                      :style="{ left: `${Math.min(150, node.x / 5)}px`, top: `${Math.min(86, node.y / 5)}px` }"
                    />
                  </div>
                </section>
              </div>
            </section>
          </template>

          <template v-else>
          <section class="asset-list">
            <div class="toolbar">
              <el-input v-model="assetFilter.keywords" :prefix-icon="Search" clearable placeholder="编码 / 名称" />
              <el-select v-model="assetFilter.sceneCode" clearable placeholder="场景">
                <el-option v-for="scene in scenes" :key="scene.code" :label="scene.name" :value="scene.code" />
              </el-select>
              <el-select v-model="assetFilter.status" clearable placeholder="状态">
                <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
              </el-select>
              <el-button :icon="Plus" @click="activeAsset === 'rules' ? resetRuleForm() : resetGenericForm()">新增</el-button>
            </div>

            <el-table :data="currentRows" height="560" highlight-current-row @current-change="selectRow">
              <el-table-column prop="code" label="编码" min-width="150" />
              <el-table-column prop="name" label="名称" min-width="160" />
              <el-table-column prop="sceneCode" label="场景" width="120" />
              <el-table-column prop="status" label="状态" width="96">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" effect="plain">{{ row.status || "草稿" }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="versionNo" label="版本" width="80" />
              <el-table-column prop="updateTime" label="更新时间" width="180" />
            </el-table>
          </section>

          <section class="editor">
            <template v-if="activeAsset === 'rules'">
              <el-tabs v-model="activeRuleTab">
                <el-tab-pane label="基础信息" name="basic">
                  <el-form label-width="92px" label-position="left">
                    <el-form-item label="场景">
                      <el-select v-model="ruleForm.sceneCode" filterable>
                        <el-option v-for="scene in scenes" :key="scene.code" :label="scene.name" :value="scene.code" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="编码"><el-input v-model="ruleForm.code" /></el-form-item>
                    <el-form-item label="名称"><el-input v-model="ruleForm.name" /></el-form-item>
                    <div class="two-cols">
                      <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="0" /></el-form-item>
                      <el-form-item label="状态">
                        <el-select v-model="ruleForm.status">
                          <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
                        </el-select>
                      </el-form-item>
                    </div>
                    <div class="two-cols">
                      <el-form-item label="表达式">
                        <el-select v-model="ruleForm.expressionType">
                          <el-option v-for="item in expressionTypeOptions" :key="item" :label="item" :value="item" />
                        </el-select>
                      </el-form-item>
                      <el-form-item label="匹配模式">
                        <el-select v-model="ruleForm.matchMode">
                          <el-option v-for="item in matchModeOptions" :key="item" :label="item" :value="item" />
                        </el-select>
                      </el-form-item>
                    </div>
                    <el-form-item label="负责人"><el-input v-model="ruleForm.owner" /></el-form-item>
                    <el-form-item label="备注"><el-input v-model="ruleForm.remark" type="textarea" :rows="2" /></el-form-item>
                  </el-form>
                </el-tab-pane>

                <el-tab-pane label="条件配置" name="conditions">
                  <el-input v-model="ruleForm.conditionsText" class="code-editor" type="textarea" :rows="18" spellcheck="false" />
                </el-tab-pane>

                <el-tab-pane label="动作配置" name="actions">
                  <el-input v-model="ruleForm.actionsText" class="code-editor" type="textarea" :rows="10" spellcheck="false" />
                  <el-divider />
                  <el-input v-model="ruleForm.fallbackActionText" class="code-editor" type="textarea" :rows="6" spellcheck="false" />
                </el-tab-pane>

                <el-tab-pane label="高级" name="advanced">
                  <el-input v-model="ruleForm.conditionExpression" class="code-editor" type="textarea" :rows="8" spellcheck="false" />
                  <div class="expression-preview">{{ generatedExpression }}</div>
                  <el-button :icon="VideoPlay" :loading="testing" @click="runTest">表达式测试</el-button>
                </el-tab-pane>
              </el-tabs>
            </template>

            <template v-else>
              <el-form label-width="92px" label-position="left">
                <el-form-item label="编码"><el-input v-model="genericForm.code" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="genericForm.name" /></el-form-item>
                <el-form-item v-if="activeAsset !== 'scenes' && activeAsset !== 'data-sources' && activeAsset !== 'models'" label="场景">
                  <el-select v-model="genericForm.sceneCode" filterable>
                    <el-option v-for="scene in scenes" :key="scene.code" :label="scene.name" :value="scene.code" />
                  </el-select>
                </el-form-item>
                <div class="two-cols">
                  <el-form-item :label="activeAsset === 'rule-sets' ? '策略' : '类型'">
                    <el-select v-if="activeAsset === 'variables'" v-model="genericForm.type">
                      <el-option v-for="item in variableTypeOptions" :key="item" :label="item" :value="item" />
                    </el-select>
                    <el-input v-else v-model="genericForm.type" />
                  </el-form-item>
                  <el-form-item :label="activeAsset === 'variables' ? '来源' : '状态'">
                    <el-select v-if="activeAsset === 'variables'" v-model="genericForm.provider">
                      <el-option v-for="item in variableSourceOptions" :key="item" :label="item" :value="item" />
                    </el-select>
                    <el-select v-else v-model="genericForm.status">
                      <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
                    </el-select>
                  </el-form-item>
                </div>
                <el-form-item v-if="activeAsset === 'scenes'" label="分类"><el-input v-model="genericForm.category" /></el-form-item>
                <el-form-item label="备注"><el-input v-model="genericForm.remark" type="textarea" :rows="2" /></el-form-item>
                <el-tabs v-model="activeGenericTab">
                  <el-tab-pane :label="activeAsset === 'scenes' ? '输入' : '配置'" name="config">
                    <el-input v-model="genericForm.jsonText" class="code-editor" type="textarea" :rows="8" spellcheck="false" />
                  </el-tab-pane>
                  <el-tab-pane v-if="activeAsset !== 'rule-sets'" label="明细" name="items">
                    <el-input v-model="genericForm.itemsText" class="code-editor" type="textarea" :rows="8" spellcheck="false" />
                  </el-tab-pane>
                  <el-tab-pane v-if="isAdvanced" label="映射" name="mapping">
                    <el-input v-model="genericForm.mappingText" class="code-editor" type="textarea" :rows="8" spellcheck="false" />
                  </el-tab-pane>
                  <el-tab-pane v-if="isAdvanced || activeAsset === 'rule-sets'" :label="activeAsset === 'rule-sets' ? '规则编码' : '行'" name="rows">
                    <el-input v-model="genericForm.rowsText" class="code-editor" type="textarea" :rows="8" spellcheck="false" />
                  </el-tab-pane>
                </el-tabs>
              </el-form>
            </template>

            <div class="editor-actions">
              <el-button type="primary" :icon="DocumentChecked" @click="saveCurrent">保存</el-button>
              <el-button :icon="Refresh" @click="activeAsset === 'rules' ? resetRuleForm() : resetGenericForm()">重置</el-button>
            </div>

            <div class="side-panel">
              <div class="panel-title">发布</div>
              <div class="two-cols">
                <el-input v-model="publishForm.code" placeholder="编码" />
                <el-input v-model="publishForm.applicant" placeholder="申请人" />
              </div>
              <el-input v-model="publishForm.remark" type="textarea" :rows="2" />
            </div>

            <div class="side-panel">
              <div class="panel-title">版本</div>
              <el-table :data="versions" height="150">
                <el-table-column prop="versionNo" label="版本" width="80" />
                <el-table-column prop="remark" label="备注" />
              </el-table>
            </div>
          </section>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行测试" name="test">
        <div class="test-grid">
          <section class="test-form">
            <el-radio-group v-model="testMode">
              <el-radio-button label="decision">实时决策</el-radio-button>
              <el-radio-button label="rule">规则</el-radio-button>
              <el-radio-button label="flow">决策流</el-radio-button>
            </el-radio-group>
            <el-form label-width="88px" label-position="left">
              <el-form-item v-if="testMode === 'decision'" label="场景"><el-input v-model="testForm.sceneCode" /></el-form-item>
              <el-form-item v-if="testMode === 'rule'" label="规则"><el-input v-model="testForm.ruleCode" /></el-form-item>
              <el-form-item v-if="testMode === 'flow'" label="决策流"><el-input v-model="testForm.flowCode" /></el-form-item>
              <el-form-item label="事件"><el-input v-model="testForm.eventId" /></el-form-item>
              <el-form-item label="用户"><el-input v-model="testForm.userId" /></el-form-item>
              <el-form-item label="参数">
                <el-input v-model="testForm.paramsText" class="code-editor" type="textarea" :rows="14" spellcheck="false" />
              </el-form-item>
              <el-button type="primary" :icon="Cpu" :loading="testing" @click="runTest">运行</el-button>
            </el-form>
          </section>
          <section class="test-result">
            <div class="result-header">
              <el-tag :type="resultTagType(testResult.decisionResult as string)" effect="dark">
                {{ testResult.decisionResult || "WAITING" }}
              </el-tag>
              <span>{{ testResult.traceId }}</span>
            </div>
            <pre>{{ pretty(testResult) }}</pre>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="日志" name="logs">
        <div class="log-grid">
          <section>
            <h3>执行日志</h3>
            <el-table :data="executionLogs" height="520">
              <el-table-column prop="traceId" label="Trace" min-width="180" />
              <el-table-column prop="sceneCode" label="场景" width="120" />
              <el-table-column prop="decisionResult" label="结果" width="100" />
              <el-table-column prop="riskLevel" label="等级" width="100" />
              <el-table-column prop="elapsedMs" label="耗时" width="90" />
              <el-table-column prop="createTime" label="时间" width="180" />
            </el-table>
          </section>
          <section>
            <h3>命中明细</h3>
            <el-table :data="hitLogs" height="520">
              <el-table-column prop="traceId" label="Trace" min-width="180" />
              <el-table-column prop="targetType" label="对象" width="90" />
              <el-table-column prop="targetCode" label="编码" min-width="130" />
              <el-table-column prop="detailType" label="类型" width="90" />
              <el-table-column prop="matched" label="命中" width="80" />
            </el-table>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="概览" name="overview">
        <div class="metric-grid">
          <div class="metric"><strong>{{ overview.artifactCount || 0 }}</strong><span>资产</span></div>
          <div class="metric"><strong>{{ overview.totalCalls || 0 }}</strong><span>调用</span></div>
          <div class="metric"><strong>{{ overview.exceptionCalls || 0 }}</strong><span>异常</span></div>
          <div class="metric"><strong>{{ Math.round((overview.recentSuccessRate || 0) * 100) }}%</strong><span>成功率</span></div>
        </div>
        <el-table :data="resultDistribution" class="distribution-table">
          <el-table-column prop="name" label="结果" />
          <el-table-column prop="value" label="数量" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.decision-console {
  min-height: calc(100vh - 92px);
  padding: 18px;
  color: #1f2937;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.94), rgba(244, 247, 250, 0.94)),
    #f6f8fb;
}

.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 4px 18px;

  h2 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
  }
}

.header-meta,
.header-actions,
.toolbar,
.editor-actions,
.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-meta {
  margin-top: 8px;
  color: #64748b;
}

.main-tabs {
  --el-border-radius-base: 6px;
}

.workbench {
  display: grid;
  grid-template-columns: 136px minmax(380px, 1fr) minmax(440px, 0.9fr);
  gap: 14px;
  align-items: start;
}

.asset-nav,
.asset-list,
.editor,
.test-form,
.test-result,
.log-grid > section,
.metric,
.distribution-table {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
}

.asset-nav {
  padding: 8px;
}

.asset-nav-item {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  margin-bottom: 4px;
  color: #475569;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;

  &.active {
    color: #0f766e;
    background: #dff5ef;
  }
}

.asset-list,
.editor,
.test-form,
.test-result {
  padding: 14px;
}

.toolbar {
  margin-bottom: 12px;

  .el-input,
  .el-select {
    max-width: 180px;
  }
}

.two-cols {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.code-editor {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.expression-preview {
  min-height: 48px;
  padding: 10px 12px;
  margin: 12px 0;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #134e4a;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 6px;
}

.flow-designer-shell {
  grid-column: span 2;
  min-height: 720px;
  overflow: hidden;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
}

.flow-designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 10px 14px;
  border-bottom: 1px solid #e5e7eb;
}

.flow-title-group,
.flow-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.flow-select {
  width: 180px;
}

.flow-name-input {
  width: 220px;
}

.flow-code-input {
  width: 180px;
}

.flow-scene-select {
  width: 160px;
}

.flow-status-select {
  width: 110px;
}

.flow-designer-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  height: 680px;
}

.flow-palette {
  padding: 18px 14px;
  overflow: auto;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.flow-palette-group {
  margin: 8px 2px 12px;
  font-size: 14px;
  font-weight: 700;
  color: #334155;
}

.flow-palette-item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 46px;
  padding: 0 14px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  background: #f8fafc;
  border: 1px solid transparent;
  border-radius: 6px;

  &:hover {
    color: #2563eb;
    background: #eef4ff;
    border-color: #bfdbfe;
  }
}

.flow-palette-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 10px;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  background: #dbeafe;
  border-radius: 6px;
}

.flow-canvas-stage {
  position: relative;
  min-width: 0;
  background: #f3f5f8;
}

.flow-canvas-toolbar {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 3;
  display: flex;
  gap: 12px;
  align-items: center;
  min-height: 44px;
  padding: 0 16px;
  font-size: 12px;
  color: #64748b;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgb(15 23 42 / 10%);
}

.flow-toolbar {
  display: grid;
  grid-template-columns: 128px minmax(120px, 1fr) auto auto;
  gap: 8px;
  margin-bottom: 10px;
}

.flow-edge-form {
  grid-template-columns: minmax(120px, 1fr) minmax(120px, 1fr) minmax(96px, 0.7fr) auto;
}

.flow-canvas-wrap {
  height: 430px;
  overflow: auto;
  background:
    linear-gradient(#eef2f7 1px, transparent 1px),
    linear-gradient(90deg, #eef2f7 1px, transparent 1px),
    #f8fafc;
  background-size: 24px 24px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
}

.flow-canvas-wrap-large {
  height: 100%;
  border: 0;
  border-radius: 0;
}

.flow-minimap {
  position: absolute;
  right: 18px;
  bottom: 18px;
  z-index: 3;
  width: 180px;
  height: 116px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  box-shadow: 0 10px 24px rgb(15 23 42 / 10%);
}

.flow-minimap-node {
  position: absolute;
  width: 28px;
  height: 4px;
  background: #cbd5e1;
  border-radius: 999px;
}

.flow-canvas {
  position: relative;
  min-width: 100%;
  min-height: 100%;
  cursor: crosshair;
}

.flow-edges {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.flow-edge-path {
  fill: none;
  stroke: #64748b;
  stroke-width: 2;

  &.selected {
    stroke: #0f766e;
    stroke-width: 3;
  }
}

.flow-edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 14;
  cursor: pointer;
  pointer-events: stroke;
}

.flow-edge-draft {
  fill: none;
  stroke: #0f766e;
  stroke-dasharray: 8 6;
  stroke-width: 2;
  pointer-events: none;
}

.flow-edge-label {
  font-size: 12px;
  fill: #475569;
  paint-order: stroke;
  stroke: #ffffff;
  stroke-width: 4px;
}

.flow-edge-delete {
  cursor: pointer;
  pointer-events: all;

  circle {
    fill: #ffffff;
    stroke: #dc2626;
    stroke-width: 1.5;
  }

  text {
    font-size: 15px;
    font-weight: 700;
    fill: #dc2626;
  }
}

.flow-node {
  position: absolute;
  top: 0;
  left: 0;
  width: 180px;
  min-height: 64px;
  padding: 10px 34px 10px 12px;
  cursor: grab;
  user-select: none;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-left: 5px solid #0f766e;
  border-radius: 8px;
  box-shadow: 0 8px 18px rgb(15 23 42 / 10%);

  &:active {
    cursor: grabbing;
  }

  &.type-start {
    border-left-color: #16a34a;
  }

  &.type-end {
    border-left-color: #dc2626;
  }

  &.type-rule-set {
    border-left-color: #2563eb;
  }

  &.type-rule {
    border-left-color: #f59e0b;
  }

  &.type-model,
  &.type-data-source,
  &.type-score-card,
  &.type-decision-table,
  &.type-tree {
    border-left-color: #7c3aed;
  }
}

.flow-node-designer {
  width: 190px;
}

.flow-node-type {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
}

.flow-node-title {
  margin-top: 3px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-node-code {
  margin-top: 2px;
  overflow: hidden;
  font-size: 12px;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-node-remove {
  position: absolute;
  top: 6px;
  right: 8px;
  width: 22px;
  height: 22px;
  font-size: 16px;
  line-height: 18px;
  color: #94a3b8;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 50%;

  &:hover {
    color: #b91c1c;
    background: #fee2e2;
  }
}

.flow-edge-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.editor-actions {
  justify-content: flex-end;
  padding-top: 12px;
}

.side-panel {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid #e2e8f0;

  .el-textarea {
    margin-top: 10px;
  }
}

.panel-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.test-grid,
.log-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.8fr) minmax(480px, 1fr);
  gap: 14px;
}

.test-result pre {
  min-height: 460px;
  padding: 12px;
  overflow: auto;
  color: #dbeafe;
  background: #111827;
  border-radius: 8px;
}

.log-grid > section {
  padding: 14px;

  h3 {
    margin: 0 0 12px;
    font-size: 16px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 14px;
}

.metric {
  padding: 18px;

  strong {
    display: block;
    font-size: 28px;
  }

  span {
    color: #64748b;
  }
}

.distribution-table {
  margin-top: 14px;
}

@media (max-width: 1280px) {
  .workbench,
  .test-grid,
  .log-grid {
    grid-template-columns: 1fr;
  }

  .asset-nav {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(92px, 1fr));
    gap: 6px;
  }

  .asset-nav-item {
    margin-bottom: 0;
    text-align: center;
  }
}

@media (max-width: 720px) {
  .console-header,
  .toolbar,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar .el-input,
  .toolbar .el-select,
  .flow-toolbar,
  .flow-edge-form,
  .two-cols {
    max-width: none;
    grid-template-columns: 1fr;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
