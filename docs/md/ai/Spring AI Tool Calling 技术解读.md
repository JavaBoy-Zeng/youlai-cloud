# Spring AI Tool Calling 技术解读

> 来源：Spring AI Reference - Tool Calling  
> 地址：https://docs.spring.io/spring-ai/reference/api/tools.html  
> 版本：Spring AI 2.0.0  
> 整理日期：2026-07-05

本文不是官方文档的逐字翻译，而是面向 Java / Spring Boot 技术人员的工程化解读，重点说明 Spring AI 中 function call，也就是 Tool Calling，应该如何理解、设计和落地。

## 1. Tool Calling 是什么

Tool Calling 也常被叫做 Function Calling。它的核心作用是让大模型在回答问题时，可以请求应用程序调用某个外部能力。

这些外部能力可以是：

- 查询数据库
- 调用第三方接口
- 查询文件系统
- 检索知识库
- 发送邮件
- 创建工单
- 触发业务流程

需要特别注意：**模型本身不会直接执行 Java 方法，也不会直接访问数据库或接口。**

真实流程是：

```text
用户提问
  -> 模型判断是否需要调用工具
  -> 模型返回工具名和参数
  -> Spring AI 执行 Java 方法
  -> Java 方法返回结果
  -> 结果再交给模型
  -> 模型生成最终回答
```

所以 Tool Calling 不是把系统权限交给模型，而是让模型在受控范围内请求应用程序执行动作。

## 2. 两类典型工具

Spring AI 官方文档把工具使用场景分成两类。

### 2.1 信息检索类

用于获取模型本身不知道的信息。

示例：

- 查询订单状态
- 查询库存
- 查询用户余额
- 查询当前时间
- 查询数据库记录
- 调用搜索接口

这类工具一般风险较低，但仍然需要做权限和租户隔离。

### 2.2 动作执行类

用于让系统执行某个业务动作。

示例：

- 创建订单
- 取消订单
- 发起退款
- 发送通知
- 创建审批单
- 修改用户资料

这类工具风险更高，不能简单暴露给模型直接调用。建议增加用户确认、权限校验、幂等控制和审计日志。

## 3. Spring AI 中的核心概念

### 3.1 ToolCallback

`ToolCallback` 是 Spring AI 对工具的核心抽象。

无论你使用注解方式还是编程方式，最终都会转成 ToolCallback，交给模型和工具执行链使用。

常见来源：

- `@Tool` 标注的 Java 方法
- `MethodToolCallback`
- `FunctionToolCallback`
- `ToolCallbackProvider`
- MCP Client 提供的远程工具

### 3.2 @Tool

`@Tool` 是最常用的声明式写法，用于把一个 Java 方法声明成模型可调用的工具。

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderAiTools {

    private final OrderService orderService;

    public OrderAiTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @Tool(
            name = "query_order_status",
            description = "Query the current user's order status by order number"
    )
    public AiToolResult<OrderStatusView> queryOrderStatus(
            @ToolParam(description = "Order number, not user id") String orderNo,
            ToolContext toolContext
    ) {
        Long userId = (Long) toolContext.getContext().get("userId");
        Long tenantId = (Long) toolContext.getContext().get("tenantId");

        OrderStatusView result = orderService.queryUserOrderStatus(
                tenantId,
                userId,
                orderNo
        );

        return AiToolResult.success(result);
    }
}
```

`@Tool` 的关键属性：

| 属性 | 作用 |
|---|---|
| `name` | 工具名称。模型通过这个名称请求调用工具 |
| `description` | 工具说明。模型依靠它判断什么时候使用工具 |
| `returnDirect` | 是否把工具结果直接返回给调用方，而不是再交给模型生成回答 |
| `resultConverter` | 工具返回结果转字符串时使用的转换器 |

工程建议：**一定要写清楚 description。** 工具描述太模糊时，模型可能不用工具，或者在错误场景下调用工具。

### 3.3 @ToolParam

`@ToolParam` 用于描述工具方法的参数。

```java
@Tool(description = "Query order by order number")
public AiToolResult<OrderView> queryOrder(
        @ToolParam(description = "Business order number, for example OD202607050001")
        String orderNo
) {
    return AiToolResult.success(orderService.queryByOrderNo(orderNo));
}
```

建议参数描述写清楚：

- 参数是什么
- 不是哪个相似概念
- 格式是什么
- 是否必填
- 示例值是什么

### 3.4 ToolContext

`ToolContext` 用来向工具方法传递运行时上下文。

典型内容：

- 当前用户 ID
- 租户 ID
- 角色
- traceId
- conversationId
- 请求来源

示例：

```java
String answer = chatClient.prompt()
        .user(command.message())
        .tools(orderAiTools)
        .toolContext(Map.of(
                "userId", command.userId(),
                "tenantId", command.tenantId(),
                "traceId", command.traceId()
        ))
        .call()
        .content();
