# 企业级知识库系统执行计划

## 1. 项目目标

建设一个企业级知识库系统，不止实现“上传文档 + 向量检索 + 大模型问答”，而是支持：

- 文档可管理：上传、解析、版本、上下线、重建索引、失败重试
- 权限可隔离：租户、知识库、文档、部门、角色、用户权限控制
- 答案可追溯：回答必须附带文档、页码、片段、版本来源
- 检索可调优：支持向量检索、关键词检索、混合检索、Rerank
- 模型可切换：通过模型网关统一接入 OpenAI、Qwen、DeepSeek、本地模型等
- 数据可审计：记录上传、检索、问答、引用、失败任务、用户反馈
- 系统可运维：支持任务状态、重试、限流、监控、日志、告警

默认技术栈：

- 后端：Spring Boot / Spring Cloud Alibaba
- 数据库：PostgreSQL + pgvector，后续可扩展 Milvus
- 缓存：Redis
- 文件存储：MinIO / NAS
- 消息队列：RocketMQ
- 全文检索：Elasticsearch
- 文档解析：Apache Tika / PDFBox / POI / OCR
- 模型接入：统一 Model Gateway

## 2. 分期执行

### 第一期：MVP 版本

目标：实现基础知识库闭环，做到“文档能入库、问题能回答、答案有引用”。

交付内容：

- 知识库管理：新增、编辑、删除、分页查询、详情查看
- 文档上传：支持 PDF、Word、TXT、Markdown
- 文件存储：原始文件保存到 MinIO / NAS
- 异步处理：上传后通过 RocketMQ 触发解析、切片、向量化
- 文档解析：抽取文本、页码、标题层级和基础元数据
- 文档切片：支持按标题、段落、固定 token 切片
- 向量化：调用 Embedding 模型生成向量并写入 pgvector
- AI 问答：根据用户问题检索 TopK 片段，组装上下文调用大模型
- 引用溯源：回答返回文档名、页码、chunkId、引用内容
- 问答历史：保存会话、问题、答案、引用记录
- 基础后台：查看文档状态、索引状态、处理失败原因

验收标准：

- 用户可以创建知识库并上传文档
- 文档上传后能自动完成解析、切片、向量化
- 用户提问后能返回答案和引用来源
- 没有检索结果时，系统明确返回“知识库中未找到相关依据”
- 问答记录和引用记录可追溯

### 第二期：企业可用版本

目标：补齐权限、安全、检索质量和运维能力。

交付内容：

- 多租户：所有知识库、文档、chunk、任务、问答记录都带 tenantId
- 权限控制：支持知识库、文档、部门、角色、用户级权限
- 检索层权限过滤：向量检索和关键词检索前必须基于权限 metadata 过滤
- 文档版本：支持同一文档多版本，chunk 绑定 documentVersion
- 文档上下线：旧版本 chunk 可置为 inactive，避免旧向量残留
- 混合检索：接入 Elasticsearch BM25 + pgvector / Milvus 向量检索
- Rerank：对召回结果进行重排序，提升最终上下文质量
- 索引任务管理：记录 parse、chunk、embedding、index 每阶段状态
- 失败重试：支持手动重试、自动重试、错误原因查看
- 安全控制：文件类型白名单、大小限制、敏感词检测、Prompt Injection 防护
- 审计日志：记录上传、删除、问答、下载、重建索引、权限变更
- 模型配置：不同知识库可配置不同 LLM 和 Embedding 模型
- 限流控制：按用户、租户、接口维度限制调用频率

验收标准：

- 无权限文档不能被检索、不能进入 Prompt、不能出现在引用中
- 文档更新后，新问题只引用当前有效版本
- 解析或向量化失败时，可定位失败阶段并重新处理
- 混合检索效果优于单纯向量检索
- 所有关键操作可在审计日志中查询

### 第三期：企业增强版本

目标：建设知识运营、质量评估和智能化扩展能力。

交付内容：

- 多知识库联合问答
- 企业微信、钉钉、飞书接入
- 网页知识采集
- API 文档自动问答
- 数据库知识接入
- SQL 问数能力
- 无答案问题沉淀
- 用户反馈闭环
- 热门问题统计
- 知识过期提醒
- 问答质量评估
- 多模型路由
- 私有化模型接入
- Agent 工作流扩展

验收标准：

- 可统计高频问题、低质量回答、无答案问题
- 用户反馈可进入知识优化流程
- 可按知识库、租户、模型统计调用量和成本
- 可接入企业 IM 渠道完成问答
- 涉密知识库可切换到私有化模型

## 3. 核心模块拆解

