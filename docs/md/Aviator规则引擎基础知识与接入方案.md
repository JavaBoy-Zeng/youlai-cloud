# Aviator 规则引擎基础知识与接入方案

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 资料来源 | 掘金文章《Aviator规则引擎基础知识与实战解析》《Aviator规则引擎组件开发》 |
| 原文链接 | https://juejin.cn/post/7502618987068325915；https://juejin.cn/post/7480512332776538152 |
| 原文发布时间 | 2025-05-11；2025-03-12 |
| 整理目的 | 梳理 Aviator 表达式引擎的核心概念、规则建模方式、表达式构建方式、表达式执行方式，并结合当前 `decision-engine` 模块和 Hare 数据网关给出后续接入建议 |
| 适用范围 | 决策引擎、规则测试、规则集执行、决策流条件节点、风控策略、营销策略、审批自动判断 |

---

## 2. 背景说明

Aviator 是 Java 生态中的轻量级表达式求值引擎，适合把业务判断逻辑从 Java 代码中抽离出来，以字符串表达式的方式动态执行。

在决策引擎场景中，规则条件经常需要由业务人员或配置平台维护，例如：

* 订单金额大于 10000 时进入人工审核
* 用户城市属于高风险城市时提高风险等级
* 设备号、手机号、证件号命中名单时拒绝交易
* 多个条件满足一定数量后输出对应匹配度
* 根据活动规则判断用户是否满足权益发放条件

当前项目已有 `decision-engine` 模块，核心条件判断由 `ConditionEvaluator` 自研实现，已支持基础操作符、条件组、点路径取值、数字和日期比较。Aviator 可以作为后续增强方向，用于承接更复杂的表达式、函数扩展和动态计算能力。

---

## 3. Aviator 能解决什么问题

| 问题 | 使用 Aviator 后的效果 |
| --- | --- |
| 条件逻辑硬编码 | 将判断逻辑配置为表达式，减少代码变更和发布 |
| 操作符持续扩展 | 通过表达式和自定义函数承接复杂判断 |
| 规则组合复杂 | 支持 AND、OR、函数调用、字符串处理、数学计算等表达 |
| 业务规则频繁变化 | 规则内容可配置、可测试、可版本化 |
| 命中结果需要动态计算 | 可直接在表达式中计算分值、标签、阈值、匹配度 |

典型适用场景：

* 风控决策：准入规则、反欺诈规则、风险等级判断
* 营销决策：活动准入、优惠券发放、人群圈选
* 审批流：自动通过、自动拒绝、转人工审核
* 低代码平台：表单校验、字段联动、动态显隐、动态计算
* 数据筛选：根据配置条件过滤业务对象

---

## 4. 核心概念

### 4.1 表达式

表达式是 Aviator 的执行单元，通常是一段字符串：

```java
String expression = "orderAmount > 10000 && city == '重庆'";
Boolean matched = (Boolean) AviatorEvaluator.execute(expression, env);
```

表达式中的变量从运行上下文 `env` 中读取。

```java
Map<String, Object> env = new HashMap<>();
env.put("orderAmount", 15000);
env.put("city", "重庆");
```

### 4.2 运行上下文

运行上下文可以理解为规则执行时的事实数据，也就是当前项目 `ExecuteDecisionRequest.params` 中承载的业务入参。

| 当前项目概念 | Aviator 概念 | 示例 |
| --- | --- | --- |
| 请求参数 `params` | `env` 变量上下文 | `orderAmount=15000` |
| 规则条件 `conditions` | 表达式字符串 | `orderAmount > 10000` |
| 规则动作 `actions` | 命中后输出 | `decisionResult=REVIEW` |
| 执行结果 `DecisionResponse` | 表达式计算结果 + 动作汇总 | `REVIEW / HIGH / tags` |

### 4.3 自定义函数

