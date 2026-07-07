import request from "@/utils/request";
import { AxiosPromise } from "axios";
import type {
  AppBuilderForm,
  AppBuilderFormPageResult,
  AppBuilderFormPayload,
  AppBuilderFormQuery,
} from "./types";

const baseUrl = "/youlai-flowable/api/v1/app-builder/forms";

export function getAppBuilderFormPage(params: AppBuilderFormQuery): AxiosPromise<AppBuilderFormPageResult> {
  return request({
    url: `${baseUrl}/page`,
    method: "get",
    params,
  });
}

export function getAppBuilderForm(id: number): AxiosPromise<AppBuilderForm> {
  return request({
    url: `${baseUrl}/${id}`,
    method: "get",
  });
}

export function getAppBuilderFormByKey(formKey: string): AxiosPromise<AppBuilderForm> {
  return request({
    url: `${baseUrl}/key/${encodeURIComponent(formKey)}`,
    method: "get",
  });
}

export function saveAppBuilderForm(data: AppBuilderFormPayload): AxiosPromise<AppBuilderForm> {
  return request({
    url: baseUrl,
    method: "post",
    data,
  });
}

export function updateAppBuilderForm(id: number, data: AppBuilderFormPayload): AxiosPromise<AppBuilderForm> {
  return request({
    url: `${baseUrl}/${id}`,
    method: "put",
    data,
  });
}

export function publishAppBuilderForm(id: number): AxiosPromise<AppBuilderForm> {
  return request({
    url: `${baseUrl}/${id}/publish`,
    method: "post",
  });
}

export function deleteAppBuilderForm(id: number) {
  return request({
    url: `${baseUrl}/${id}`,
    method: "delete",
  });
}
