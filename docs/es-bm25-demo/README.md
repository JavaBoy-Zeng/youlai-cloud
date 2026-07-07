# Elasticsearch BM25 问答召回 Demo

这个示例适合在 Mac mini 本地快速验证：

- 用 Docker 启动单节点 Elasticsearch。
- 建一个 3 主分片的问答索引。
- 写入 QA 文档时显式传 `_routing`，模拟“按租户 + 业务域”分片写入。
- 使用 Elasticsearch 默认 `match`/`multi_match` 查询做 BM25 问题召回。

## 1. 启动 Elasticsearch

```bash
cd docs/es-bm25-demo
docker compose up -d
```

确认 ES 已启动：

```bash
curl http://localhost:9200
curl "http://localhost:9200/_cat/health?v"
```

如果 Mac mini 内存比较紧，可以把 `docker-compose.yml` 里的 `ES_JAVA_OPTS=-Xms1g -Xmx1g` 调小到 `-Xms512m -Xmx512m`。

## 2. 运行写入和 BM25 召回示例

这个脚本只用 Python 标准库，不需要 `pip install`。

```bash
python3 qa_bm25_demo.py
```

也可以传入自己的问题：

```bash
python3 qa_bm25_demo.py "报销发票注意事项"
```

你会看到类似输出：

```text
Created index: qa_bm25_demo, primary shards: 3

Routing plan:
  hr-001       routing=tenant-a:hr      es_target_shard=...
  finance-002 routing=tenant-a:finance es_target_shard=...

Global BM25 recall: 差旅报销需要什么发票
...

Routed and filtered BM25 recall: 差旅报销需要什么发票 routing=tenant-a:finance
...
```

## 3. 核心概念

### 分片写入

写入时给每条文档指定 routing：

```http
POST /_bulk
{"index":{"_index":"qa_bm25_demo","_id":"finance-002","routing":"tenant-a:finance"}}
{"tenant":"tenant-a","domain":"finance","question":"差旅费如何报销？", ...}
```

同一个 routing key 会稳定路由到同一个目标主分片。示例脚本会调用 `_search_shards?routing=...` 打印 ES 实际目标分片。常见 routing key 设计：

- `tenant_id`
- `tenant_id:domain`
- `tenant_id:knowledge_base_id`

这样做的好处是：

- 同租户或同知识库的数据局部性更好。
- 查询时带同一个 `routing` 可以只查目标分片，减少 fan-out。
- 权限隔离和问题排查更直观。

注意：

- `routing` 只减少查询分片，不等于业务过滤条件；同一个物理分片上可能还有其他 routing key 的文档。
- 真正做租户/知识库隔离时，查询里还要加 `tenant_id`、`knowledge_base_id` 或示例里的 `routing_key` filter。
- routing 设计不要让某一个 key 特别大，否则会形成热点分片。

### BM25 召回

Elasticsearch 的文本查询默认使用 BM25。示例里查询语句是：

```json
{
  "query": {
    "multi_match": {
      "query": "差旅报销需要什么发票",
      "fields": ["question^3", "answer^2", "content"],
      "type": "best_fields"
    }
  }
}
```

含义：

- `question^3`：问题字段权重最高。
- `answer^2`：答案字段次之。
- `content`：把问题、答案、标签拼起来做兜底召回。

### 全局查和路由查

全局查：

```bash
curl -X POST "http://localhost:9200/qa_bm25_demo/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query":{"match":{"content":"差旅报销需要什么发票"}}}'
```

按 routing 查，并加业务过滤：

```bash
curl -X POST "http://localhost:9200/qa_bm25_demo/_search?routing=tenant-a:finance&pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": {
          "multi_match": {
            "query": "差旅报销需要什么发票",
            "fields": ["question^3", "answer^2", "content"]
          }
        },
        "filter": {
          "term": {
            "routing_key": "tenant-a:finance"
          }
        }
      }
    }
  }'
```

## 4. 生产环境建议

- 中文检索建议配置 IK、SmartCN 或业务自定义 analyzer，否则内置 analyzer 只能作为最小可跑示例。
- QA 召回通常会做两段式：BM25 先召回 TopN，再用向量模型或 reranker 精排。
- 多租户场景优先考虑 routing，索引级隔离适合强隔离或数据量特别大的租户。
- 分片数不是越多越好。本地示例用 3 个主分片只是为了演示路由，生产要按数据量、查询并发和节点规模评估。

## 5. 清理

```bash
docker compose down -v
```