当内置表达式无法覆盖业务判断时，可以注册自定义函数。例如集合求交、名单命中、时间窗口统计、画像标签匹配等。

```java
public class IntersectFunction extends AbstractFunction {
    @Override
    public String getName() {
        return "intersect";
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject left, AviatorObject right) {
        String leftText = String.valueOf(left.getValue(env));
        String rightText = String.valueOf(right.getValue(env));
        Set<String> leftSet = new HashSet<>(Arrays.asList(leftText.split(";")));
        Set<String> rightSet = new HashSet<>(Arrays.asList(rightText.split(";")));
        leftSet.retainAll(rightSet);
        return AviatorBoolean.valueOf(!leftSet.isEmpty());
    }
}
```

注册后即可在表达式中调用：

```java
AviatorEvaluator.addFunction(new IntersectFunction());
Boolean matched = (Boolean) AviatorEvaluator.execute("intersect(userTags, 'vip;risk')", env);
```

---

## 5. 规则模型设计

结合两篇文章的思路和当前 `decision-engine` 模块，可以将规则拆成三个核心对象：规则、条件、动作。第二篇文章的重点是将前端传入的多样化 JSON 规则统一为稳定的规则实体，再由后端组件转换为 Aviator 可执行表达式。

### 5.1 Rule 规则

| 字段 | 说明 |
| --- | --- |
| `code` | 规则编码，全局唯一 |
| `name` | 规则名称 |
| `sceneCode` | 归属业务场景 |
| `conditionExpression` | Aviator 表达式 |
| `conditions` | 可视化条件配置，用于生成表达式 |
| `actions` | 命中后的动作 |
| `fallbackAction` | 未命中时的动作 |
| `isMontageAction` | 是否将动作拼接进表达式，例如生成三元表达式 |
| `isDegree` | 是否按匹配度返回分值 |
| `priority` | 优先级 |
| `status` | 草稿、已发布、停用等状态 |

### 5.2 Condition 条件

| 字段 | 说明 | 示例 |
| --- | --- | --- |
| `field` | 事实字段 | `orderAmount` |
| `operator` | 操作符 | `>`、`in`、`between` |
| `value` | 比较值 | `10000` |
| `logic` | 条件组逻辑 | `AND`、`OR` |
| `items` | 子条件列表 | 嵌套条件组 |
| `customFunction` | 自定义函数 | `intersect`、`string.endsWith` |
| `prefix` | 字段前缀 | `user.`、`order.` |
| `isValueQuoted` | 值是否需要引号包裹 | 字符串常量需要，变量名不需要 |
| `arithmeticExpressions` | 算术表达式链 | `amount + fee - discount` |
| `isBracketed` | 是否使用括号包裹 | `(A && B) || C` |
| `expression` | 直接表达式 | 有值时直接使用，不再解析结构化条件 |

### 5.3 Action 动作

| 字段 | 说明 | 示例 |
| --- | --- | --- |
| `decisionResult` | 决策结果 | `PASS`、`REVIEW`、`REJECT` |
| `riskLevel` | 风险等级 | `LOW`、`MEDIUM`、`HIGH` |
| `score` | 分值变化 | `+20` |
| `tags` | 命中标签 | `高风险订单` |
| `outputs` | 额外输出 | `{ "channel": "manual_review" }` |
| `reason` | 命中原因 | `订单金额超过阈值` |
| `type` | 动作类型 | `RETURN`、`SET_FIELD` |
| `target` | 动作目标值 | `REVIEW`、`PASS`、`HIGH` |

---

## 6. 表达式构建方式

### 6.1 直接维护表达式

适合技术人员或高级规则配置场景。

```text
orderAmount > 10000 && city == '重庆' && string.startsWith(userLevel, 'VIP')
```

优点：

* 表达能力强
* 存储结构简单
* 适合复杂公式和高级规则

风险：

* 对业务人员不够友好
* 需要表达式校验和安全控制
* 字段名、函数名变更时需要依赖分析

