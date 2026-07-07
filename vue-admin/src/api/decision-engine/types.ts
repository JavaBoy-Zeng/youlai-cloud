export type TargetType =
  | "SCENE"
  | "VARIABLE"
  | "RULE"
  | "RULE_SET"
  | "FLOW"
  | "DATA_SOURCE"
  | "MODEL"
  | "SCORE_CARD"
  | "DECISION_TABLE";

export type AdvancedAssetPath = "data-sources" | "models" | "score-cards" | "decision-tables";
export type ExpressionType = "STRUCTURED" | "AVIATOR" | "MIXED";
export type MatchMode = "BOOLEAN" | "DEGREE";

export interface DecisionSceneRequest {
  code: string;
  name: string;
  category?: string;
  status?: string;
  inputs?: Array<Record<string, any>>;
  outputs?: Array<Record<string, any>>;
  owner?: string;
  remark?: string;
}

export interface DecisionScene extends DecisionSceneRequest {
  id?: number;
  inputSchemaJson?: string;
  outputSchemaJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DecisionVariableRequest {
  sceneCode?: string;
  code: string;
  name: string;
  type?: string;
  source?: "REQUEST" | "CONSTANT" | "LOCAL" | "HARE" | "MODEL" | "DATA_SOURCE" | string;
  sourceConfig?: Record<string, any>;
  defaultValue?: any;
  status?: string;
  remark?: string;
}

export interface DecisionVariable extends DecisionVariableRequest {
  id?: number;
  sourceConfigJson?: string;
  defaultValueJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface RuleConditionGroup {
  logic?: "AND" | "OR";
  items?: Array<RuleConditionGroup | Record<string, any>>;
  [key: string]: any;
}

export interface DecisionRuleRequest {
  sceneCode: string;
  code: string;
  name: string;
  priority?: number;
  expressionType?: ExpressionType;
  matchMode?: MatchMode;
  requiredMatch?: number;
  conditionExpression?: string;
  conditions?: RuleConditionGroup;
  actions?: Record<string, any>;
  fallbackAction?: Record<string, any>;
  status?: string;
  owner?: string;
  remark?: string;
}

export interface DecisionRule extends DecisionRuleRequest {
  id?: number;
  conditionsJson?: string;
  actionsJson?: string;
  fallbackActionJson?: string;
  versionNo?: number;
  createTime?: string;
  updateTime?: string;
}

export interface DecisionRuleSetRequest {
  sceneCode: string;
  code: string;
  name: string;
  strategy?: "ANY" | "ALL" | "AT_LEAST" | string;
  requiredMatch?: number;
  shortCircuit?: boolean;
  ruleCodes?: string[];
  status?: string;
  remark?: string;
}

export interface DecisionRuleSet extends DecisionRuleSetRequest {
  id?: number;
  ruleCodesJson?: string;
  versionNo?: number;
  createTime?: string;
  updateTime?: string;
}

export interface DecisionFlowRequest {
  sceneCode: string;
  code: string;
  name: string;
  status?: string;
  nodes?: Array<Record<string, any>>;
  edges?: Array<Record<string, any>>;
  remark?: string;
}

export interface DecisionFlow extends Omit<DecisionFlowRequest, "nodes" | "edges"> {
  id?: number;
  nodes?: Array<Record<string, any>>;
  edges?: Array<Record<string, any>>;
  versionNo?: number;
  createTime?: string;
  updateTime?: string;
}

export interface AdvancedAssetRequest {
  sceneCode?: string;
  code: string;
  name: string;
  type?: string;
  provider?: string;
  hitPolicy?: string;
  config?: Record<string, any>;
  items?: Array<Record<string, any>>;
  mapping?: Array<Record<string, any>>;
  rows?: Array<Record<string, any>>;
  status?: string;
  remark?: string;
}

export interface AdvancedAsset extends AdvancedAssetRequest {
  id?: number;
  configJson?: string;
  itemsJson?: string;
  mappingJson?: string;
  rowsJson?: string;
  versionNo?: number;
  createTime?: string;
  updateTime?: string;
}

export interface PublishRequestForm {
  targetType: TargetType;
  targetId?: number;
  code?: string;
  workflowModelId?: number;
  applicant?: string;
  remark?: string;
}

export interface PublishRequestRecord extends PublishRequestForm {
  id?: number;
  versionNo?: number;
  status?: string;
  workflowBusinessKey?: string;
  processInstanceId?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DecisionVersion {
  id?: number;
  targetType?: TargetType | string;
  targetId?: number;
  code?: string;
  versionNo?: number;
  snapshotJson?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface GrayPolicyRequest {
  sceneCode?: string;
  targetType?: TargetType | string;
  targetCode?: string;
  versionNo?: number;
  percent?: number;
  condition?: Record<string, any>;
  enabled?: boolean;
  remark?: string;
}

export interface GrayPolicy extends GrayPolicyRequest {
  id?: number;
  conditionJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SimulationJobRequest {
  sceneCode?: string;
  name?: string;
  samples?: Array<Record<string, any>>;
  remark?: string;
}

export interface SimulationJob extends SimulationJobRequest {
  id?: number;
  status?: string;
  sampleJson?: string;
  resultJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ExecuteDecisionRequest {
  sceneCode: string;
  eventId?: string;
  userId?: string;
  params?: Record<string, any>;
}

export interface RuleTestRequest {
  ruleCode: string;
  params?: Record<string, any>;
}

export interface FlowTestRequest {
  flowCode: string;
  eventId?: string;
  userId?: string;
  params?: Record<string, any>;
}

export interface DecisionHitRule {
  ruleCode?: string;
  ruleName?: string;
  reason?: string;
  score?: number;
  decisionResult?: string;
  riskLevel?: string;
  tags?: string[];
}

export interface DecisionConditionTrace {
  targetType?: string;
  targetCode?: string;
  expression?: string;
  matched?: boolean;
  facts?: Record<string, any>;
  elapsedMs?: number;
  errorMessage?: string;
}

export interface DecisionResponse {
  traceId?: string;
  eventId?: string;
  sceneCode?: string;
  decisionResult?: string;
  riskLevel?: string;
  score?: number;
  tags?: string[];
  hitRules?: DecisionHitRule[];
  path?: string[];
  outputs?: Record<string, any>;
  conditionTraces?: DecisionConditionTrace[];
  elapsedMs?: number;
}

export interface DecisionExecuteLog {
  id?: number;
  traceId?: string;
  eventId?: string;
  sceneCode?: string;
  decisionResult?: string;
  riskLevel?: string;
  score?: number;
  requestJson?: string;
  responseJson?: string;
  hitRulesJson?: string;
  pathJson?: string;
  success?: boolean;
  errorMessage?: string;
  elapsedMs?: number;
  createTime?: string;
  updateTime?: string;
}

export interface DecisionHitDetailLog {
  id?: number;
  traceId?: string;
  sceneCode?: string;
  targetType?: string;
  targetCode?: string;
  detailType?: string;
  expression?: string;
  matched?: boolean;
  detailJson?: string;
  elapsedMs?: number;
  createTime?: string;
  updateTime?: string;
}

export interface DecisionAuditLog {
  id?: number;
  operator?: string;
  action?: string;
  targetKind?: TargetType | string;
  targetCode?: string;
  detailJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DashboardOverview {
  artifactCount?: number;
  totalCalls?: number;
  recentSuccessRate?: number;
  avgElapsedMs?: number;
  exceptionCalls?: number;
  resultDistribution?: Record<string, number>;
  hitRanking?: Array<{ ruleCode: string; hitCount: number }>;
  recentPublishes?: Array<Record<string, any>>;
}
