# 企业级 AI 知识库系统设计与面试说明文档

## 一、项目背景

企业内部通常会沉淀大量文档，例如制度文件、项目文档、接口文档、运维手册、FAQ、产品说明、业务流程说明等。这些知识分散在不同文件、不同系统和不同部门中，传统方式主要依赖人工搜索和人工查阅，效率较低。

因此设计一个企业级 AI 知识库系统，目标是将企业内部文档进行统一管理、解析、切片、向量化和检索，并结合大模型实现智能问答。系统不仅要支持“上传文档后可以问答”，还要满足企业级场景下的权限隔离、引用溯源、文档版本管理、审计日志、失败重试、模型可切换、数据安全和系统可运维等要求。

一句话概括：

> 企业级知识库不是简单调用大模型接口，而是把企业内部知识安全、准确、可追溯地接入大模型。

---

## 二、系统整体架构

系统整体可以分为以下几层：

```text
前端层
  ├─ 知识库管理
  ├─ 文档上传与预览
  ├─ AI 智能问答
  ├─ 问答历史
  ├─ 权限配置
  └─ 后台管理

网关层
  ├─ 统一鉴权
  ├─ 接口限流
  ├─ 日志追踪
  └─ 租户隔离

业务服务层
  ├─ 知识库服务
  ├─ 文档服务
  ├─ 文档解析服务
  ├─ 文档切片服务
  ├─ 向量化服务
  ├─ 检索服务
  ├─ 问答服务
  ├─ 权限服务
  ├─ 审计服务
  └─ 模型网关服务

数据存储层
  ├─ MySQL/PostgreSQL：业务数据
  ├─ Redis：缓存、会话、任务状态
  ├─ MinIO/NAS：原始文件存储
  ├─ Elasticsearch：关键词检索/BM25
  └─ Milvus/pgvector/ES Vector：向量检索
```

技术选型可以结合 Java 后端技术栈：

```text
后端框架：Spring Boot / Spring Cloud Alibaba
权限鉴权：Sa-Token / Spring Security / JWT
数据库：MySQL / PostgreSQL
缓存：Redis
消息队列：RocketMQ
文件存储：MinIO / NAS
全文检索：Elasticsearch
向量数据库：Milvus / pgvector / Elasticsearch Vector
文档解析：Apache Tika / PDFBox / POI / OCR
模型接入：OpenAI / Qwen / GLM / DeepSeek / 私有化大模型
部署运维：Docker / Jenkins / Nginx / Linux
```

---

## 三、核心业务模块

### 1. 知识库管理模块

知识库是系统的顶层业务容器，可以按照业务场景划分不同知识库，例如：

```text
公司制度知识库
项目文档知识库
运维手册知识库
客服 FAQ 知识库
产品需求知识库
合同法务知识库
```

核心能力包括：

```text
知识库新增、编辑、删除
知识库权限配置
知识库模型配置
知识库文档统计
知识库问答记录
知识库状态管理
```

核心表设计：

```sql
kb_knowledge_base
- id
- tenant_id
- name
- description
- visibility
- owner_id
- embedding_model
- llm_model
- chunk_strategy
- status
- create_time
- update_time
```

---

### 2. 文档管理模块

文档管理不能只是简单上传文件，还要支持完整的文档生命周期。

文档状态流转：

```text
上传中 -> 解析中 -> 切片中 -> 向量化中 -> 已发布 -> 已下线 -> 已删除
```

核心能力包括：

```text
文档上传
文档预览
文档分类
文档标签
文档版本管理
重复文件识别
文档重新索引
文档上下线
文档删除
失败重试
```

文档表设计：

```sql
kb_document
- id
- kb_id
- tenant_id
- file_name
- file_type
- file_size
- file_url
- md5
- version
- parse_status
- index_status
- permission_type
- uploader_id
- create_time
- update_time
```

企业级知识库中，文档版本管理非常重要。因为文档更新后，不能简单覆盖旧文档，否则后续审计时无法追溯某次问答到底引用的是哪个版本的内容。