### 6.2 可视化条件生成表达式

适合当前决策引擎控制台继续演进。前端维护结构化条件树，后端执行前转换为 Aviator 表达式。

条件配置示例：

```json
{
  "logic": "AND",
  "items": [
    { "field": "orderAmount", "operator": ">", "value": 10000 },
    { "field": "city", "operator": "=", "value": "重庆" }
  ]
}
```

转换后的表达式：

```text
orderAmount > 10000 && city == '重庆'
```

这种方式可以保留当前 `ConditionEvaluator` 的配置模型，同时把底层执行器替换或扩展为 Aviator。

### 6.3 通用规则 JSON 转 Aviator 表达式

第二篇文章提供了一个更组件化的方向：前端不直接拼接表达式，而是提交统一的规则 JSON，后端根据 `Rule`、`Condition`、`Action` 生成 Aviator 语句。

推荐转换原则：

| 输入结构 | 转换规则 |
| --- | --- |
| `rule.expression` 有值 | 直接使用表达式，不解析 `conditions` |
| `conditions` 有值 | 按顺序拼接字段、操作符、值、逻辑符 |
| `subConditions` 有值 | 递归构建子表达式 |
| `isBracketed = true` | 在当前条件或条件组外加括号 |
| `customFunction` 有值 | 生成函数调用表达式 |
| `arithmeticExpressions` 有值 | 先拼接算术链，再拼接比较运算符 |
| `isMontageAction = true` | 将命中动作和未命中动作拼成可执行表达式 |
| `isDegree = true` | 返回匹配度分值，未命中返回兜底值 |

规则 JSON 示例：

```json
{
  "id": "large_amount_risk",
  "expression": "",
  "conditions": [
    {
      "field": "orderAmount",
      "operator": ">",
      "value": ["10000"],
      "logic": "&&",
      "isValueQuoted": false,
      "isBracketed": false
    },
    {
      "field": "city",
      "operator": "==",
      "value": ["重庆"],
      "logic": "",
      "isValueQuoted": true,
      "isBracketed": false
    }
  ],
  "isMontageAction": true,
  "action": { "type": "RETURN", "target": "REVIEW" },
  "fallbackAction": { "type": "RETURN", "target": "PASS" }
}
```

转换后的表达式可以是：

```text
(orderAmount > 10000 && city == '重庆') ? 'REVIEW' : 'PASS'
```

### 6.4 自定义函数条件

当规则需要集合交集、字符串后缀、名单命中等判断时，条件可以不使用普通字段比较，而是生成函数调用。

结构化条件示例：

```json
{
  "customFunction": "intersect",
  "value": ["userTags", "'risk;new_user'"],
  "logic": "&&",
  "isValueQuoted": false
}
```

转换后的表达式：

```text
intersect(userTags, 'risk;new_user')
```

这种设计适合 Hare 风控场景中的三方数据标签判断，例如企业风险标签、司法风险标签、设备风险标签、名单结果等。

### 6.5 算术表达式链

复杂规则中经常需要先计算再比较，例如综合授信额度、费用合计、评分累加等。可以使用 `arithmeticExpressions` 描述算术链，避免前端直接拼接字符串。

结构化配置示例：

```json
{
  "arithmeticExpressions": [
    { "operand": "income" },
    { "operator": "-", "operand": "debt" },
    { "operator": "+", "operand": "guaranteeAmount" }
  ],
  "operator": ">=",
  "value": ["500000"],
  "isValueQuoted": false
}
```

转换后的表达式：

```text
income - debt + guaranteeAmount >= 500000
```

### 6.6 匹配度规则

文章中还提到一种适合计费项、风控评分、规则推荐的模式：一个规则中配置多个条件，最终返回“至少命中几个条件”的匹配度。

在当前决策引擎中，可以将其抽象为规则集策略：