### 知识库模块

建设内容：

- 知识库 CRUD
- 知识库可见范围配置
- 知识库 owner 管理
- 默认模型配置
- 默认切片策略配置

核心表：

- `kb_knowledge_base`
- `kb_model_config`

### 文档模块

建设内容：

- 文档上传
- 文档预览
- 文档版本管理
- 文档上下线
- 文档删除
- 文档重新索引
- 重复文件识别
- 批量导入

核心表：

- `kb_document`
- `kb_document_version`
- `kb_document_block`
- `kb_index_task`

### 解析与索引模块

建设内容：

- 文件类型识别
- 文本抽取
- 表格抽取
- 图片 OCR
- 标题层级识别
- 文本清洗
- chunk 切片
- embedding 生成
- 向量索引写入
- ES 关键词索引写入

核心表：

- `kb_document_block`
- `kb_chunk`
- `kb_index_task`

### 检索与问答模块

建设内容：

- Query Rewrite
- 权限过滤
- BM25 检索
- 向量检索
- 结果合并去重
- Rerank 重排序
- 上下文组装
- 大模型生成
- 引用来源返回
- 低置信度拒答

核心表：

- `kb_chat_session`
- `kb_chat_message`
- `kb_reference`
- `kb_feedback`

### 权限与审计模块

建设内容：

- 租户隔离
- 知识库权限
- 文档权限
- 部门权限
- 用户权限
- 角色权限
- 下载权限
- 问答权限
- 操作审计
- 问答审计

核心表：

- `kb_permission`
- `kb_audit_log`

## 4. 核心接口

知识库接口：

```http
POST   /api/kb
GET    /api/kb/page
GET    /api/kb/{id}
PUT    /api/kb/{id}
DELETE /api/kb/{id}
```

文档接口：

```http
POST   /api/kb/{kbId}/documents/upload
GET    /api/kb/{kbId}/documents/page
GET    /api/documents/{id}
DELETE /api/documents/{id}
POST   /api/documents/{id}/reindex
```

问答接口：

```http
POST /api/kb/chat
GET  /api/kb/chat/sessions
GET  /api/kb/chat/sessions/{id}/messages
POST /api/kb/chat/feedback
```

管理接口：

```http
GET  /api/kb/index/tasks
POST /api/kb/index/tasks/{id}/retry
GET  /api/kb/audit/logs
GET  /api/kb/statistics
```

## 5. 测试与验收

功能测试：

- 创建知识库、上传文档、解析文档、生成 chunk、向量入库
- 用户提问后返回答案、引用来源和 traceId
- 文档重新上传后生成新版本，旧版本不再被引用
- 索引失败后可重试并恢复状态

权限测试：

- 无权限用户不能查看知识库
- 无权限用户不能下载文档
- 无权限文档不能进入检索结果
- 无权限内容不能出现在大模型上下文和回答引用中

检索测试：

- 专有名词、编号、金额、日期类问题走关键词召回
- 语义相似问题走向量召回
- 混合检索结果经过 Rerank 后相关性提升
- 无依据问题必须拒答

安全测试：

- 非法文件类型上传失败
- 超大文件上传失败
- Prompt Injection 文档内容不能覆盖系统指令
- 敏感问题按权限和策略处理
- 高频请求触发限流

运维测试：

- 解析、切片、向量化、索引任务状态可查询
- 失败任务记录错误原因
- 审计日志可查询关键操作
- 日志中可通过 traceId 串联一次完整问答链路

## 6. 关键风险与应对

- 答案不准：通过混合检索、Rerank、优化切片、引用溯源、低置信度拒答解决
- 权限越权：权限控制下沉到检索层，metadata 中携带 tenantId、kbId、documentId、permissionScope
- 旧向量残留：chunk 绑定文档版本，文档更新时旧 chunk 置为 inactive
- 大文件处理慢：使用 MQ 异步任务、分阶段状态记录、失败重试和批量限流
- 表格问答差：表格单独解析，保留表头，必要时转结构化数据或接入 SQL Agent
- 模型供应商绑定：通过 Model Gateway 抽象 LLM 和 Embedding 接口

## 7. 默认假设

- 第一阶段优先做单体 Spring Boot 版本，降低交付复杂度
- MVP 使用 PostgreSQL + pgvector，企业增强阶段再引入 Milvus
- MVP 先支持 PDF、Word、TXT、Markdown，Excel、PPT、OCR 放到后续版本
- 问答必须基于知识库上下文，禁止无依据自由发挥
- 所有回答默认必须返回引用来源
- 权限校验必须同时存在于接口层、检索层和下载层
