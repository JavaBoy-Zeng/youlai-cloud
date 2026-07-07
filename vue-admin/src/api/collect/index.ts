import request from "@/utils/request";
import { AxiosPromise } from "axios";
import type {
  CollectApi,
  CollectDashboard,
  CollectDbSource,
  CollectInstance,
  CollectModel,
  CollectQualityReport,
  CollectQuery,
  CollectRawData,
  CollectErrorData,
  CollectTask,
  CollectTaskMessage,
} from "./types";

const baseUrl = "/youlai-collect/api/v1/collect";

export function getCollectDashboard(): AxiosPromise<CollectDashboard> {
  return request({ url: `${baseUrl}/dashboard`, method: "get" });
}

export function getCollectModels(params: CollectQuery): AxiosPromise<PageResult<CollectModel[]>> {
  return request({ url: `${baseUrl}/models`, method: "get", params });
}

export function getCollectModel(id: number): AxiosPromise<CollectModel> {
  return request({ url: `${baseUrl}/models/${id}`, method: "get" });
}

export function saveCollectModel(data: CollectModel, id?: number) {
  return request({ url: `${baseUrl}/models${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function previewModelDdl(id: number): AxiosPromise<string> {
  return request({ url: `${baseUrl}/models/${id}/table/preview`, method: "post" });
}

export function updateCollectModelStatus(id: number, enabled: boolean) {
  return request({ url: `${baseUrl}/models/${id}/${enabled ? "enable" : "disable"}`, method: "post" });
}

export function getCollectApis(params: CollectQuery): AxiosPromise<PageResult<CollectApi[]>> {
  return request({ url: `${baseUrl}/apis`, method: "get", params });
}

export function saveCollectApi(data: CollectApi, id?: number) {
  return request({ url: `${baseUrl}/apis${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function testCollectApi(id: number): AxiosPromise<Record<string, any>> {
  return request({ url: `${baseUrl}/apis/${id}/test`, method: "post" });
}

export function getCollectDbSources(params: CollectQuery): AxiosPromise<PageResult<CollectDbSource[]>> {
  return request({ url: `${baseUrl}/db-sources`, method: "get", params });
}

export function saveCollectDbSource(data: CollectDbSource, id?: number) {
  return request({ url: `${baseUrl}/db-sources${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function testCollectDbSource(id: number): AxiosPromise<Record<string, any>> {
  return request({ url: `${baseUrl}/db-sources/${id}/test`, method: "post" });
}

export function getCollectTasks(params: CollectQuery): AxiosPromise<PageResult<CollectTask[]>> {
  return request({ url: `${baseUrl}/tasks`, method: "get", params });
}

export function saveCollectTask(data: CollectTask, id?: number) {
  return request({ url: `${baseUrl}/tasks${id ? `/${id}` : ""}`, method: id ? "put" : "post", data });
}

export function updateCollectTaskStatus(id: number, enabled: boolean) {
  return request({ url: `${baseUrl}/tasks/${id}/${enabled ? "enable" : "disable"}`, method: "post" });
}

export function runCollectTask(id: number): AxiosPromise<CollectInstance> {
  return request({ url: `${baseUrl}/tasks/${id}/run`, method: "post", data: { triggerType: "manual" } });
}

export function dispatchCollectTask(id: number): AxiosPromise<CollectInstance> {
  return request({ url: `${baseUrl}/tasks/${id}/dispatch`, method: "post", data: { triggerType: "manual" } });
}

export function consumeCollectMessages(limit = 10): AxiosPromise<number> {
  return request({ url: `${baseUrl}/messages/consume`, method: "post", params: { limit } });
}

export function retryCollectInstance(id: number): AxiosPromise<CollectInstance> {
  return request({ url: `${baseUrl}/instances/${id}/retry`, method: "post" });
}

export function getCollectInstances(params: PageQuery): AxiosPromise<PageResult<CollectInstance[]>> {
  return request({ url: `${baseUrl}/instances`, method: "get", params });
}

export function getCollectTaskInstances(taskId: number, params: PageQuery): AxiosPromise<PageResult<CollectInstance[]>> {
  return request({ url: `${baseUrl}/tasks/${taskId}/instances`, method: "get", params });
}

export function getCollectInstanceMessages(id: number): AxiosPromise<CollectTaskMessage[]> {
  return request({ url: `${baseUrl}/instances/${id}/messages`, method: "get" });
}

export function getCollectRawData(id: number, params: PageQuery): AxiosPromise<PageResult<CollectRawData[]>> {
  return request({ url: `${baseUrl}/instances/${id}/raw-data`, method: "get", params });
}

export function getCollectErrorData(id: number, params: PageQuery): AxiosPromise<PageResult<CollectErrorData[]>> {
  return request({ url: `${baseUrl}/instances/${id}/error-data`, method: "get", params });
}

export function getCollectQualityReport(id: number): AxiosPromise<CollectQualityReport> {
  return request({ url: `${baseUrl}/instances/${id}/quality-report`, method: "get" });
}