| 字段 | 说明 |
| --- | --- |
| `isDegree` | 是否启用匹配度计算 |
| `requiredMatch` | 至少需要命中的条件数量 |
| `matchCount` | 实际命中数量 |
| `fallbackValue` | 未达到匹配度时返回值 |

示例表达：

```text
(conditionA && conditionB && conditionC) ? 3 : -1
```

更推荐在工程实现中不要只依赖一个大表达式完成匹配度，而是把每个条件拆开执行并记录命中明细：

```json
{
  "requiredMatch": 3,
  "conditions": [
    "intersect(userTags, 'risk;new_user')",
    "orderAmount > 10000",
    "city == '重庆'",
    "blackDevice == true"
  ]
}
```

这样执行日志可以清楚记录每个条件是否命中，便于排查规则效果。

---

## 7. 与当前项目的接入关系

当前 `decision-engine` 中已有以下能力：

| 已有能力 | 当前实现 | Aviator 增强点 |
| --- | --- | --- |
| 单条规则执行 | `DecisionEngineService.evaluateRule` 调用 `ConditionEvaluator` | 支持表达式编译、缓存、函数扩展 |
| 条件组 | `logic + items` 递归判断 | 转换为 `&&`、`||` 表达式 |
| 字段取值 | `ConditionEvaluator.getValue` 支持点路径 | 需要将嵌套字段扁平化或注册取值函数 |
| 操作符 | `= != > >= < <= in between regex` | 可补充字符串函数、集合函数、日期函数 |
| 规则动作 | `actions` 合并到 `DecisionContext` | 命中逻辑不变 |
| 测试规则 | `/test-rule` 接口 | 增加表达式语法校验和错误定位 |
| 执行日志 | `decision_execute_log` | 记录原始表达式、变量快照、耗时 |

建议不要一次性替换现有 `ConditionEvaluator`，而是先引入表达式执行策略：

| 策略 | 说明 |
| --- | --- |
| `STRUCTURED` | 使用当前结构化条件树执行，保持兼容 |
| `AVIATOR` | 使用 Aviator 表达式执行 |
| `MIXED` | 优先执行显式表达式，没有表达式时回退条件树 |

---

## 8. 建议落地方案

### 8.1 第一步：引入依赖

原文示例使用的依赖如下，实际版本建议由项目统一依赖管理锁定：

```xml
<dependency>
    <groupId>com.googlecode.aviator</groupId>
    <artifactId>aviator</artifactId>
    <version>5.3.0</version>
</dependency>
```

### 8.2 第二步：新增表达式执行器

建议新增 `ExpressionConditionEvaluator`，避免直接修改现有 `ConditionEvaluator`。

职责：

* 接收表达式和事实数据
* 注册系统内置函数
* 执行表达式并返回布尔值
* 捕获表达式异常并转换为业务错误
* 支持表达式编译缓存

伪代码：

```java
@Component
public class ExpressionConditionEvaluator {

    public boolean matches(String expression, Map<String, Object> facts) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        Object result = AviatorEvaluator.execute(expression, facts);
        return Boolean.TRUE.equals(result);
    }
}
```

### 8.3 第三步：扩展规则内容结构

可以在 `DecisionArtifact.contentJson` 中扩展字段：

```json
{
  "sceneCode": "trade_risk",
  "expressionType": "AVIATOR",
  "conditionExpression": "orderAmount > 10000 && city == '重庆'",
  "conditions": {
    "logic": "AND",
    "items": []
  },
  "actions": {
    "decisionResult": "REVIEW",
    "riskLevel": "MEDIUM",
    "reason": "命中高金额订单规则"
  }
}
```

### 8.4 第四步：接入规则测试

规则测试接口应返回以下信息：

| 字段 | 说明 |
| --- | --- |
| `matched` | 表达式是否命中 |
| `expression` | 实际执行表达式 |
| `facts` | 测试入参快照 |
| `actions` | 命中后动作 |
| `errorMessage` | 表达式解析或执行错误 |
| `elapsedMs` | 执行耗时 |