---

## 四、文档入库流程

文档入库是知识库系统的核心流程之一。

整体流程如下：

```text
用户上传文档
  ↓
保存原始文件到 MinIO/NAS
  ↓
保存文档记录
  ↓
发送 MQ 异步任务
  ↓
文档解析
  ↓
文本清洗
  ↓
文档切片
  ↓
调用 Embedding 模型生成向量
  ↓
写入向量数据库
  ↓
写入 Elasticsearch 关键词索引
  ↓
更新文档索引状态
```

这里使用 RocketMQ 做异步处理，原因是文档解析、切片和向量化都是耗时任务。如果在上传接口中同步处理，会导致接口响应慢，甚至出现超时。

可以这样表达：

> 文档上传成功后，我会先保存原始文件和文档元数据，然后通过 RocketMQ 投递异步解析任务。消费者负责文档解析、切片、向量化和索引构建。每个阶段都会记录任务状态，支持失败重试和人工重新索引，避免大文件处理阻塞主流程。

---

## 五、文档解析与切片设计

### 1. 文档解析

系统需要支持多种类型文档：

```text
PDF
Word
Excel
PPT
Markdown
TXT
HTML
图片 OCR
网页内容
接口文档
数据库知识
```

解析流程：

```text
文件类型识别
  ↓
文本抽取
  ↓
表格抽取
  ↓
图片 OCR
  ↓
页眉页脚清洗
  ↓
标题层级识别
  ↓
元数据提取
  ↓
生成标准文本结构
```

企业级场景中，不能只保存纯文本，最好将文档解析结果保存为结构化块。

```sql
kb_document_block
- id
- document_id
- block_type
- page_no
- sheet_name
- content
- metadata_json
- sort_no
```

block_type 可以包括：

```text
title
paragraph
table
image
code
```

这样后续做引用溯源时，可以精确到文档、页码、段落、表格或 Sheet。

---

### 2. 文档切片

切片不是简单按照固定字数截断，而是要结合文档结构。

常见切片策略：

```text
按标题层级切片
按段落切片
按固定 token 长度切片
按语义边界切片
表格单独切片
代码块单独切片
FAQ 问答对切片
```

chunk 表设计：

```sql
kb_chunk
- id
- document_id
- kb_id
- tenant_id
- chunk_no
- title_path
- content
- content_hash
- token_count
- page_start
- page_end
- metadata_json
- status
- create_time
```

切片时需要保留元数据：

```text
tenant_id
kb_id
document_id
chunk_id
title_path
page_no
tags
version
permission_scope
```

这些元数据后续会用于权限过滤、引用溯源和检索排序。

---

## 六、RAG 问答核心流程

RAG 全称是 Retrieval-Augmented Generation，也就是检索增强生成。

简单来说，就是用户提问后，系统先从知识库中检索相关内容，再把相关内容作为上下文交给大模型生成答案。

完整流程：

```text
用户提问
  ↓
问题改写
  ↓
问题向量化
  ↓
权限过滤
  ↓
混合检索
  ↓
结果去重
  ↓
Rerank 重排序
  ↓
上下文组装
  ↓
大模型生成答案
  ↓
返回答案和引用来源
  ↓
记录问答日志和审计日志
```

问答接口示例：

```http
POST /api/kb/chat
```

请求示例：

```json
{
  "kbId": "1001",
  "sessionId": "xxx",
  "question": "员工年假怎么计算？",
  "stream": true
}
```

返回示例：

```json
{
  "answer": "根据公司制度，员工年假按照累计工作年限计算...",
  "references": [
    {
      "documentName": "员工手册2026版.pdf",
      "pageNo": 12,
      "chunkId": "xxx",
      "content": "员工累计工作满1年不满10年的，年休假5天..."
    }
  ],
  "traceId": "xxx"
}
```

Prompt 设计重点：