```

ToolContext 的价值是：**不要让模型自己传用户身份、租户身份这类敏感参数。**

用户身份应该来自系统登录态，而不是来自用户自然语言输入。

### 3.5 ToolCallingAdvisor

Spring AI 2.0.0 中，推荐使用 `ChatClient` 的工具调用链。

`ChatClient` 默认通过 `ToolCallingAdvisor` 管理工具执行流程。

不建议直接依赖 `ChatModel` 自动执行工具，因为文档中说明该方式已经不推荐，并计划在后续版本移除。

工程建议：

```text
优先使用 ChatClient
不要把工具执行逻辑散落在 ChatModel 调用代码里
```

## 4. 推荐架构

不要在业务 Service 上直接加 `@Tool`。

推荐增加一层 AI Tool Facade。

```text
AiChatController
    -> AiChatService
        -> Tool Policy
        -> ChatClient
            -> AI Tool Facade
                -> Application Service
                    -> Repository / Remote API
```

对应包结构：

```text
com.youlai.ai
  ├── chat
  │   ├── AiChatController
  │   └── AiChatService
  ├── tool
  │   ├── order
  │   ├── user
  │   └── system
  ├── policy
  ├── audit
  └── config
```

这样做的好处：

- 业务 Service 不被 AI 框架污染
- Tool 入参和返回结果可以单独设计
- 可以统一审计
- 可以统一权限控制
- 后续可以平滑迁移到 MCP

## 5. 统一返回结构

不要让工具直接返回自然语言字符串。

建议统一返回结构化对象。

```java
public record AiToolResult<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> AiToolResult<T> success(T data) {
        return new AiToolResult<>(true, "OK", "success", data);
    }

    public static <T> AiToolResult<T> fail(String code, String message) {
        return new AiToolResult<>(false, code, message, null);
    }
}
```

工具返回结构化数据，有几个好处：

- 模型更容易理解结果
- 前端可以做稳定解析
- 日志审计更清楚
- 异常处理更标准
- 不容易把敏感内部信息泄露给用户

## 6. 运行时工具与默认工具

Spring AI 支持两种方式提供工具。

### 6.1 运行时工具

每次请求单独传入工具：

```java
String answer = chatClient.prompt()
        .system(systemPrompt)
        .user(userMessage)
        .tools(orderAiTools)
        .toolContext(toolContext)
        .call()
        .content();
```

这是企业应用更推荐的方式。

优点：

- 每次请求可以动态决定工具范围
- 容易按用户权限控制工具
- 不容易意外暴露高风险工具

### 6.2 默认工具

构建 `ChatClient` 时绑定默认工具：

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(systemTools)
        .build();
```

默认工具会被该 `ChatClient.Builder` 构建出来的客户端共享。

工程建议：

```text
只把低风险、通用、只读工具放入 defaultTools
高风险工具不要放入 defaultTools
业务场景工具优先使用运行时 tools
```

## 7. 企业级 Tool Policy 设计

建议给工具增加风险等级。

```java
public enum ToolRiskLevel {
    READ_ONLY,
    WRITE,
    HIGH_RISK
}
```

工具策略：

| 风险等级 | 示例 | 执行策略 |
|---|---|---|
| `READ_ONLY` | 查询订单、查询库存 | 可自动执行，但要鉴权 |
| `WRITE` | 创建工单、修改昵称 | 需要权限校验和审计 |
| `HIGH_RISK` | 退款、删除、发券、改权限 | 必须二次确认 |

高风险动作建议拆成两步：

```text
preview_refund
  -> 返回退款计划、金额、影响范围

execute_refund
  -> 用户确认后，使用确认 token 执行
```

不要让模型直接执行不可逆操作。

## 8. Tool 方法设计原则

### 8.1 工具要小而明确

不推荐：

```java
@Tool(description = "Handle order operations")
public Object handleOrder(String input) {
    // ...
}
```

推荐：

```java
@Tool(description = "Query order status by order number")
public AiToolResult<OrderStatusView> queryOrderStatus(String orderNo) {
    // ...
}

@Tool(description = "Query logistics tracking by order number")
public AiToolResult<LogisticsView> queryLogistics(String orderNo) {
    // ...
}
```

### 8.2 不要让模型传敏感身份参数

不推荐：

```java
public AiToolResult<OrderView> queryOrder(Long userId, String orderNo) {
    // userId 来自模型参数，风险高
}
```

推荐：

```java
public AiToolResult<OrderView> queryOrder(String orderNo, ToolContext context) {
    Long userId = (Long) context.getContext().get("userId");
    // userId 来自服务端上下文
}
```

### 8.3 Tool 层必须二次鉴权

