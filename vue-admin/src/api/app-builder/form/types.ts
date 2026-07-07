import type { AppBuilderFormSchema } from "@/views/app-builder/form/types";

export interface AppBuilderFormQuery extends PageQuery {
  appId?: number;
  modelId?: number;
  formKey?: string;
  formName?: string;
  keywords?: string;
  status?: string;
}

export interface AppBuilderForm {
  id?: number;
  appId?: number;
  modelId?: number;
  formKey: string;
  formName: string;
  formSchema: string;
  status?: string;
  version?: number;
  remark?: string;
  deleted?: number;
  createTime?: string;
  updateTime?: string;
}

export interface AppBuilderFormPayload {
  id?: number;
  appId?: number;
  modelId?: number;
  formKey: string;
  formName: string;
  formSchema: string;
  status?: string;
  remark?: string;
}

export interface AppBuilderFormDetail extends AppBuilderForm {
  schema?: AppBuilderFormSchema;
}

export type AppBuilderFormPageResult = PageResult<AppBuilderForm[]>;