```text
你是企业知识库助手。
你只能根据提供的上下文回答。
如果上下文没有答案，请明确说明“知识库中未找到相关依据”。
回答必须附带引用来源。
不要编造制度、金额、时间、法律条款。
```

---

## 七、向量召回与多文档知识聚合检索

“基于向量召回实现多文档知识聚合检索”的意思是：

> 用户提问时，系统不是只查一个文档，而是从整个知识库中召回多个语义相关的文档片段，再经过过滤、排序、去重和上下文组装，最终交给大模型综合回答。

例如企业知识库中有以下文档：

```text
员工手册.pdf
请假制度.docx
薪酬福利说明.pdf
劳动合同模板.docx
离职流程说明.md
```

用户提问：

```text
员工离职后年假没有休完怎么处理？
```

系统可能会从多个文档中召回：

```text
员工手册.pdf：年假规则
请假制度.docx：未休年假折算规则
离职流程说明.md：离职结算流程
薪酬福利说明.pdf：工资结算时间
```

然后把这些片段聚合起来，交给大模型生成答案。

实现流程：

```text
用户问题
  ↓
调用 Embedding 模型转成问题向量
  ↓
到向量库中检索相似 chunk
  ↓
从多个文档中召回相关片段
  ↓
根据用户权限过滤无权内容
  ↓
对结果进行去重和排序
  ↓
通过 Rerank 提升相关性
  ↓
组装上下文
  ↓
大模型生成答案
  ↓
返回引用来源
```

面试表达：

> 我们先把企业文档解析成文本，然后按照标题、段落或 token 长度切成多个 chunk，再调用 Embedding 模型将每个 chunk 转成向量，存入向量数据库。用户提问时，也会先转成向量，然后从向量库中召回语义相似的 chunk。这些 chunk 可能来自不同文档，比如制度文档、流程文档、接口文档。召回后会根据权限、相似度、文档来源和重复度进行过滤排序，再聚合成上下文交给大模型生成答案，并返回引用来源。

---

## 八、BM25 与混合检索设计

企业知识库不能只依赖向量检索。

向量检索擅长语义相似，例如：

```text
用户问：员工离职后公司怎么补偿？
系统可以召回：解除劳动合同经济补偿规则
```

但是向量检索对一些精确内容不一定稳定，例如：

```text
错误码 50013
接口 /api/user/list
合同编号 CQ-2026-001
政策条款编号
专有名词
金额
日期
```

这类问题更适合 BM25 关键词检索。

BM25 是一种关键词相关性算法，常用于 Elasticsearch、Lucene、Solr。它会综合考虑：

```text
关键词在文档中出现的频率
关键词在整个语料中的稀有程度
文档长度归一化
```

因此企业知识库中更适合使用混合检索：

```text
向量检索：解决语义相似
BM25 检索：解决关键词精确匹配
Rerank 重排序：提升最终结果相关性
```

混合检索流程：

```text
用户问题
  ↓
Query Rewrite
  ↓
Embedding 向量化
  ↓
Elasticsearch BM25 检索 TopN
  ↓
向量数据库检索 TopN
  ↓
结果合并
  ↓
去重
  ↓
权限过滤
  ↓
Rerank 重排序
  ↓
取 TopK 作为上下文
```

面试表达：

> 在 RAG 检索层，我不会只做向量检索，而是采用向量检索和 BM25 关键词检索结合的方式。向量检索负责语义召回，BM25 负责精确关键词召回，例如接口名、错误码、编号、政策条款等。两路召回结果合并去重后，再通过权限过滤和 Rerank 重排序，最终选取 TopK 片段作为大模型上下文。

---

## 九、权限与安全设计

企业级知识库的核心要求之一是权限隔离。

权限不能只在页面层控制，必须下沉到检索层。否则用户虽然看不到某个文档，但如果向量检索召回了无权限内容，并放入 Prompt 中，大模型仍然可能回答出来，造成数据泄露。

权限模型：

```sql
kb_permission
- id
- resource_type
- resource_id
- subject_type
- subject_id
- permission
```

resource_type 可以是：

