import request from "@/utils/request";
import { AxiosPromise } from "axios";

const baseUrl = "/youlai-flowable/api/v1/app-builder";

export type ExtensionRow = Record<string, any>;
export type ExtensionPageResult = PageResult<ExtensionRow[]>;

export function getExtensionPage(module: string, params: Record<string, any>): AxiosPromise<ExtensionPageResult> {
  return request({ url: `${baseUrl}/${module}/page`, method: "get", params });
}

export function saveExtension(module: string, data: ExtensionRow): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/${module}`, method: "post", data });
}

export function updateExtension(module: string, id: number, data: ExtensionRow): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/${module}/${id}`, method: "put", data });
}

export function deleteExtension(module: string, id: number) {
  return request({ url: `${baseUrl}/${module}/${id}`, method: "delete" });
}

export function publishExtension(module: string, id: number) {
  return request({ url: `${baseUrl}/${module}/${id}/publish`, method: "post" });
}

export function invokeApi(id: number, data: ExtensionRow): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/apis/${id}/invoke`, method: "post", data });
}

export function runReport(id: number): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/reports/${id}/run`, method: "get" });
}

export function createAppFromTemplate(id: number) {
  return request({ url: `${baseUrl}/templates/${id}/create-app`, method: "post" });
}

export function previewTemplate(id: number): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/templates/${id}/preview`, method: "get" });
}

export function exportTemplate(id: number): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/templates/${id}/export`, method: "get" });
}

export function importTemplate(data: ExtensionRow): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/templates/import`, method: "post", data });
}

export function rollbackVersion(id: number) {
  return request({ url: `${baseUrl}/versions/${id}/rollback`, method: "post" });
}

export function generateAiForm(prompt: string): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/ai/generate-form`, method: "post", data: { prompt } });
}

export function generateAiSql(prompt: string, code?: string): AxiosPromise<ExtensionRow> {
  return request({ url: `${baseUrl}/ai/generate-sql`, method: "post", data: { prompt, code } });
}
