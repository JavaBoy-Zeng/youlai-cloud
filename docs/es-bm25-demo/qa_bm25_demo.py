#!/usr/bin/env python3
"""
Minimal Elasticsearch QA retrieval demo.

What it shows:
1. Create a QA index with multiple primary shards.
2. Bulk write documents with explicit _routing, so related tenant/domain data
   is routed to the same target shard.
3. Query with BM25 by using Elasticsearch's default match query.
4. Query with the same routing key to search only the shard that owns that data.
"""

from __future__ import annotations

import http.client
import json
import sys
import time
import urllib.parse
from typing import Any


ES_HOST = "localhost"
ES_PORT = 9200
INDEX = "qa_bm25_demo"
PRIMARY_SHARDS = 3


QA_DOCS = [
    {
        "id": "hr-001",
        "tenant": "tenant-a",
        "domain": "hr",
        "question": "如何申请年假？",
        "answer": "员工可以在考勤系统提交年假申请，直属主管审批通过后生效。",
        "tags": ["考勤", "假期", "审批"],
    },
    {
        "id": "hr-002",
        "tenant": "tenant-a",
        "domain": "hr",
        "question": "试用期可以请假吗？",
        "answer": "试用期员工可以请假，但需要按照公司考勤制度提交申请。",
        "tags": ["试用期", "请假", "考勤"],
    },
    {
        "id": "finance-001",
        "tenant": "tenant-a",
        "domain": "finance",
        "question": "报销发票需要注意什么？",
        "answer": "报销时需要上传真实有效的发票，并填写费用类型、金额和项目归属。",
        "tags": ["报销", "发票", "财务"],
    },
    {
        "id": "finance-002",
        "tenant": "tenant-a",
        "domain": "finance",
        "question": "差旅费如何报销？",
        "answer": "差旅费报销需要提交行程单、住宿发票、交通票据和审批记录。",
        "tags": ["差旅", "报销", "审批"],
    },
    {
        "id": "it-001",
        "tenant": "tenant-b",
        "domain": "it",
        "question": "忘记系统密码怎么办？",
        "answer": "可以在登录页点击忘记密码，通过手机号或邮箱验证码重置密码。",
        "tags": ["账号", "密码", "登录"],
    },
    {
        "id": "it-002",
        "tenant": "tenant-b",
        "domain": "it",
        "question": "如何开通 VPN 权限？",
        "answer": "员工需要在 IT 服务台提交 VPN 权限申请，并说明访问的业务系统。",
        "tags": ["VPN", "权限", "IT"],
    },
    {
        "id": "ops-001",
        "tenant": "tenant-b",
        "domain": "ops",
        "question": "生产告警怎么处理？",
        "answer": "值班人员应先确认告警级别，再查看监控指标、日志和最近发布记录。",
        "tags": ["告警", "监控", "运维"],
    },
]


def request(
    method: str,
    path: str,
    body: Any | None = None,
    *,
    expected: tuple[int, ...] = (200,),
) -> Any:
    payload = None
    headers = {}
    if body is not None:
        if isinstance(body, str):
            payload = body.encode("utf-8")
        else:
            payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"

    conn = http.client.HTTPConnection(ES_HOST, ES_PORT, timeout=10)
    conn.request(method, path, payload, headers)
    resp = conn.getresponse()
    raw = resp.read().decode("utf-8")
    conn.close()

    if resp.status not in expected:
        raise RuntimeError(f"{method} {path} failed: HTTP {resp.status}\n{raw}")
    if not raw:
        return None
    return json.loads(raw)


def wait_for_es() -> None:
    print("Waiting for Elasticsearch...")
    for _ in range(60):
        try:
            health = request("GET", "/_cluster/health", expected=(200,))
            print(f"Cluster status: {health['status']}")
            return
        except Exception:
            time.sleep(1)
    raise RuntimeError("Elasticsearch is not ready on http://localhost:9200")