```text
knowledge_base
document
folder
```

subject_type 可以是：

```text
user
role
dept
```

permission 可以是：

```text
read
write
admin
```

检索时的权限控制：

```text
用户提问
  ↓
获取用户身份、角色、部门
  ↓
计算可访问知识库和文档范围
  ↓
向量检索时通过 metadata filter 过滤
  ↓
BM25 检索时通过 tenant_id、kb_id、document_id 过滤
  ↓
只将有权限的 chunk 组装到 Prompt
```

安全设计包括：

```text
租户隔离
知识库权限
文档权限
接口鉴权
文件上传白名单
文件大小限制
敏感信息脱敏
Prompt Injection 防护
接口限流
操作审计
问答日志留痕
```

面试表达：

> 企业知识库的权限不能只放在前端页面控制，必须在检索层做权限过滤。我的设计是在 chunk metadata 中保存 tenantId、kbId、documentId、deptId、permissionScope 等信息，向量检索和关键词检索时都先基于权限条件过滤，保证无权限内容不会进入大模型上下文，从源头避免越权问答。

---

## 十、模型网关设计

企业级系统不能和某一个大模型厂商强绑定，因此需要设计模型网关。

统一接口：

```java
public interface LlmClient {
    ChatResult chat(ChatRequest request);

    Stream<ChatChunk> streamChat(ChatRequest request);
}

public interface EmbeddingClient {
    List<Float> embed(String text);
}
```

可接入模型：

```text
OpenAI
通义千问
智谱 GLM
DeepSeek
MiniMax
本地私有化模型
```

模型网关的作用：

```text
统一大模型调用入口
支持不同模型切换
支持流式输出
支持失败重试
支持超时控制
支持成本统计
支持私有化部署
支持不同知识库配置不同模型
```

面试表达：

> 我会单独封装模型网关层，把 Chat 模型和 Embedding 模型抽象成统一接口。业务层不直接依赖某一个大模型厂商，而是通过模型网关调用。这样后续可以灵活切换 OpenAI、通义千问、智谱、DeepSeek 或本地私有化模型，也方便做限流、重试、降级和成本统计。

---

## 十一、核心数据表设计

系统核心表包括：

```text
kb_knowledge_base        知识库表
kb_document              文档表
kb_document_version      文档版本表
kb_document_block        文档解析块表
kb_chunk                 文档切片表
kb_chat_session          会话表
kb_chat_message          问答消息表
kb_reference             引用记录表
kb_permission            权限表
kb_index_task            索引任务表
kb_model_config          模型配置表
kb_audit_log             审计日志表
kb_feedback              用户反馈表
```

索引任务表设计：

```sql
kb_index_task
- id
- document_id
- task_type
- status
- retry_count
- error_msg
- start_time
- end_time
```

task_type 可以是：

```text
parse
chunk
embedding
index
```

status 可以是：

```text
waiting
running
success
failed
```

有了任务表后，文档解析、向量化、索引构建等过程都可以追踪，也方便失败重试和后台运维。

---

## 十二、核心接口设计

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

---

## 十三、项目难点与解决方案

### 难点一：答案不准确

原因：

```text
文档切片不合理
召回结果相关性不高
只使用单一向量检索
上下文过长或噪声太多
大模型幻觉
```

解决方案：

```text
优化切片策略
使用 BM25 + 向量混合检索
引入 Rerank 重排序
保留标题路径和文档元数据
限制大模型只能基于上下文回答
低置信度时拒答
返回引用来源
加入用户反馈闭环
```

---

### 难点二：权限越权

原因：

```text
只在页面层控制权限
检索层没有权限过滤
无权限内容进入 Prompt
```

解决方案：

```text
chunk metadata 中保存权限字段
检索阶段先做 tenant_id、kb_id、document_id 过滤
结合用户角色、部门、资源权限计算可访问范围
无权限内容不进入上下文
文档下载和预览再次鉴权
问答日志记录引用来源
```

---

### 难点三：文档更新后旧向量残留

原因：

