export interface WfCategory {
  id?: number;
  parentId?: number;
  name: string;
  code: string;
  sort?: number;
  status?: number;
}

export interface WfModelQuery extends PageQuery {
  categoryId?: number;
  keywords?: string;
  status?: string;
}

export interface WfModel {
  id?: number;
  categoryId?: number;
  modelKey: string;
  name: string;
  version?: number;
  status?: string;
  formKey?: string;
  bpmnXml?: string;
  configJson?: string;
  deploymentId?: string;
  processDefinitionId?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export type WfModelPageResult = PageResult<WfModel[]>;

export interface StartProcessForm {
  modelId?: number;
  processDefinitionId?: string;
  businessKey?: string;
  formDataJson?: string;
  variables?: Record<string, any>;
}

export interface WfInstanceQuery extends PageQuery {
  keywords?: string;
  status?: string;
  starterId?: number;
}

export interface WfInstance {
  id?: number;
  processInstanceId: string;
  processDefinitionId: string;
  businessKey?: string;
  businessDataId?: number;
  businessModelId?: number;
  businessAppId?: number;
  modelId?: number;
  modelKey?: string;
  modelName?: string;
  starterId?: number;
  starterUsername?: string;
  status?: string;
  formKey?: string;
  formDataJson?: string;
  currentNodeName?: string;
  startTime?: string;
  endTime?: string;
  records?: WfTaskRecord[];
}

export type WfInstancePageResult = PageResult<WfInstance[]>;

export interface WfTaskQuery extends PageQuery {
  keywords?: string;
}

export interface WfTask {
  taskId: string;
  taskName: string;
  taskDefinitionKey?: string;
  processInstanceId: string;
  processDefinitionId?: string;
  assignee?: string;
  businessKey?: string;
  processName?: string;
  starterUsername?: string;
  createTime?: string;
  endTime?: string;
}

export type WfTaskPageResult = PageResult<WfTask[]>;

export interface TaskApproveForm {
  comment?: string;
  targetActivityId?: string;
  assignee?: string;
  attachmentJson?: string;
  variables?: Record<string, any>;
}

export interface WfTaskRecord {
  taskId?: string;
  taskName?: string;
  operatorId?: number;
  operatorUsername?: string;
  action?: string;
  comment?: string;
  attachmentJson?: string;
  createTime?: string;
}

export interface ProcessDiagram {
  bpmnXml: string;
  activeActivityIds: string[];
  finishedActivityIds: string[];
}
