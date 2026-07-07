export interface AppBuilderAppQuery extends PageQuery {
  keywords?: string;
  status?: string;
}

export interface AppBuilderApp {
  id?: number;
  appCode: string;
  appName: string;
  appDesc?: string;
  appIcon?: string;
  category?: string;
  status?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export type AppBuilderAppPageResult = PageResult<AppBuilderApp[]>;

export interface AppBuilderModelQuery extends PageQuery {
  appId?: number;
  keywords?: string;
  status?: string;
}

export interface AppBuilderModel {
  id?: number;
  appId?: number;
  modelCode: string;
  modelName: string;
  tableName?: string;
  mainField?: string;
  enableFlow?: number;
  formKey?: string;
  processKey?: string;
  status?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export type AppBuilderModelPageResult = PageResult<AppBuilderModel[]>;

export interface AppBuilderModelField {
  id?: number;
  modelId?: number;
  fieldCode: string;
  fieldName: string;
  fieldType?: string;
  dbType?: string;
  required?: number;
  defaultValue?: string;
  optionsJson?: string;
  validateJson?: string;
  sortOrder?: number;
}

export interface AppBuilderModelFieldVersion {
  id?: number;
  modelId?: number;
  versionNo?: number;
  fieldsSnapshotJson?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AppBuilderPageQuery extends PageQuery {
  appId?: number;
  modelId?: number;
  pageType?: string;
  keywords?: string;
}

export interface AppBuilderPage {
  id?: number;
  appId?: number;
  modelId?: number;
  pageType: string;
  pageName: string;
  pageSchema?: string;
  status?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export type AppBuilderPagePageResult = PageResult<AppBuilderPage[]>;

export interface AppBuilderMenu {
  id?: number;
  appId?: number;
  pageId?: number;
  menuName: string;
  routePath: string;
  routeName?: string;
  component?: string;
  perm?: string;
  icon?: string;
  visible?: number;
  sortOrder?: number;
  status?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AppBuilderDataQuery extends PageQuery {
  keywords?: string;
  status?: string;
}

export type AppBuilderDataRow = Record<string, any>;
export type AppBuilderDataPageResult = PageResult<AppBuilderDataRow[]>;