即使前面做过工具选择，工具方法内部仍然要校验：

```java
Order order = orderService.queryUserOrder(
        tenantId,
        userId,
        orderNo
);
```

不要只按 `orderNo` 查询全局订单。

### 8.4 不要返回过多数据

工具返回给模型的数据应该最小化。

例如订单查询工具只返回：

- 订单号
- 状态
- 支付状态
- 发货状态
- 必要时间字段

不要返回：

- 完整地址
- 手机号
- 身份证号
- 内部成本
- 内部备注
- 数据库主键

## 9. 异常处理

Spring AI 提供 `ToolExecutionExceptionProcessor` 处理工具执行异常。

工程上建议：

- 业务异常转成标准错误码
- 系统异常不要直接暴露给模型
- 是否把错误交给模型处理，应按场景决定

示例：

```java
try {
    OrderStatusView result = orderService.queryUserOrderStatus(
            tenantId,
            userId,
            orderNo
    );
    return AiToolResult.success(result);
} catch (BizException ex) {
    return AiToolResult.fail(ex.getCode(), ex.getMessage());
} catch (Exception ex) {
    log.error("AI tool execution failed", ex);
    return AiToolResult.fail("TOOL_EXECUTION_ERROR", "Tool execution failed");
}
```

如果希望工具异常直接抛给调用方处理，可以配置：

```yaml
spring:
  ai:
    tools:
      throw-exception-on-error: true
```

如果希望异常转换成消息交给模型处理，可以保持默认策略，并确保错误消息不包含敏感信息。

## 10. 审计日志设计

建议建立工具调用审计表。

```sql
CREATE TABLE ai_tool_call_log (
    id               BIGINT PRIMARY KEY,
    conversation_id  VARCHAR(64),
    trace_id         VARCHAR(64),
    user_id          BIGINT,
    tenant_id        BIGINT,
    tool_name        VARCHAR(128),
    risk_level       VARCHAR(32),
    input_json       TEXT,
    output_json      TEXT,
    success          TINYINT,
    error_code       VARCHAR(64),
    duration_ms      BIGINT,
    created_at       DATETIME
);
```

至少记录：

- 谁调用
- 在哪个会话调用
- 调用了哪个工具
- 入参是什么
- 结果是什么
- 是否成功
- 耗时多久
- 是否高风险

这对排查越权、误调用、模型幻觉、用户投诉都很重要。

## 11. 可观测性

Spring AI Tool Calling 支持观测能力，会记录工具执行耗时并传播 tracing 信息。

工程建议：

- 工具调用纳入 trace
- 工具耗时纳入 metrics
- 慢工具需要告警
- 高风险工具需要单独审计
- 默认不要把工具参数和结果输出到 span 属性，除非已经做好脱敏

日志调试时，可以打开：

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

生产环境不要长期打开 DEBUG。

## 12. Tool Resolution

工具可以在调用时直接传入，也可以通过 `ToolCallbackResolver` 按名称动态解析。

适合使用动态解析的场景：

- 工具数量很多
- 工具来自多个模块
- 工具需要按租户或权限动态过滤
- 工具来自 MCP Server
- 工具需要插件化扩展

如果只是少量工具，直接 `.tools(orderAiTools)` 更简单。

## 13. Tool Search Tool

当工具数量很多时，把所有工具定义都塞给模型会有两个问题：

- token 消耗变大
- 模型选错工具的概率上升

Spring AI 提供 Tool Search Tool 模式。

它的思路是：

```text
一开始只给模型一个 toolSearchTool
模型先搜索需要的工具
系统把匹配工具加入上下文
模型再调用真正的工具
```

适合场景：

- 工具有 10 个以上
- 工具定义消耗超过 10K tokens
- 接入多个 MCP Server
- 工具名称相似，模型容易选错

不适合场景：

- 工具少于 10 个
- 每次会话几乎都会用到所有工具
- 工具描述非常短

## 14. 推荐落地方案

对企业后台系统，建议按下面方式落地。

```text
第一阶段：少量工具
  @Tool + ChatClient.tools(...)
  运行时动态注入工具
  ToolContext 传用户和租户
  统一 AiToolResult
  手动审计日志

第二阶段：工具平台化
  自定义 @AiFunction 元数据
  启动时扫描工具
  ToolRegistry 统一管理
  ToolPolicy 动态过滤
  ToolCallbackResolver 动态解析

第三阶段：跨系统复用
  把稳定工具沉淀成 MCP Server
  Spring AI 作为 MCP Client 使用远程工具
  Codex / 其他 Agent 也可以复用

第四阶段：大量工具
  引入 Tool Search Tool
  Lucene / Vector / Regex 索引工具
  按会话动态发现工具
```

## 15. 最小可用代码骨架

### 15.1 Chat Service

