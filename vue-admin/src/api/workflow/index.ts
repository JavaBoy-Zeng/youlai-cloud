import request from "@/utils/request";
import { AxiosPromise } from "axios";
import {
  ProcessDiagram,
  StartProcessForm,
  TaskApproveForm,
  WfCategory,
  WfInstance,
  WfInstancePageResult,
  WfInstanceQuery,
  WfModel,
  WfModelPageResult,
  WfModelQuery,
  WfTaskPageResult,
  WfTaskQuery,
} from "./types";

const baseUrl = "/youlai-flowable/api/v1/workflow";

export function listWorkflowCategories(): AxiosPromise<WfCategory[]> {
  return request({
    url: `${baseUrl}/categories`,
    method: "get",
  });
}

export function saveWorkflowCategory(data: WfCategory) {
  return request({
    url: `${baseUrl}/categories`,
    method: "post",
    data,
  });
}

export function updateWorkflowCategory(id: number, data: WfCategory) {
  return request({
    url: `${baseUrl}/categories/${id}`,
    method: "put",
    data,
  });
}

export function deleteWorkflowCategory(id: number) {
  return request({
    url: `${baseUrl}/categories/${id}`,
    method: "delete",
  });
}

export function getWorkflowModelPage(params: WfModelQuery): AxiosPromise<WfModelPageResult> {
  return request({
    url: `${baseUrl}/models/page`,
    method: "get",
    params,
  });
}

export function getWorkflowModel(id: number): AxiosPromise<WfModel> {
  return request({
    url: `${baseUrl}/models/${id}`,
    method: "get",
  });
}

export function saveWorkflowModel(data: WfModel) {
  return request({
    url: `${baseUrl}/models`,
    method: "post",
    data,
  });
}

export function updateWorkflowModel(id: number, data: WfModel) {
  return request({
    url: `${baseUrl}/models/${id}`,
    method: "put",
    data,
  });
}

export function publishWorkflowModel(id: number): AxiosPromise<WfModel> {
  return request({
    url: `${baseUrl}/models/${id}/publish`,
    method: "post",
  });
}

export function suspendWorkflowModel(id: number) {
  return request({
    url: `${baseUrl}/models/${id}/suspend`,
    method: "put",
  });
}

export function activateWorkflowModel(id: number) {
  return request({
    url: `${baseUrl}/models/${id}/activate`,
    method: "put",
  });
}

export function deleteWorkflowModel(id: number) {
  return request({
    url: `${baseUrl}/models/${id}`,
    method: "delete",
  });
}

export function startWorkflow(data: StartProcessForm): AxiosPromise<WfInstance> {
  return request({
    url: `${baseUrl}/instances/start`,
    method: "post",
    data,
  });
}

export function getWorkflowInstancePage(params: WfInstanceQuery): AxiosPromise<WfInstancePageResult> {
  return request({
    url: `${baseUrl}/instances/page`,
    method: "get",
    params,
  });
}

export function getMyStartedWorkflowPage(params: WfInstanceQuery): AxiosPromise<WfInstancePageResult> {
  return request({
    url: `${baseUrl}/instances/my/page`,
    method: "get",
    params,
  });
}

export function getWorkflowInstance(processInstanceId: string): AxiosPromise<WfInstance> {
  return request({
    url: `${baseUrl}/instances/${processInstanceId}`,
    method: "get",
  });
}

export function getWorkflowDiagram(processInstanceId: string): AxiosPromise<ProcessDiagram> {
  return request({
    url: `${baseUrl}/instances/${processInstanceId}/diagram`,
    method: "get",
  });
}

export function revokeWorkflow(processInstanceId: string, reason?: string) {
  return request({
    url: `${baseUrl}/instances/${processInstanceId}/revoke`,
    method: "post",
    params: { reason },
  });
}

export function terminateWorkflow(processInstanceId: string, reason?: string) {
  return request({
    url: `${baseUrl}/instances/${processInstanceId}/terminate`,
    method: "post",
    params: { reason },
  });
}

export function getTodoTaskPage(params: WfTaskQuery): AxiosPromise<WfTaskPageResult> {
  return request({
    url: `${baseUrl}/tasks/todo/page`,
    method: "get",
    params,
  });
}

export function getDoneTaskPage(params: WfTaskQuery): AxiosPromise<WfTaskPageResult> {
  return request({
    url: `${baseUrl}/tasks/done/page`,
    method: "get",
    params,
  });
}

export function completeTask(taskId: string, data: TaskApproveForm) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/complete`,
    method: "post",
    data,
  });
}

export function rejectTask(taskId: string, data: TaskApproveForm) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/reject`,
    method: "post",
    data,
  });
}

export function transferTask(taskId: string, data: TaskApproveForm) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/transfer`,
    method: "post",
    data,
  });
}

export function delegateTask(taskId: string, data: TaskApproveForm) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/delegate`,
    method: "post",
    data,
  });
}

export function addSignTask(taskId: string, data: TaskApproveForm) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/add-sign`,
    method: "post",
    data,
  });
}

export function claimTask(taskId: string) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/claim`,
    method: "post",
  });
}

export function unclaimTask(taskId: string) {
  return request({
    url: `${baseUrl}/tasks/${taskId}/unclaim`,
    method: "post",
  });
}
