import request from "@/utils/request";
import { AxiosPromise } from "axios";
import type {
  AdvancedAsset,
  AdvancedAssetPath,
  AdvancedAssetRequest,
  DashboardOverview,
  DecisionAuditLog,
  DecisionExecuteLog,
  DecisionHitDetailLog,
  DecisionResponse,
  DecisionRule,
  DecisionRuleRequest,
  DecisionRuleSet,
  DecisionRuleSetRequest,
  DecisionScene,
  DecisionSceneRequest,
  DecisionVersion,
  DecisionVariable,
  DecisionVariableRequest,
  ExecuteDecisionRequest,
  FlowTestRequest,
  GrayPolicy,
  GrayPolicyRequest,
  PublishRequestForm,
  PublishRequestRecord,
  RuleTestRequest,
  SimulationJob,
  SimulationJobRequest,
  DecisionFlow,
  DecisionFlowRequest,
} from "./types";

const baseUrl = "/decision-engine/api/v1/decision-engine";

export function getDecisionOverview(): AxiosPromise<DashboardOverview> {
  return request({ url: `${baseUrl}/dashboard/overview`, method: "get" });
}

export function getScenes(): AxiosPromise<DecisionScene[]> {
  return request({ url: `${baseUrl}/scenes`, method: "get" });
}

export function saveScene(data: DecisionSceneRequest, id?: number): AxiosPromise<DecisionScene> {
  return request({ url: `${baseUrl}/scenes${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function getVariables(sceneCode?: string): AxiosPromise<DecisionVariable[]> {
  return request({ url: `${baseUrl}/variables`, method: "get", params: { sceneCode } });
}

export function saveVariable(data: DecisionVariableRequest, id?: number): AxiosPromise<DecisionVariable> {
  return request({ url: `${baseUrl}/variables${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function getRules(sceneCode?: string): AxiosPromise<DecisionRule[]> {
  return request({ url: `${baseUrl}/rules`, method: "get", params: { sceneCode } });
}

export function saveRule(data: DecisionRuleRequest, id?: number): AxiosPromise<DecisionRule> {
  return request({ url: `${baseUrl}/rules${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function getRuleSets(sceneCode?: string): AxiosPromise<DecisionRuleSet[]> {
  return request({ url: `${baseUrl}/rule-sets`, method: "get", params: { sceneCode } });
}

export function saveRuleSet(data: DecisionRuleSetRequest, id?: number): AxiosPromise<DecisionRuleSet> {
  return request({ url: `${baseUrl}/rule-sets${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function getFlows(sceneCode?: string): AxiosPromise<DecisionFlow[]> {
  return request({ url: `${baseUrl}/flows`, method: "get", params: { sceneCode } });
}

export function saveFlow(data: DecisionFlowRequest, id?: number): AxiosPromise<DecisionFlow> {
  return request({ url: `${baseUrl}/flows${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function getAdvancedAssets(path: AdvancedAssetPath): AxiosPromise<AdvancedAsset[]> {
  return request({ url: `${baseUrl}/${path}`, method: "get" });
}

export function saveAdvancedAsset(
  path: AdvancedAssetPath,
  data: AdvancedAssetRequest,
  id?: number
): AxiosPromise<AdvancedAsset> {
  return request({ url: `${baseUrl}/${path}${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function executeDecision(data: ExecuteDecisionRequest): AxiosPromise<DecisionResponse> {
  return request({ url: `${baseUrl}/decision/execute`, method: "post", data });
}

export function testDecisionRule(data: RuleTestRequest): AxiosPromise<DecisionResponse> {
  return request({ url: `${baseUrl}/rules/${data.ruleCode}/test`, method: "post", data });
}

export function testDecisionFlow(data: FlowTestRequest): AxiosPromise<DecisionResponse> {
  return request({ url: `${baseUrl}/flows/${data.flowCode}/test`, method: "post", data });
}

export function getDecisionVersions(targetType: string, targetId: number): AxiosPromise<DecisionVersion[]> {
  return request({ url: `${baseUrl}/versions/${targetType}/${targetId}`, method: "get" });
}

export function createPublishRequest(data: PublishRequestForm): AxiosPromise<PublishRequestRecord> {
  return request({ url: `${baseUrl}/publish-requests`, method: "post", data });
}

export function submitPublishRequest(id: number): AxiosPromise<PublishRequestRecord> {
  return request({ url: `${baseUrl}/publish-requests/${id}/submit`, method: "post" });
}

export function refreshPublishRequest(id: number): AxiosPromise<PublishRequestRecord> {
  return request({ url: `${baseUrl}/publish-requests/${id}/refresh`, method: "post" });
}

export function getGrayPolicies(): AxiosPromise<GrayPolicy[]> {
  return request({ url: `${baseUrl}/gray-policies`, method: "get" });
}

export function saveGrayPolicy(data: GrayPolicyRequest, id?: number): AxiosPromise<GrayPolicy> {
  return request({ url: `${baseUrl}/gray-policies${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function getSimulationJobs(): AxiosPromise<SimulationJob[]> {
  return request({ url: `${baseUrl}/simulation/jobs`, method: "get" });
}

export function saveSimulationJob(data: SimulationJobRequest): AxiosPromise<SimulationJob> {
  return request({ url: `${baseUrl}/simulation/jobs`, method: "post", data });
}

export function getDecisionExecutionLogs(): AxiosPromise<DecisionExecuteLog[]> {
  return request({ url: `${baseUrl}/logs/executions`, method: "get" });
}

export function getDecisionHitDetailLogs(): AxiosPromise<DecisionHitDetailLog[]> {
  return request({ url: `${baseUrl}/logs/hit-details`, method: "get" });
}

export function getDecisionAuditLogs(): AxiosPromise<DecisionAuditLog[]> {
  return request({ url: `${baseUrl}/logs/audits`, method: "get" });
}