```java
@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final OrderAiTools orderAiTools;
    private final ToolPolicyService toolPolicyService;

    public AiChatService(ChatClient.Builder builder,
                         OrderAiTools orderAiTools,
                         ToolPolicyService toolPolicyService) {
        this.chatClient = builder.build();
        this.orderAiTools = orderAiTools;
        this.toolPolicyService = toolPolicyService;
    }

    public String chat(AiChatCommand command) {
        Object[] allowedTools = toolPolicyService.resolveTools(
                command,
                orderAiTools
        );

        return chatClient.prompt()
                .system("""
                        You are an enterprise assistant.
                        Use tools only when necessary.
                        Never guess business data.
                        If a tool returns an error, explain it clearly.
                        """)
                .user(command.message())
                .tools(allowedTools)
                .toolContext(Map.of(
                        "userId", command.userId(),
                        "tenantId", command.tenantId(),
                        "traceId", command.traceId()
                ))
                .call()
                .content();
    }
}
```

### 15.2 Tool Policy

```java
@Service
public class ToolPolicyService {

    public Object[] resolveTools(AiChatCommand command, Object... candidateTools) {
        if (command.userId() == null || command.tenantId() == null) {
            return new Object[0];
        }

        return candidateTools;
    }
}
```

真实项目中，`ToolPolicyService` 应该结合：

- 当前用户
- 当前租户
- 角色权限
- 功能开关
- 工具风险等级
- 业务场景

动态决定本轮对话允许哪些工具。

### 15.3 Tool Facade

```java
@Component
public class OrderAiTools {

    private final OrderAppService orderAppService;
    private final AiToolAuditService auditService;

    public OrderAiTools(OrderAppService orderAppService,
                        AiToolAuditService auditService) {
        this.orderAppService = orderAppService;
        this.auditService = auditService;
    }

    @Tool(
            name = "query_order_status",
            description = "Query the current user's order status by order number"
    )
    public AiToolResult<OrderStatusView> queryOrderStatus(
            @ToolParam(description = "Order number, not user id")
            String orderNo,
            ToolContext toolContext
    ) {
        Long userId = (Long) toolContext.getContext().get("userId");
        Long tenantId = (Long) toolContext.getContext().get("tenantId");
        String traceId = (String) toolContext.getContext().get("traceId");

        long start = System.currentTimeMillis();

        try {
            OrderStatusView result = orderAppService.queryUserOrderStatus(
                    tenantId,
                    userId,
                    orderNo
            );

            auditService.success(traceId, userId, tenantId, "query_order_status",
                    orderNo, System.currentTimeMillis() - start);

            return AiToolResult.success(result);
        } catch (BizException ex) {
            auditService.fail(traceId, userId, tenantId, "query_order_status",
                    orderNo, ex.getCode(), System.currentTimeMillis() - start);

            return AiToolResult.fail(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            auditService.fail(traceId, userId, tenantId, "query_order_status",
                    orderNo, "TOOL_EXECUTION_ERROR", System.currentTimeMillis() - start);

            return AiToolResult.fail("TOOL_EXECUTION_ERROR", "Tool execution failed");
        }
    }
}
```

## 16. 与 MCP 的边界

Tool Calling 和 MCP 不是同一个层面的东西。

| 能力 | 适合场景 |
|---|---|
| Spring AI `@Tool` | 当前 Spring Boot 应用内部调用 |
| `ToolCallbackProvider` | 从多个来源提供工具 |
| MCP Client | 调用外部 MCP Server 暴露的工具 |
| MCP Server | 把本系统能力开放给多个 AI 客户端 |

如果工具只给当前应用使用，`@Tool` 就够了。

如果订单系统、用户系统、权限系统的能力要给多个 AI 应用复用，建议抽成 MCP Server。

## 17. 实施检查清单

上线前至少检查：

- 是否只暴露了必要工具
- 是否避免了全局 defaultTools 暴露高风险工具
- 是否使用 ToolContext 传用户和租户
- Tool 方法内部是否做了二次鉴权
- 是否统一返回 `AiToolResult`
- 是否有审计日志
- 是否有 traceId
- 是否处理了业务异常
- 是否避免返回敏感字段
- 写操作是否有确认机制
- 高风险操作是否有幂等控制
- 工具描述是否足够清晰
- 参数描述是否足够清晰
- 是否有测试覆盖工具正常调用、参数缺失、越权、异常等场景

## 18. 一句话总结

Spring AI Tool Calling 的本质不是“让大模型调用 Java 方法”，而是“让大模型在受控范围内请求业务系统执行一个被明确描述、被权限约束、可审计、可观测的工具”。

企业级实现不要只关注 `@Tool` 注解本身，更应该关注工具边界、权限策略、审计日志、异常处理和高风险动作确认机制。
