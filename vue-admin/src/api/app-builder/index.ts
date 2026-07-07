import request from "@/utils/request";
import { AxiosPromise } from "axios";
import type {
  AppBuilderApp,
  AppBuilderAppPageResult,
  AppBuilderAppQuery,
  AppBuilderDataPageResult,
  AppBuilderDataQuery,
  AppBuilderDataRow,
  AppBuilderMenu,
  AppBuilderModel,
  AppBuilderModelField,
  AppBuilderModelFieldVersion,
  AppBuilderModelPageResult,
  AppBuilderModelQuery,
  AppBuilderPage,
  AppBuilderPagePageResult,
  AppBuilderPageQuery,
} from "./types";

const baseUrl = "/youlai-flowable/api/v1/app-builder";

export function getAppPage(params: AppBuilderAppQuery): AxiosPromise<AppBuilderAppPageResult> {
  return request({ url: `${baseUrl}/apps/page`, method: "get", params });
}

export function getApp(id: number): AxiosPromise<AppBuilderApp> {
  return request({ url: `${baseUrl}/apps/${id}`, method: "get" });
}

export function saveApp(data: AppBuilderApp): AxiosPromise<AppBuilderApp> {
  return request({ url: `${baseUrl}/apps`, method: "post", data });
}

export function updateApp(id: number, data: AppBuilderApp): AxiosPromise<AppBuilderApp> {
  return request({ url: `${baseUrl}/apps/${id}`, method: "put", data });
}

export function publishApp(id: number) {
  return request({ url: `${baseUrl}/apps/${id}/publish`, method: "post" });
}

export function disableApp(id: number) {
  return request({ url: `${baseUrl}/apps/${id}/disable`, method: "post" });
}

export function copyApp(id: number): AxiosPromise<AppBuilderApp> {
  return request({ url: `${baseUrl}/apps/${id}/copy`, method: "post" });
}

export function deleteApp(id: number) {
  return request({ url: `${baseUrl}/apps/${id}`, method: "delete" });
}

export function getModelPage(params: AppBuilderModelQuery): AxiosPromise<AppBuilderModelPageResult> {
  return request({ url: `${baseUrl}/models/page`, method: "get", params });
}

export function getModel(id: number): AxiosPromise<AppBuilderModel> {
  return request({ url: `${baseUrl}/models/${id}`, method: "get" });
}

export function saveModel(data: AppBuilderModel): AxiosPromise<AppBuilderModel> {
  return request({ url: `${baseUrl}/models`, method: "post", data });
}

export function updateModel(id: number, data: AppBuilderModel): AxiosPromise<AppBuilderModel> {
  return request({ url: `${baseUrl}/models/${id}`, method: "put", data });
}

export function publishModel(id: number) {
  return request({ url: `${baseUrl}/models/${id}/publish`, method: "post" });
}

export function deleteModel(id: number) {
  return request({ url: `${baseUrl}/models/${id}`, method: "delete" });
}

export function listModelFields(modelId: number): AxiosPromise<AppBuilderModelField[]> {
  return request({ url: `${baseUrl}/models/${modelId}/fields`, method: "get" });
}

export function saveModelFields(modelId: number, data: AppBuilderModelField[]) {
  return request({ url: `${baseUrl}/models/${modelId}/fields`, method: "put", data });
}

export function listModelFieldVersions(modelId: number): AxiosPromise<AppBuilderModelFieldVersion[]> {
  return request({ url: `${baseUrl}/models/${modelId}/field-versions`, method: "get" });
}

export function getPageConfigPage(params: AppBuilderPageQuery): AxiosPromise<AppBuilderPagePageResult> {
  return request({ url: `${baseUrl}/pages/page`, method: "get", params });
}

export function getPageConfig(id: number): AxiosPromise<AppBuilderPage> {
  return request({ url: `${baseUrl}/pages/${id}`, method: "get" });
}

export function getRuntimePageConfig(id: number): AxiosPromise<AppBuilderPage> {
  return request({ url: `${baseUrl}/pages/${id}/runtime`, method: "get" });
}

export function savePageConfig(data: AppBuilderPage): AxiosPromise<AppBuilderPage> {
  return request({ url: `${baseUrl}/pages`, method: "post", data });
}

export function updatePageConfig(id: number, data: AppBuilderPage): AxiosPromise<AppBuilderPage> {
  return request({ url: `${baseUrl}/pages/${id}`, method: "put", data });
}

export function publishPageConfig(id: number) {
  return request({ url: `${baseUrl}/pages/${id}/publish`, method: "post" });
}

export function deletePageConfig(id: number) {
  return request({ url: `${baseUrl}/pages/${id}`, method: "delete" });
}

export function listRuntimeMenus(appId?: number): AxiosPromise<AppBuilderMenu[]> {
  return request({ url: `${baseUrl}/menus`, method: "get", params: { appId } });
}

export function refreshRuntimeMenus(appId: number) {
  return request({ url: `${baseUrl}/menus/refresh/${appId}`, method: "post" });
}

export function getDataPage(modelId: number, data: AppBuilderDataQuery): AxiosPromise<AppBuilderDataPageResult> {
  return request({ url: `${baseUrl}/data/${modelId}/page`, method: "post", data });
}

export function createData(modelId: number, data: AppBuilderDataRow) {
  return request({ url: `${baseUrl}/data/${modelId}`, method: "post", data });
}

export function updateData(id: number, data: AppBuilderDataRow) {
  return request({ url: `${baseUrl}/data/${id}`, method: "put", data });
}

export function submitDataApproval(id: number) {
  return request({ url: `${baseUrl}/data/${id}/submit`, method: "post" });
}

export function deleteData(id: number) {
  return request({ url: `${baseUrl}/data/${id}`, method: "delete" });
}

export function importData(modelId: number, data: AppBuilderDataRow[]) {
  return request({ url: `${baseUrl}/data/${modelId}/import`, method: "post", data });
}

export function exportData(modelId: number, data: AppBuilderDataQuery): AxiosPromise<AppBuilderDataRow[]> {
  return request({ url: `${baseUrl}/data/${modelId}/export`, method: "post", data });
}
