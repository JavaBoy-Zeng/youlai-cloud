package com.youlai.decision.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.DecisionArtifactMapper;
import com.youlai.decision.mapper.DecisionExecuteLogMapper;
import com.youlai.decision.model.*;
import com.youlai.decision.model.ApiModels.*;
import com.youlai.decision.service.JsonService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class DecisionEngineService {

    private static final List<String> RESULT_PRIORITY = List.of("PASS", "OBSERVE", "REVIEW", "REJECT");
    private static final List<String> RISK_PRIORITY = List.of("LOW", "MEDIUM", "HIGH");

    private final DecisionArtifactMapper artifactMapper;
    private final DecisionExecuteLogMapper executeLogMapper;
    private final JsonService jsonService;
    private final ConditionEvaluator conditionEvaluator;
    private final StrategyAssetExecutor strategyAssetExecutor;
    private final ExternalNodeExecutor externalNodeExecutor;

    /**
     * 创建决策执行服务。
     *
     * @param artifactMapper 策略资产 Mapper
     * @param executeLogMapper 执行日志 Mapper
     * @param jsonService JSON 服务
     * @param conditionEvaluator 条件解释器
     */
    public DecisionEngineService(
            DecisionArtifactMapper artifactMapper,
            DecisionExecuteLogMapper executeLogMapper,
            JsonService jsonService,
            ConditionEvaluator conditionEvaluator,
            StrategyAssetExecutor strategyAssetExecutor,
            ExternalNodeExecutor externalNodeExecutor
    ) {
        this.artifactMapper = artifactMapper;
        this.executeLogMapper = executeLogMapper;
        this.jsonService = jsonService;
        this.conditionEvaluator = conditionEvaluator;
        this.strategyAssetExecutor = strategyAssetExecutor;
        this.externalNodeExecutor = externalNodeExecutor;
    }

    /**
     * 执行实时决策，并将请求、结果、路径和命中规则写入执行日志。
     *
     * @param request 决策请求
     * @return 决策结果
     */
    public DecisionResponse execute(ExecuteDecisionRequest request) {
        Instant started = Instant.now();
        String traceId = "TRACE" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        try {
            DecisionArtifact scene = Optional.ofNullable(findByKindAndCode(ArtifactKind.SCENE, request.sceneCode()))
                    .orElseThrow(() -> new NoSuchElementException("场景不存在: " + request.sceneCode()));
            ensureRunnable(scene);
            DecisionContext context = new DecisionContext(traceId, request, started);
            context.path.add("SCENE:" + scene.getCode());

            Optional<DecisionArtifact> flow = findFlowForScene(request.sceneCode());
            if (flow.isPresent()) {
                executeFlow(flow.get(), context);
            } else {
                executeRuleSetsOrRules(request.sceneCode(), context);
            }

            DecisionResponse response = context.toResponse(Duration.between(started, Instant.now()).toMillis());
            saveLog(request, response, true, null);
            return response;
        } catch (RuntimeException ex) {
            DecisionResponse response = new DecisionResponse(
                    traceId,
                    request.eventId(),
                    request.sceneCode(),
                    "ERROR",
                    "HIGH",
                    0,
                    List.of(),
                    List.of(),
                    List.of("ERROR:" + ex.getMessage()),
                    Map.of("error", ex.getMessage()),
                    Duration.between(started, Instant.now()).toMillis()
            );
            saveLog(request, response, false, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 使用输入样例测试单条规则。
     *
     * @param request 规则测试请求
     * @return 测试结果
     */
    public DecisionResponse testRule(RuleTestRequest request) {
        DecisionArtifact rule = Optional.ofNullable(findByKindAndCode(ArtifactKind.RULE, request.ruleCode()))
                .orElseThrow(() -> new NoSuchElementException("规则不存在: " + request.ruleCode()));
        ExecuteDecisionRequest executeRequest = new ExecuteDecisionRequest(
                Objects.toString(jsonService.readMap(rule.getContentJson()).getOrDefault("sceneCode", "single_rule_test")),
                "TEST-" + System.currentTimeMillis(),
                "tester",
                request.params() == null ? Map.of() : request.params()
        );
        DecisionContext context = new DecisionContext("TEST" + System.currentTimeMillis(), executeRequest, Instant.now());
        evaluateRule(rule, context);
        return context.toResponse(0L);
    }

    /**
     * 使用输入样例测试指定决策流。
     *
     * @param request 决策流测试请求
     * @return 测试结果
     */
    public DecisionResponse testFlow(FlowTestRequest request) {
        DecisionArtifact flow = Optional.ofNullable(findByKindAndCode(ArtifactKind.FLOW, request.flowCode()))
                .orElseThrow(() -> new NoSuchElementException("决策流不存在: " + request.flowCode()));
        Map<String, Object> content = jsonService.readMap(flow.getContentJson());
        String sceneCode = Objects.toString(content.getOrDefault("sceneCode", request.flowCode()));
        DecisionContext context = new DecisionContext(
                "FLOWTEST" + System.currentTimeMillis(),
                new ExecuteDecisionRequest(sceneCode, request.eventId(), request.userId(), request.params()),
                Instant.now()
        );
        executeFlow(flow, context);
        return context.toResponse(0L);
    }

    /**
     * 查询绑定指定场景的已发布决策流。
     *
     * @param sceneCode 场景编码
     * @return 决策流资产
     */
    private Optional<DecisionArtifact> findFlowForScene(String sceneCode) {
        return findByKind(ArtifactKind.FLOW).stream()
                .filter(this::isRunnable)
                .filter(flow -> sceneCode.equals(Objects.toString(jsonService.readMap(flow.getContentJson()).get("sceneCode"), "")))
                .findFirst();
    }

    /**
     * 在没有决策流时按场景执行规则集或规则。
     *
     * @param sceneCode 场景编码
     * @param context 决策上下文
     */
    private void executeRuleSetsOrRules(String sceneCode, DecisionContext context) {
        List<DecisionArtifact> ruleSets = findByKind(ArtifactKind.RULE_SET).stream()
                .filter(this::isRunnable)
                .filter(item -> sceneCode.equals(Objects.toString(jsonService.readMap(item.getContentJson()).get("sceneCode"), "")))
                .toList();
        if (!ruleSets.isEmpty()) {
            ruleSets.forEach(ruleSet -> executeRuleSet(ruleSet, context));
            return;
        }
        runnableRulesForScene(sceneCode).forEach(rule -> evaluateRule(rule, context));
    }

    /**
     * 执行规则集中的规则，并根据配置处理短路策略。
     *
     * @param ruleSet 规则集资产
     * @param context 决策上下文
     */
    @SuppressWarnings("unchecked")
    private void executeRuleSet(DecisionArtifact ruleSet, DecisionContext context) {
        Map<String, Object> content = jsonService.readMap(ruleSet.getContentJson());
        context.path.add("RULE_SET:" + ruleSet.getCode());
        String strategy = Objects.toString(content.getOrDefault("strategy", "ANY")).toUpperCase(Locale.ROOT);
        boolean shortCircuit = Boolean.TRUE.equals(content.get("shortCircuit"));
        List<String> ruleCodes = content.get("ruleCodes") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : runnableRulesForScene(context.request.sceneCode()).stream().map(DecisionArtifact::getCode).toList();
        int hitBefore = context.hitRules.size();
        for (String ruleCode : ruleCodes) {
            DecisionArtifact rule = findByKindAndCode(ArtifactKind.RULE, ruleCode);
            if (rule == null || !isRunnable(rule)) {
                continue;
            }
            boolean hit = evaluateRule(rule, context);
            if (shortCircuit && hit) {
                context.path.add("SHORT_CIRCUIT:" + ruleCode);
                break;
            }
        }
        if ("ALL".equals(strategy) && context.hitRules.size() - hitBefore < ruleCodes.size()) {
            context.outputs.put("ruleSetStrategy", "ALL_NOT_SATISFIED");
        }
    }

    /**
     * 执行决策流节点列表。
     *
     * @param flow 决策流资产
     * @param context 决策上下文
     */
    @SuppressWarnings("unchecked")
    private void executeFlow(DecisionArtifact flow, DecisionContext context) {
        ensureRunnable(flow);
        Map<String, Object> content = jsonService.readMap(flow.getContentJson());
        context.path.add("FLOW:" + flow.getCode());
        List<Map<String, Object>> nodes = content.get("nodes") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        if (nodes.isEmpty()) {
            executeRuleSetsOrRules(context.request.sceneCode(), context);
            return;
        }
        List<Map<String, Object>> edges = content.get("edges") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();
        if (edges.isEmpty()) {
            nodes.stream()
                    .sorted(Comparator.comparingInt(node -> intValue(node.getOrDefault("sort", 0))))
                    .forEach(node -> executeNode(node, context));
            return;
        }
        executeFlowGraph(nodes, edges, context);
    }

    /**
     * 按决策流连线执行节点，支持条件节点 true/false 分支。
     *
     * @param nodes 节点列表
     * @param edges 连线列表
     * @param context 决策上下文
     */
    private void executeFlowGraph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, DecisionContext context) {
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        nodes.forEach(node -> nodeMap.put(nodeId(node), node));
        Map<String, List<Map<String, Object>>> outgoing = new LinkedHashMap<>();
        for (Map<String, Object> edge : edges) {
            outgoing.computeIfAbsent(Objects.toString(edge.get("source"), ""), key -> new ArrayList<>()).add(edge);
        }
        String startId = nodes.stream()
                .filter(node -> "START".equalsIgnoreCase(Objects.toString(node.get("type"), "")))
                .findFirst()
                .map(this::nodeId)
                .orElse(nodeId(nodes.get(0)));
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> visits = new HashMap<>();
        queue.add(startId);
        int guard = Math.max(nodes.size() * 4, 16);
        while (!queue.isEmpty() && guard-- > 0) {
            String currentId = queue.removeFirst();
            Map<String, Object> node = nodeMap.get(currentId);
            if (node == null || visits.merge(currentId, 1, Integer::sum) > 3) {
                continue;
            }
            NodeOutcome outcome = executeNode(node, context);
            if (outcome.end()) {
                continue;
            }
            for (Map<String, Object> edge : nextEdges(outgoing.getOrDefault(currentId, List.of()), outcome)) {
                queue.addLast(Objects.toString(edge.get("target"), ""));
            }
        }
        if (guard <= 0) {
            context.path.add("FLOW_GUARD_STOP");
        }
    }

    /**
     * 执行单个决策流节点。
     *
     * @param node 节点配置
     * @param context 决策上下文
     */
    private NodeOutcome executeNode(Map<String, Object> node, DecisionContext context) {
        String type = Objects.toString(node.getOrDefault("type", "RULE")).toUpperCase(Locale.ROOT);
        String code = Objects.toString(node.getOrDefault("code", ""), "");
        if (Boolean.FALSE.equals(node.get("enabled"))) {
            context.path.add("SKIP_NODE:" + code);
            return new NodeOutcome(null, false);
        }
        switch (type) {
            case "START" -> context.path.add("START:" + code);
            case "RULE" -> Optional.ofNullable(findByKindAndCode(ArtifactKind.RULE, code))
                    .ifPresent(rule -> evaluateRule(rule, context));
            case "RULE_SET" -> Optional.ofNullable(findByKindAndCode(ArtifactKind.RULE_SET, code))
                    .ifPresent(ruleSet -> executeRuleSet(ruleSet, context));
            case "CONDITION" -> {
                boolean matched = conditionEvaluator.matches(conditionMap(node.get("conditions")), context.facts);
                context.path.add("CONDITION:" + code + "=" + matched);
                return new NodeOutcome(matched, false);
            }
            case "ACTION" -> applyActions(castMap(node.get("actions")), code, code, context);
            case "REVIEW" -> applyActions(castMap(node.get("actions")), code, code, context);
            case "SCORE_CARD" -> Optional.ofNullable(findByKindAndCode(ArtifactKind.SCORE_CARD, code))
                    .flatMap(scoreCard -> strategyAssetExecutor.executeScoreCard(scoreCard, context.facts))
                    .ifPresent(execution -> applyActions(execution.actions(), execution.code(), execution.name(), context));
            case "DECISION_TABLE" -> Optional.ofNullable(findByKindAndCode(ArtifactKind.DECISION_TABLE, code))
                    .ifPresent(table -> strategyAssetExecutor.executeDecisionTable(table, context.facts)
                            .forEach(execution -> applyActions(execution.actions(), execution.code(), execution.name(), context)));
            case "MODEL" -> Optional.ofNullable(findByKindAndCode(ArtifactKind.MODEL, code))
                    .ifPresent(model -> applyExternalOutput(
                            type,
                            code,
                            externalNodeExecutor.executeModel(model, context.facts, node),
                            context,
                            true
                    ));
            case "DATA" -> Optional.ofNullable(findByKindAndCode(ArtifactKind.DATA_SOURCE, code))
                    .ifPresent(dataSource -> applyExternalOutput(
                            type,
                            code,
                            externalNodeExecutor.executeDataSource(dataSource, context.facts, node),
                            context,
                            false
                    ));
            case "CALCULATE" -> {
                Map<String, Object> outputs = castMap(node.getOrDefault("outputs", node.getOrDefault("mockOutput", Map.of())));
                context.outputs.put(code, outputs);
                context.facts.put(code, outputs);
                context.facts.putAll(outputs);
                context.path.add(type + ":" + code);
            }
            case "END" -> {
                context.path.add("END:" + code);
                return new NodeOutcome(null, true);
            }
            default -> context.path.add("UNKNOWN_NODE:" + type + ":" + code);
        }
        return new NodeOutcome(null, false);
    }

    /**
     * 查询某个场景下可运行的规则并按优先级排序。
     *
     * @param sceneCode 场景编码
     * @return 规则列表
     */
    private List<DecisionArtifact> runnableRulesForScene(String sceneCode) {
        return findByKind(ArtifactKind.RULE).stream()
                .filter(this::isRunnable)
                .filter(rule -> sceneCode.equals(Objects.toString(jsonService.readMap(rule.getContentJson()).get("sceneCode"), "")))
                .sorted(Comparator.comparingInt(rule -> -intValue(jsonService.readMap(rule.getContentJson()).getOrDefault("priority", 0))))
                .toList();
    }

    /**
     * 判断规则是否命中并在命中时应用动作。
     *
     * @param rule 规则资产
     * @param context 决策上下文
     * @return 是否命中
     */
    private boolean evaluateRule(DecisionArtifact rule, DecisionContext context) {
        Map<String, Object> content = jsonService.readMap(rule.getContentJson());
        Map<String, Object> conditions = conditionMap(content.get("conditions"));
        boolean hit = conditionEvaluator.matches(conditions, context.facts);
        context.path.add("RULE:" + rule.getCode() + "=" + hit);
        if (hit) {
            applyActions(castMap(content.get("actions")), rule.getCode(), rule.getName(), context);
        }
        return hit;
    }

    /**
     * 将规则或动作节点的动作配置合并到决策上下文。
     *
     * @param actions 动作配置
     * @param code 命中对象编码
     * @param name 命中对象名称
     * @param context 决策上下文
     */
    @SuppressWarnings("unchecked")
    private void applyActions(Map<String, Object> actions, String code, String name, DecisionContext context) {
        String decisionResult = Objects.toString(actions.getOrDefault("decisionResult", "REVIEW")).toUpperCase(Locale.ROOT);
        String riskLevel = Objects.toString(actions.getOrDefault("riskLevel", "MEDIUM")).toUpperCase(Locale.ROOT);
        int score = intValue(actions.getOrDefault("score", 0));
        List<String> tags = actions.get("tags") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        String reason = Objects.toString(actions.getOrDefault("reason", name), name);

        context.decisionResult = higher(decisionResult, context.decisionResult, RESULT_PRIORITY);
        context.riskLevel = higher(riskLevel, context.riskLevel, RISK_PRIORITY);
        context.score = Math.min(100, context.score + Math.max(score, 0));
        context.tags.addAll(tags);
        context.hitRules.add(new HitRule(code, name, reason, score, decisionResult, riskLevel, tags));
        if (actions.get("outputs") instanceof Map<?, ?> outputs) {
            outputs.forEach((key, value) -> context.outputs.put(String.valueOf(key), value));
        }
    }

    /**
     * 按优先级列表选择更高优先级的枚举值。
     *
     * @param candidate 候选值
     * @param current 当前值
     * @param priority 优先级列表
     * @return 更高优先级值
     */
    private String higher(String candidate, String current, List<String> priority) {
        return priority.indexOf(candidate) > priority.indexOf(current) ? candidate : current;
    }

    /**
     * 判断策略资产是否处于可执行状态。
     *
     * @param artifact 策略资产
     * @return 是否可执行
     */
    private boolean isRunnable(DecisionArtifact artifact) {
        return DecisionStatus.isRunnable(artifact.getStatus());
    }

    /**
     * 校验策略资产是否可执行。
     *
     * @param artifact 策略资产
     */
    private void ensureRunnable(DecisionArtifact artifact) {
        if (!isRunnable(artifact)) {
            throw new IllegalStateException("资产未发布或未启用: " + artifact.getCode());
        }
    }

    /**
     * 保存决策执行日志。
     *
     * @param request 原始请求
     * @param response 决策响应
     * @param success 是否成功
     * @param errorMessage 异常信息
     */
    private void saveLog(ExecuteDecisionRequest request, DecisionResponse response, boolean success, String errorMessage) {
        DecisionExecuteLog log = new DecisionExecuteLog();
        log.setTraceId(response.traceId());
        log.setEventId(request.eventId());
        log.setSceneCode(request.sceneCode());
        log.setDecisionResult(response.decisionResult());
        log.setRiskLevel(response.riskLevel());
        log.setScore(response.score());
        log.setRequestJson(jsonService.write(request));
        log.setResponseJson(jsonService.write(response));
        log.setHitRulesJson(jsonService.write(response.hitRules()));
        log.setPathJson(jsonService.write(response.path()));
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setElapsedMs(response.elapsedMs());
        executeLogMapper.insert(log);
    }

    private DecisionArtifact findByKindAndCode(ArtifactKind kind, String code) {
        return artifactMapper.selectOne(Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind)
                .eq(DecisionArtifact::getCode, code)
                .last("LIMIT 1"));
    }

    private List<DecisionArtifact> findByKind(ArtifactKind kind) {
        return artifactMapper.selectList(Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind)
                .orderByDesc(DecisionArtifact::getUpdateTime)
                .orderByDesc(DecisionArtifact::getId));
    }

    /**
     * 安全地将对象转换为 Map。
     *
     * @param value 原始对象
     * @return Map 结构
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * 将前端可能传入的条件数组转换为 AND 条件组。
     *
     * @param value 条件对象或条件数组
     * @return 条件 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> conditionMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof List<?> list) {
            return Map.of("logic", "AND", "items", list);
        }
        return Map.of();
    }

    /**
     * 安全地将对象转换为整数。
     *
     * @param value 原始对象
     * @return 整数值
     */
    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void applyExternalOutput(
            String type,
            String code,
            ExternalNodeExecutor.ExternalNodeResult result,
            DecisionContext context,
            boolean applyDecisionActions
    ) {
        context.outputs.put(code, result.outputs());
        context.outputs.put(code + "Meta", Map.of("fallback", result.fallback(), "source", result.source()));
        context.facts.put(code, result.outputs());
        context.facts.putAll(result.outputs());
        context.path.add(type + ":" + code + (result.fallback() ? ":FALLBACK" : ""));
        if (applyDecisionActions && containsDecisionAction(result.outputs())) {
            applyActions(result.outputs(), code, code, context);
        }
    }

    private boolean containsDecisionAction(Map<String, Object> outputs) {
        return outputs.containsKey("decisionResult")
                || outputs.containsKey("riskLevel")
                || outputs.containsKey("tags");
    }

    private List<Map<String, Object>> nextEdges(List<Map<String, Object>> edges, NodeOutcome outcome) {
        if (outcome.conditionMatched() == null) {
            return edges;
        }
        String expected = outcome.conditionMatched() ? "true" : "false";
        List<Map<String, Object>> matched = edges.stream()
                .filter(edge -> expected.equalsIgnoreCase(Objects.toString(edge.getOrDefault("branch", ""), "")))
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return edges.stream()
                .filter(edge -> !Set.of("true", "false").contains(Objects.toString(edge.getOrDefault("branch", ""), "").toLowerCase(Locale.ROOT)))
                .toList();
    }

    private String nodeId(Map<String, Object> node) {
        return Objects.toString(node.getOrDefault("id", node.getOrDefault("code", "")), "");
    }

    private record NodeOutcome(Boolean conditionMatched, boolean end) {
    }

    private final class DecisionContext {
        private final String traceId;
        private final ExecuteDecisionRequest request;
        private final Instant started;
        private final Map<String, Object> facts = new LinkedHashMap<>();
        private final List<HitRule> hitRules = new ArrayList<>();
        private final List<String> path = new ArrayList<>();
        private final Set<String> tags = new LinkedHashSet<>();
        private final Map<String, Object> outputs = new LinkedHashMap<>();
        private String decisionResult = "PASS";
        private String riskLevel = "LOW";
        private int score = 0;

        /**
         * 创建单次决策上下文。
         *
         * @param traceId 追踪 ID
         * @param request 决策请求
         * @param started 开始时间
         */
        private DecisionContext(String traceId, ExecuteDecisionRequest request, Instant started) {
            this.traceId = traceId;
            this.request = request;
            this.started = started;
            if (request.params() != null) {
                this.facts.putAll(request.params());
            }
        }

        /**
         * 将上下文聚合为决策响应。
         *
         * @param elapsedMs 指定耗时
         * @return 决策响应
         */
        private DecisionResponse toResponse(Long elapsedMs) {
            return new DecisionResponse(
                    traceId,
                    request.eventId(),
                    request.sceneCode(),
                    decisionResult,
                    riskLevel,
                    score,
                    new ArrayList<>(tags),
                    hitRules,
                    path,
                    outputs,
                    elapsedMs == null ? Duration.between(started, Instant.now()).toMillis() : elapsedMs
            );
        }
    }
}