def create_index() -> None:
    if request("HEAD", f"/{INDEX}", expected=(200, 404)) is None:
        # HEAD returns an empty body, so use the status-bearing helper below.
        pass

    conn = http.client.HTTPConnection(ES_HOST, ES_PORT, timeout=10)
    conn.request("HEAD", f"/{INDEX}")
    exists = conn.getresponse().status == 200
    conn.close()
    if exists:
        request("DELETE", f"/{INDEX}")

    mapping = {
        "settings": {
            "number_of_shards": PRIMARY_SHARDS,
            "number_of_replicas": 0,
            "similarity": {
                "default": {
                    "type": "BM25",
                    "k1": 1.2,
                    "b": 0.75,
                }
            },
        },
        "mappings": {
            "properties": {
                "tenant": {"type": "keyword"},
                "domain": {"type": "keyword"},
                "question": {"type": "text"},
                "answer": {"type": "text"},
                "tags": {"type": "keyword"},
                "routing_key": {"type": "keyword"},
                "content": {"type": "text"},
            }
        },
    }
    request("PUT", f"/{INDEX}", mapping)
    print(f"Created index: {INDEX}, primary shards: {PRIMARY_SHARDS}")


def routing_key(doc: dict[str, Any]) -> str:
    return f"{doc['tenant']}:{doc['domain']}"


def es_target_shard(routing: str) -> int:
    path = f"/{INDEX}/_search_shards?routing={urllib.parse.quote(routing)}"
    result = request("GET", path)
    return result["shards"][0][0]["shard"]


def bulk_index_docs() -> None:
    lines = []
    print("\nRouting plan:")
    route_to_shard: dict[str, int] = {}
    for doc in QA_DOCS:
        route = routing_key(doc)
        route_to_shard.setdefault(route, es_target_shard(route))
        source = {
            **doc,
            "routing_key": route,
            "content": f"{doc['question']} {doc['answer']} {' '.join(doc['tags'])}",
        }
        lines.append(json.dumps({"index": {"_index": INDEX, "_id": doc["id"], "routing": route}}, ensure_ascii=False))
        lines.append(json.dumps(source, ensure_ascii=False))
        print(f"  {doc['id']:<12} routing={route:<16} es_target_shard={route_to_shard[route]}")

    payload = "\n".join(lines) + "\n"
    result = request("POST", "/_bulk?refresh=true", payload, expected=(200,))
    if result.get("errors"):
        raise RuntimeError(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"\nIndexed {len(QA_DOCS)} QA documents.")


def search(question: str, *, routing: str | None = None, size: int = 3) -> dict[str, Any]:
    params = {"size": str(size)}
    if routing:
        params["routing"] = routing
    path = f"/{INDEX}/_search?{urllib.parse.urlencode(params)}"
    bm25_query = {
        "multi_match": {
            "query": question,
            "fields": ["question^3", "answer^2", "content"],
            "type": "best_fields",
        }
    }
    query: dict[str, Any]
    if routing:
        # Routing reduces fan-out to the target shard. The filter keeps the
        # business result set scoped to the same tenant/domain.
        query = {
            "bool": {
                "must": bm25_query,
                "filter": {"term": {"routing_key": routing}},
            }
        }
    else:
        query = bm25_query

    body = {
        "query": query,
        "highlight": {"fields": {"question": {}, "answer": {}}},
    }
    return request("POST", path, body)


def print_hits(title: str, result: dict[str, Any]) -> None:
    print(f"\n{title}")
    print("-" * len(title))
    for hit in result["hits"]["hits"]:
        source = hit["_source"]
        print(f"score={hit['_score']:.4f} id={hit['_id']} routing={source['routing_key']}")
        print(f"Q: {source['question']}")
        print(f"A: {source['answer']}")


def explain_one(question: str, doc_id: str, routing: str) -> None:
    body = {
        "query": {
            "multi_match": {
                "query": question,
                "fields": ["question^3", "answer^2", "content"],
            }
        }
    }
    result = request("POST", f"/{INDEX}/_explain/{doc_id}?routing={urllib.parse.quote(routing)}", body)
    print(f"\nBM25 explain for doc={doc_id}")
    print("--------------------------")
    print(f"matched={result['matched']}, value={result.get('explanation', {}).get('value')}")
    print("Open the full _explain API output if you want to inspect tf/idf/field-length details.")


def main() -> int:
    query = "差旅报销需要什么发票"
    if len(sys.argv) > 1:
        query = " ".join(sys.argv[1:])

    wait_for_es()
    create_index()
    bulk_index_docs()

    print_hits(f"Global BM25 recall: {query}", search(query))

    finance_route = "tenant-a:finance"
    print_hits(
        f"Routed and filtered BM25 recall: {query} routing={finance_route}",
        search(query, routing=finance_route),
    )
    explain_one(query, "finance-002", finance_route)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
