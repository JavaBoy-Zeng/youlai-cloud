export interface CollectDashboard {
  modelCount: number;
  apiCount: number;
  taskCount: number;
  runningCount: number;
  successCount: number;
  failedCount: number;
}

export interface CollectModelField {
  id?: number;
  modelId?: number;
  fieldName: string;
  fieldCode: string;
  fieldType: string;
  requiredFlag: number;
  uniqueFlag: number;
  defaultValue?: string;
  lengthLimit?: number;
  formatRule?: string;
  dictTypeCode?: string;
  sort?: number;
}

export interface CollectModel {
  id?: number;
  modelName: string;
  modelCode: string;
  targetTableName?: string;
  status: string;
  fieldCount?: number;
  remark?: string;
  fields?: CollectModelField[];
}

export interface CollectApi {
  id?: number;
  apiName: string;
  apiCode: string;
  collectType: string;
  sourceName?: string;
  timeoutSeconds?: number;
  maxFetchCount?: number;
  parseConfig?: string;
  configJson?: string;
  status: string;
  remark?: string;
}

export interface CollectDbSource {
  id?: number;
  sourceName: string;
  dbType: string;
  jdbcUrl: string;
  driverClass?: string;
  username?: string;
  passwordEncrypt?: string;
  connectTimeout?: number;
  queryTimeout?: number;
  poolConfig?: string;
  poolMinSize?: number;
  poolMaxSize?: number;
  validationQuery?: string;
  lastTestTime?: string;
  lastTestStatus?: string;
  status: string;
}

export interface CollectTask {
  id?: number;
  taskName: string;
  taskCode: string;
  modelId?: number;
  apiId?: number;
  scheduleType: string;
  cronExpr?: string;
  jobId?: number;
  collectMode: string;
  lastSuccessTime?: string;
  lastCursor?: string;
  insertStrategy: string;
  maxFetchCount?: number;
  mappingJson?: string;
  transformJson?: string;
  status: string;
  remark?: string;
}

export interface CollectInstance {
  id: number;
  taskId: number;
  jobLogId?: number;
  traceId: string;
  mqMessageId?: string;
  triggerType: string;
  status: string;
  startTime?: string;
  endTime?: string;
  totalCount?: number;
  validCount?: number;
  invalidCount?: number;
  duplicateCount?: number;
  insertedCount?: number;
  updatedCount?: number;
  failedCount?: number;
  errorMessage?: string;
}

export interface CollectTaskMessage {
  id: number;
  taskId: number;
  instanceId: number;
  traceId: string;
  mqTopic?: string;
  mqMessageId?: string;
  messageBody?: string;
  sendStatus: string;
  consumeStatus: string;
  sendTime?: string;
  consumeTime?: string;
  finishTime?: string;
  errorMessage?: string;
}

export interface CollectRawData {
  id: number;
  taskId: number;
  instanceId: number;
  traceId: string;
  dataIndex: number;
  rawBody?: string;
  status: string;
  errorMessage?: string;
}

export interface CollectErrorData {
  id: number;
  taskId: number;
  instanceId: number;
  traceId: string;
  dataIndex: number;
  errorType: string;
  errorMessage?: string;
  rawBody?: string;
  transformedBody?: string;
}

export interface CollectQualityReport {
  id?: number;
  taskId?: number;
  instanceId?: number;
  traceId?: string;
  totalCount?: number;
  validCount?: number;
  invalidCount?: number;
  duplicateCount?: number;
  insertedCount?: number;
  updatedCount?: number;
  failedCount?: number;
  fieldCompletenessJson?: string;
  summaryJson?: string;
}

export interface CollectQuery extends PageQuery {
  keywords?: string;
  status?: string;
  collectType?: string;
}