### 8.5 第五步：补充函数库

优先沉淀以下函数：

| 函数 | 作用 | 示例 |
| --- | --- | --- |
| `intersect(left, right)` | 判断两个集合是否有交集 | `intersect(userTags, 'vip;risk')` |
| `containsAny(left, right)` | 判断是否包含任一元素 | `containsAny(cityTags, 'highRisk')` |
| `inList(value, list)` | 判断值是否在列表中 | `inList(city, '重庆;成都')` |
| `betweenNum(value, min, max)` | 数值区间判断 | `betweenNum(score, 60, 90)` |
| `daysBetween(start, end)` | 日期差计算 | `daysBetween(registerDate, now()) < 7` |
| `jsonGet(object, path)` | JSON 路径取值 | `jsonGet(profile, 'device.id')` |

### 8.6 第六步：补充规则构建器

建议新增 `AviatorRuleStatementBuilder`，专门负责把结构化规则 JSON 转换为 Aviator 表达式。

核心职责：

* 如果 `rule.expression` 有值，直接返回表达式。
* 如果存在 `conditions`，递归解析条件和子条件。
* 支持普通比较、自定义函数、算术表达式链、括号、逻辑符。
* 支持命中动作和未命中动作拼接。
* 支持匹配度规则，但要保留每个条件的命中明细。
* 支持批量计算时复用已编译表达式，避免每条数据重复解析。

伪代码：

```java
public class AviatorRuleStatementBuilder {

    public String build(RuleConfig rule) {
        String expression = hasText(rule.getExpression())
                ? rule.getExpression()
                : buildConditions(rule.getConditions());

        if (!rule.isMontageAction()) {
            return expression;
        }
        if (rule.isDegree()) {
            return buildDegreeExpression(expression, rule.getRequiredMatch());
        }
        return "(" + expression + ") ? "
                + quote(rule.getAction().getTarget())
                + " : "
                + quote(rule.getFallbackAction().getTarget());
    }
}
```

### 8.7 第七步：与 Hare 数据网关结合

Hare 项目中，数据网关已经提供统一的外部数据调用接口：

```text
POST /service-interface-gateway/results/{shortName}/{apiName}
```

风控变量中也已经存在类似 `${shortName:apiName:absoluteKey}` 的外部数据引用方式。Aviator 接入时，建议保持“先取数，再执行表达式”的分层：

```text
业务入参
  ↓
识别表达式中的外部数据引用
  ↓
调用 Hare 数据网关
  ↓
从返回 JSON 中按 absoluteKey 取值
  ↓
写入 Aviator env
  ↓
执行 Aviator 表达式
  ↓
返回命中结果、动作、匹配度、日志
```

示例原始规则：

```text
orderAmount > 10000 && ${tyc:企业风险信息:$.data.riskCount} > 0
```

执行前应先转换为纯 Aviator 表达式：

```text
orderAmount > 10000 && tycRiskCount > 0
```

并构造上下文：

```java
Map<String, Object> env = new HashMap<>(requestParams);
env.put("tycRiskCount", riskCount);
```

不建议在主链路中把数据网关调用封装成 Aviator 函数，例如 `callGateway(...)`。原因是 Hare 数据网关已经承担了账号、密钥、有效期、缓存、日志、计费和异常治理，表达式引擎只应消费治理后的变量。

---

## 9. 多条件匹配度示例

在风控或推荐策略中，有时不是要求全部条件命中，而是统计命中数量。

示例目标：

* 命中高风险城市，加 1
* 订单金额超过阈值，加 1
* 用户标签与风险标签有交集，加 1
* 设备指纹命中名单，加 1
* 满足 3 个及以上条件时输出 `REVIEW`

示例逻辑：