```text
文档重新上传后旧 chunk 没有处理
向量库中仍存在旧版本向量
问答可能引用过期内容
```

解决方案：

```text
设计文档版本号
chunk 绑定 document_version
更新文档时旧 chunk 置为 inactive
异步重建新版本索引
新版本构建完成后再切换状态
```

---

### 难点四：大文件处理慢

原因：

```text
PDF 页数多
Excel Sheet 多
OCR 耗时
Embedding 调用耗时
```

解决方案：

```text
使用 MQ 异步处理
任务分阶段执行
任务状态入库
失败自动重试
前端展示进度
大文件分片处理
消费者限流
```

---

### 难点五：表格问答效果差

原因：

```text
表格结构复杂
存在合并单元格
普通文本切片会破坏行列关系
```

解决方案：

```text
表格单独解析
保留表头
行列转自然语言
重要表格结构化入库
复杂统计问题走 SQL 查询或 SQL Agent
```

例如：

```text
部门 | 预算 | 实际支出
研发部 | 100万 | 80万
```

可以转换为：

```text
研发部的预算是100万，实际支出是80万。
```

这样更利于向量检索和大模型理解。

---

## 十四、项目亮点总结

可以在面试中重点强调以下亮点：

```text
1. 支持多类型文档上传、解析、切片和向量化。
2. 使用 RocketMQ 异步处理文档解析和索引构建，避免接口阻塞。
3. 基于 Embedding + 向量数据库实现跨文档语义召回。
4. 使用 BM25 + 向量检索的混合召回方式，提高检索准确率。
5. 引入 Rerank 对召回结果进行重排序，提升上下文质量。
6. 支持多文档知识聚合检索，解决企业知识分散问题。
7. 支持引用溯源，答案可以追溯到具体文档、页码和片段。
8. 权限控制下沉到检索层，避免越权内容进入 Prompt。
9. 支持文档版本管理，避免旧向量残留和过期知识引用。
10. 抽象模型网关，支持不同大模型和 Embedding 模型切换。
11. 支持问答历史、用户反馈、审计日志和失败重试。
12. 系统具备企业级安全性、可扩展性和可运维性。
```

---

## 十五、简历项目描述

可以写成：

> 负责企业级 AI 知识库平台后端设计与开发，支持 PDF、Word、Markdown、Excel 等多类型文档上传、解析、切片、向量化和智能问答。系统基于 Spring Boot、Redis、RocketMQ、MinIO、PostgreSQL/pgvector、Elasticsearch 构建，实现文档异步解析、混合检索、RAG 问答、引用溯源、权限隔离、问答历史和索引任务管理。通过 Embedding 向量检索 + BM25 关键词检索 + Rerank 重排序的方式提升知识召回准确率，并结合文档版本、审计日志和失败重试机制保障企业级可用性。

更精简版本：

> 参与企业级 AI 知识库系统建设，负责文档解析、切片、向量化、混合检索和 RAG 问答模块开发。基于 Embedding + 向量数据库实现跨文档语义召回，结合 BM25 和 Rerank 提升检索准确率，并通过权限过滤、引用溯源、文档版本和异步索引任务机制保障企业级可用性。

---

## 十六、面试完整回答模板

如果面试官问：

> 你们的企业知识库是怎么设计的？

可以这样回答：

