# Decision Engine

决策引擎微服务，覆盖《决策引擎功能点调研文档.md》里的 MVP 主链路，并预留高级模块管理能力。

## 已实现

- 场景、变量、规则、规则集、决策流、数据源、模型、评分卡、决策表统一资产管理
- 规则条件配置：`= != > < >= <= in not in contains between regex is null is not null`
- 嵌套 AND/OR 条件组、复杂对象点路径取值
- 规则动作：决策结果、风险等级、评分、标签、原因码、是否短路
- 规则集执行：按优先级/指定顺序执行、任一命中、全部命中、短路
- 实时决策 API、规则测试 API、决策流测试 API
- 发布记录、版本快照、回滚、审计日志、执行日志
- 监控工作台：调用量、耗时、结果分布、热门命中规则、异常数
- 静态前端控制台：资产管理、发布、复制、删除、策略测试、日志查看

## 接入 youlai-cloud

- 服务名：`decision-engine`
- 默认端口：`18080`
- 数据库：MySQL，建表脚本见 `docs/sql/decision_engine.sql`
- DAO：MyBatis-Plus `BaseMapper`
- Nacos 配置示例：`docs/nacos/decision-engine.yaml`
- 网关路由示例：`docs/nacos/youlai-gateway-decision-engine-route.yaml`

## 启动

```bash
mvn -pl decision-engine -am spring-boot:run
```

浏览器打开：

- 控制台：http://localhost:18080
- 网关访问：http://localhost:9999/decision-engine

## 示例 API

```bash
curl -X POST http://localhost:18080/api/v1/decision-engine/decision/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "sceneCode":"trade_risk",
    "eventId":"EVT202606180001",
    "userId":"10001",
    "params":{"orderAmount":12000,"ip":"127.0.0.1","deviceId":"D001","city":"重庆"}
  }'
```

更完整的前端配置、资产 JSON 和调用示例见：

- `docs/md/决策引擎使用示例.md`

## 数据模型

核心使用 `decision_artifact` 承载策略资产：

- `SCENE`
- `VARIABLE`
- `RULE`
- `RULE_SET`
- `FLOW`
- `DATA_SOURCE`
- `MODEL`
- `SCORE_CARD`
- `DECISION_TABLE`

审计、发布、版本、执行日志分别落在独立表中。