```java
int matchCount = 0;

if ((Boolean) AviatorEvaluator.execute("inList(city, '重庆;成都;郑州')", env)) {
    matchCount++;
}
if ((Boolean) AviatorEvaluator.execute("orderAmount > 10000", env)) {
    matchCount++;
}
if ((Boolean) AviatorEvaluator.execute("intersect(userTags, 'risk;newUser')", env)) {
    matchCount++;
}
if ((Boolean) AviatorEvaluator.execute("inList(deviceId, deviceBlackList)", env)) {
    matchCount++;
}

String decisionResult = matchCount >= 3 ? "REVIEW" : "PASS";
```

后续也可以将匹配度抽象为规则集策略：

```json
{
  "strategy": "AT_LEAST",
  "requiredMatch": 3,
  "ruleCodes": [
    "high_risk_city",
    "large_order_amount",
    "risk_user_tag",
    "black_device"
  ]
}
```

---

## 10. 安全与治理注意事项

| 风险 | 建议 |
| --- | --- |
| 表达式语法错误 | 发布前做语法校验，测试通过后才能发布 |
| 字段不存在 | 建立变量库，表达式只能选择已注册变量 |
| 函数滥用 | 只开放白名单函数，不允许任意 Java 调用 |
| 性能抖动 | 对表达式做编译缓存，记录执行耗时 |
| 规则不可追踪 | 日志记录表达式、入参摘要、命中结果和版本号 |
| 业务人员难维护 | 前端保留可视化条件配置，高级模式再开放表达式 |
| 表达式过长 | 拆分为规则集、规则流或公共函数 |
| 数据类型不一致 | 在变量库中维护字段类型，发布前做类型检查 |
| 前端 JSON 结构不统一 | 统一 Rule / Condition / Action 协议，由后端组件生成表达式 |
| 外部数据调用混入表达式 | 先通过数据网关取数并生成变量，再执行 Aviator |
| 匹配度不可解释 | 拆分条件执行，记录每个条件的命中状态和返回值 |

---

## 11. 推荐演进路线

| 阶段 | 目标 | 交付物 |
| --- | --- | --- |
| P0 | 保持当前条件树执行稳定 | 完善 `ConditionEvaluator` 测试 |
| P1 | 引入 Aviator 执行器 | `ExpressionConditionEvaluator`、依赖、基础测试 |
| P2 | 支持表达式规则 | `conditionExpression` 字段、规则测试、执行日志 |
| P3 | 支持可视化条件转表达式 | 表达式构建器、变量库、操作符映射 |
| P4 | 支持通用规则 JSON 协议 | Rule / Condition / Action、子条件、算术链、命中/未命中动作 |
| P5 | 支持 Hare 数据网关变量预处理 | `${shortName:apiName:absoluteKey}` 解析、取数、变量写入 `env` |
| P6 | 支持函数库治理 | 系统函数、业务函数、函数说明和测试用例 |
| P7 | 支持表达式版本与灰度 | 版本对比、发布校验、回滚、灰度执行 |

---

## 12. 总结

Aviator 的价值不在于替代整个决策引擎，而是增强“条件判断和动态计算”这一层能力。对于当前项目，比较稳妥的方式是：

1. 继续保留现有结构化规则模型，确保业务配置友好。
2. 在底层增加 Aviator 表达式执行能力，承接复杂条件和函数扩展。
3. 通过变量库、函数白名单、发布校验、执行日志来保证可治理。
4. 引入统一的 Rule / Condition / Action JSON 协议，让前端只负责配置结构，后端负责生成 Aviator 表达式。
5. 与 Hare 数据网关结合时，先完成外部数据取数和变量标准化，再交给 Aviator 执行判断。
6. 先在规则测试和少量高级规则中试点，再逐步扩展到规则集和决策流条件节点。

这样可以在不破坏当前决策引擎架构的前提下，提升规则表达能力，并为后续风控、营销、审批和低代码动态计算场景打基础。