> 我们这个知识库不是简单的文档问答 Demo，而是按企业级场景设计的，主要包括知识库管理、文档管理、文档解析、切片向量化、混合检索、RAG 问答、权限控制、模型网关和审计运维几个模块。
>
> 文档上传后，系统会先把原始文件保存到 MinIO 或 NAS，同时保存文档元数据。然后通过 RocketMQ 投递异步任务，由消费者完成文档解析、文本清洗、切片、Embedding 向量化和索引构建。索引层我们会同时写入向量库和 Elasticsearch，向量库用于语义检索，Elasticsearch 用于 BM25 关键词检索。
>
> 用户提问时，系统先将问题进行向量化，然后从向量库中召回语义相似的 chunk，同时通过 BM25 召回关键词匹配结果。两路结果合并后，会做去重、权限过滤和 Rerank 重排序，最后选取 TopK 片段组装成上下文交给大模型生成答案，并返回引用来源。
>
> 企业级场景里我比较关注几个点：第一是权限，权限不能只在页面层控制，必须下沉到检索层，保证无权限内容不会进入 Prompt；第二是引用溯源，答案必须能追溯到具体文档、页码和片段；第三是文档版本，文档更新后要避免旧向量残留；第四是异步任务和失败重试，保证大文件处理不会阻塞主流程；第五是模型网关，避免业务系统和某一个大模型厂商强绑定。
>
> 所以这个系统的核心不是单纯调用大模型，而是把企业内部知识通过解析、检索、权限和审计体系安全、准确、可追溯地接入大模型。

---

## 十七、面试官可能追问的问题

### 1. 为什么不用纯向量检索？

可以回答：

> 纯向量检索适合语义相似问题，但对错误码、接口名、编号、金额、日期、政策条款这类精确关键词不一定稳定。所以我会结合 BM25 做混合检索，BM25 负责精确词召回，向量检索负责语义召回，再通过 Rerank 做最终排序。

---

### 2. 什么是多文档知识聚合检索？

可以回答：

> 多文档知识聚合检索就是用户提问时，不只从单个文档中找答案，而是从整个知识库多个文档中召回相关片段。比如用户问离职年假结算，系统可能同时召回员工手册、请假制度、离职流程和薪酬制度中的相关内容，然后经过去重、排序和上下文组装后交给大模型综合回答。

---

### 3. 如何防止知识库越权？

可以回答：

> 我会把权限控制下沉到检索层。每个 chunk 的 metadata 中保存 tenantId、kbId、documentId、deptId、permissionScope 等字段。用户提问时先计算用户可访问的文档范围，向量检索和 BM25 检索都基于权限条件过滤，保证无权限内容不会进入大模型上下文。

---

### 4. 文档更新后怎么处理旧向量？

可以回答：

> 我会设计文档版本机制。每个 chunk 都绑定 document_version。文档更新后，旧版本 chunk 不直接删除，而是置为 inactive，新版本异步重新解析、切片和向量化。新版本索引构建成功后再切换为 active，避免问答引用过期内容。

---

### 5. 如何保证答案可靠？

可以回答：

> 主要从几个方面保证：第一，混合检索提高召回质量；第二，Rerank 提升最终上下文相关性；第三，Prompt 限制大模型只能基于上下文回答；第四，答案必须返回引用来源；第五，低置信度或无检索结果时不强行回答，而是提示知识库中未找到依据。

---

### 6. Rerank 的作用是什么？

可以回答：

> 向量检索和 BM25 召回的是候选结果，但候选结果不一定排序最优。Rerank 会结合用户问题和候选 chunk 内容重新计算相关性，把真正最符合问题的片段排到前面，从而提高最终送入大模型的上下文质量。

---

### 7. 为什么要用 MQ？

可以回答：

> 文档解析、OCR、切片、Embedding 和索引构建都比较耗时，如果同步处理会导致上传接口阻塞甚至超时。所以我使用 MQ 异步处理，把上传和索引构建解耦。配合任务状态表，可以实现处理进度展示、失败重试和人工重新索引。

---

### 8. 表格类文档怎么处理？

可以回答：

> 表格不能简单按普通文本切片，否则会丢失行列关系。我会对表格单独解析，保留表头、Sheet 名、行列信息，并将每一行转换为自然语言描述。对于复杂统计类问题，可以将表格结构化入库，再结合 SQL 查询或 SQL Agent 处理。

---

## 十八、最终一句话总结

> 企业级 AI 知识库的核心，是通过文档解析、切片向量化、混合检索、权限过滤、上下文聚合和引用溯源，把企业分散知识安全、准确、可追溯地提供给大模型使用，从而实现可靠的企业内部智能问答。
