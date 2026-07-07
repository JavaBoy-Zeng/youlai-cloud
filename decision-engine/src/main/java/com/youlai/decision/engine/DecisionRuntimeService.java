package com.youlai.decision.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.*;
import com.youlai.decision.model.*;
import com.youlai.decision.model.DomainApiModels.*;
import com.youlai.decision.service.JsonService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class DecisionRuntimeService {

    private static final List<String> RESULT_PRIORITY = List.of("PASS", "OBSERVE", "REVIEW", "REJECT");
    private static final List<String> RISK_PRIORITY = List.of("LOW", "MEDIUM", "HIGH");

    private final DecisionSceneMapper sceneMapper;
    private final DecisionRuleMapper ruleMapper;
    private final DecisionRuleSetMapper ruleSetMapper;
    private final DecisionFlowMapper flowMapper;
    private final DecisionFlowNodeMapper flowNodeMapper;
    private final DecisionFlowEdgeMapper flowEdgeMapper;
    private final DecisionScoreCardMapper scoreCardMapper;
    private final DecisionTableMapper tableMapper;
    private final DecisionExecuteLogMapper executeLogMapper;
    private final DecisionHitDetailLogMapper hitDetailLogMapper;
    private final JsonService jsonService;
    private final AviatorRuleStatementBuilder statementBuilder;
    private final AviatorExpressionEvaluator expressionEvaluator;
    private final VariableResolver variableResolver;

    public DecisionRuntimeService(
            DecisionSceneMapper sceneMapper,
            DecisionRuleMapper ruleMapper,
            DecisionRuleSetMapper ruleSetMapper,
            DecisionFlowMapper flowMapper,
            DecisionFlowNodeMapper flowNodeMapper,
            DecisionFlowEdgeMapper flowEdgeMapper,
            DecisionScoreCardMapper scoreCardMapper,
            DecisionTableMapper tableMapper,
            DecisionExecuteLogMapper executeLogMapper,
            DecisionHitDetailLogMapper hitDetailLogMapper,
            JsonService jsonService,
            AviatorRuleStatementBuilder statementBuilder,
            AviatorExpressionEvaluator expressionEvaluator,
            VariableResolver variableResolver
    ) {
        this.sceneMapper = sceneMapper;
        this.ruleMapper = ruleMapper;
        this.ruleSetMapper = ruleSetMapper;
        this.flowMapper = flowMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.flowEdgeMapper = flowEdgeMapper;
        this.scoreCardMapper = scoreCardMapper;
        this.tableMapper = tableMapper;
        this.executeLogMapper = executeLogMapper;
        this.hitDetailLogMapper = hitDetailLogMapper;
        this.jsonService = jsonService;
        this.statementBuilder = statementBuilder;
        this.expressionEvaluator = expressionEvaluator;
        this.variableResolver = variableResolver;
    }

    public DecisionResponse execute(ExecuteDecisionRequest request) {
        Instant started = Instant.now();
        String traceId = "TRACE" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        DecisionContext context = new DecisionContext(traceId, request, started);
        try {
            DecisionScene scene = requireScene(request.sceneCode());
            ensureRunnable(scene.getStatus(), scene.getCode());
            context.path.add("SCENE:" + scene.getCode());
            Optional<DecisionFlow> flow = flowForScene(scene.getCode());
            if (flow.isPresent()) {
                executeFlow(flow.get(), context);
            } else {
                executeRuleSetsOrRules(scene.getCode(), context);
            }
            DecisionResponse response = context.toResponse(Duration.between(started, Instant.now()).toMillis());
            saveLog(request, response, true, null);
            return response;
        } catch (RuntimeException ex) {
            DecisionResponse response = new DecisionResponse(traceId, request.eventId(), request.sceneCode(), "ERROR", "HIGH", 0,
                    List.of(), List.of(), List.of("ERROR:" + ex.getMessage()), Map.of("error", ex.getMessage()), List.of(),
                    Duration.between(started, Instant.now()).toMillis());
            saveLog(request, response, false, ex.getMessage());
            throw ex;
        }
    }

    public DecisionResponse testRule(RuleTestRequest request) {
        DecisionRule rule = requireRule(request.ruleCode());
        DecisionContext context = new DecisionContext(
                "TEST" + System.currentTimeMillis(),
                new ExecuteDecisionRequest(rule.getSceneCode(), "TEST-" + System.currentTimeMillis(), "tester", request.params()),
                Instant.now()
        );
        evaluateRule(rule, context, true);
        return context.toResponse(0L);
    }

    public DecisionResponse testFlow(FlowTestRequest request) {
        DecisionFlow flow = requireFlow(request.flowCode());
        DecisionContext context = new DecisionContext(
                "FLOWTEST" + System.currentTimeMillis(),
                new ExecuteDecisionRequest(flow.getSceneCode(), request.eventId(), request.userId(), request.params()),
                Instant.now()
        );
        executeFlow(flow, context);
        return context.toResponse(0L);
    }

    private void executeRuleSetsOrRules(String sceneCode, DecisionContext context) {
        List<DecisionRuleSet> ruleSets = ruleSetMapper.selectList(Wrappers.lambdaQuery(DecisionRuleSet.class)
                .eq(DecisionRuleSet::getSceneCode, sceneCode)
                .orderByDesc(DecisionRuleSet::getUpdateTime));
        List<DecisionRuleSet> runnableRuleSets = ruleSets.stream().filter(item -> DecisionStatus.isRunnable(item.getStatus())).toList();
        if (!runnableRuleSets.isEmpty()) {
            runnableRuleSets.forEach(ruleSet -> executeRuleSet(ruleSet, context));
            return;
        }
        runnableRules(sceneCode).forEach(rule -> evaluateRule(rule, context, true));
    }

    private boolean executeRuleSet(DecisionRuleSet ruleSet, DecisionContext context) {
        context.path.add("RULE_SET:" + ruleSet.getCode());
        List<String> ruleCodes = jsonService.readStringList(ruleSet.getRuleCodesJson());
        List<RuleHit> hits = new ArrayList<>();
        List<RuleHit> misses = new ArrayList<>();
        for (String ruleCode : ruleCodes) {
            DecisionRule rule = findRule(ruleCode).orElse(null);
            if (rule == null || !DecisionStatus.isRunnable(rule.getStatus())) {
                continue;
            }
            RuleHit hit = evaluateRule(rule, context, false);
            if (hit.matched()) {
                hits.add(hit);
                if (Boolean.TRUE.equals(ruleSet.getShortCircuit()) && "ANY".equalsIgnoreCase(ruleSet.getStrategy())) {
                    break;
                }
            } else {
                misses.add(hit);
            }
        }
        String strategy = Objects.toString(ruleSet.getStrategy(), "ANY").toUpperCase(Locale.ROOT);
        boolean passed = switch (strategy) {
            case "ALL" -> !ruleCodes.isEmpty() && hits.size() == ruleCodes.size();
            case "AT_LEAST" -> hits.size() >= Math.max(ruleSet.getRequiredMatch() == null ? 0 : ruleSet.getRequiredMatch(), 1);
            default -> !hits.isEmpty();
        };
        if (passed) {
            hits.forEach(hit -> applyActions(hit.actions(), hit.rule().getCode(), hit.rule().getName(), context));
        } else {
            context.outputs.put(ruleSet.getCode() + "Strategy", strategy + "_NOT_SATISFIED");
        }
        misses.forEach(hit -> applyFallback(hit.rule(), context));
        return passed;
    }

    private void executeFlow(DecisionFlow flow, DecisionContext context) {
        ensureRunnable(flow.getStatus(), flow.getCode());
        context.path.add("FLOW:" + flow.getCode());
        List<DecisionFlowNode> nodes = flowNodeMapper.selectList(Wrappers.lambdaQuery(DecisionFlowNode.class)
                .eq(DecisionFlowNode::getFlowId, flow.getId())
                .orderByAsc(DecisionFlowNode::getSort, DecisionFlowNode::getId));
        List<DecisionFlowEdge> edges = flowEdgeMapper.selectList(Wrappers.lambdaQuery(DecisionFlowEdge.class)
                .eq(DecisionFlowEdge::getFlowId, flow.getId()));
        if (edges.isEmpty()) {
            nodes.forEach(node -> executeNode(node, context));
            return;
        }
        executeGraph(nodes, edges, context);
    }

    private void executeGraph(List<DecisionFlowNode> nodes, List<DecisionFlowEdge> edges, DecisionContext context) {
        Map<String, DecisionFlowNode> nodeMap = new LinkedHashMap<>();
        nodes.forEach(node -> nodeMap.put(node.getNodeKey(), node));
        Map<String, List<DecisionFlowEdge>> outgoing = new LinkedHashMap<>();
        edges.forEach(edge -> outgoing.computeIfAbsent(edge.getSourceKey(), key -> new ArrayList<>()).add(edge));
        String start = nodes.stream().filter(node -> "START".equalsIgnoreCase(node.getType())).findFirst().map(DecisionFlowNode::getNodeKey).orElse(nodes.get(0).getNodeKey());
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        int guard = Math.max(16, nodes.size() * 4);
        while (!queue.isEmpty() && guard-- > 0) {
            DecisionFlowNode node = nodeMap.get(queue.removeFirst());
            if (node == null) {
                continue;
            }
            NodeOutcome outcome = executeNode(node, context);
            if (outcome.end()) {
                continue;
            }
            nextEdges(outgoing.getOrDefault(node.getNodeKey(), List.of()), outcome).forEach(edge -> queue.add(edge.getTargetKey()));
        }
    }

    private NodeOutcome executeNode(DecisionFlowNode node, DecisionContext context) {
        if (Boolean.FALSE.equals(node.getEnabled())) {
            context.path.add("SKIP_NODE:" + node.getCode());
            return new NodeOutcome(null, false);
        }
        Map<String, Object> config = jsonService.readMap(node.getConfigJson());
        String type = Objects.toString(node.getType(), "RULE").toUpperCase(Locale.ROOT);
        switch (type) {
            case "START" -> context.path.add("START:" + node.getCode());
            case "RULE" -> findRule(node.getCode()).ifPresent(rule -> evaluateRule(rule, context, true));
            case "RULE_SET" -> findRuleSet(node.getCode()).ifPresent(ruleSet -> executeRuleSet(ruleSet, context));
            case "CONDITION" -> {
                String expr = statementBuilder.build("MIXED", Objects.toString(config.get("conditionExpression"), ""), castMap(config.get("conditions")));
                VariableResolver.ResolveResult resolved = variableResolver.resolve(context.traceId, context.request.sceneCode(), context.request.params(), expr);
                AviatorExpressionEvaluator.EvaluationResult result = expressionEvaluator.evaluate("FLOW_NODE", node.getCode(), resolved.expression(), resolved.facts());
                context.conditionTraces.add(result.trace());
                saveConditionTrace(context, result.trace());
                context.path.add("CONDITION:" + node.getCode() + "=" + result.matched());
                return new NodeOutcome(result.matched(), false);
            }
            case "ACTION", "REVIEW" -> applyActions(castMap(config.get("actions")), node.getCode(), node.getLabel(), context);
            case "SCORE_CARD" -> executeScoreCard(node.getCode(), context);
            case "DECISION_TABLE" -> executeDecisionTable(node.getCode(), context);
            case "END" -> {
                context.path.add("END:" + node.getCode());
                return new NodeOutcome(null, true);
            }
            default -> context.path.add(type + ":" + node.getCode());
        }
        return new NodeOutcome(null, false);
    }

    private RuleHit evaluateRule(DecisionRule rule, DecisionContext context, boolean applyImmediately) {
        Map<String, Object> conditions = jsonService.readMap(rule.getConditionsJson());
        String expression = StringUtils.hasText(rule.getConditionExpression())
                ? rule.getConditionExpression()
                : statementBuilder.build(rule.getExpressionType(), rule.getConditionExpression(), conditions);
        VariableResolver.ResolveResult resolved = variableResolver.resolve(context.traceId, rule.getSceneCode(), context.request.params(), expression);
        AviatorExpressionEvaluator.EvaluationResult result = expressionEvaluator.evaluate("RULE", rule.getCode(), resolved.expression(), resolved.facts());
        context.conditionTraces.add(result.trace());
        saveConditionTrace(context, result.trace());
        context.path.add("RULE:" + rule.getCode() + "=" + result.matched());
        Map<String, Object> actions = jsonService.readMap(rule.getActionsJson());
        if (result.matched() && applyImmediately) {
            applyActions(actions, rule.getCode(), rule.getName(), context);
        } else if (!result.matched() && applyImmediately) {
            applyFallback(rule, context);
        }
        return new RuleHit(rule, result.matched(), actions);
    }

    private void applyFallback(DecisionRule rule, DecisionContext context) {
        Map<String, Object> fallback = jsonService.readMap(rule.getFallbackActionJson());
        if (!fallback.isEmpty()) {
            applyActions(fallback, rule.getCode() + "_fallback", rule.getName(), context);
        }
    }

    @SuppressWarnings("unchecked")
    private void executeScoreCard(String code, DecisionContext context) {
        DecisionScoreCard scoreCard = scoreCardMapper.selectOne(Wrappers.lambdaQuery(DecisionScoreCard.class).eq(DecisionScoreCard::getCode, code).last("LIMIT 1"));
        if (scoreCard == null) {
            return;
        }
        int score = 0;
        for (Map<String, Object> item : jsonService.readMapList(scoreCard.getItemsJson())) {
            Object rangesValue = item.get("ranges");
            if (!(rangesValue instanceof List<?> ranges)) {
                continue;
            }
            for (Object rangeValue : ranges) {
                if (!(rangeValue instanceof Map<?, ?> range)) {
                    continue;
                }
                Map<String, Object> rangeMap = castMap(range);
                Map<String, Object> condition = rangeMap.containsKey("condition")
                        ? castMap(rangeMap.get("condition"))
                        : Map.of("field", item.get("field"), "operator", rangeMap.getOrDefault("operator", "between"), "value", rangeMap.getOrDefault("between", rangeMap.get("value")));
                String expr = statementBuilder.buildCondition(condition);
                VariableResolver.ResolveResult resolved = variableResolver.resolve(context.traceId, context.request.sceneCode(), context.request.params(), expr);
                if (expressionEvaluator.evaluate("SCORE_CARD", scoreCard.getCode(), resolved.expression(), resolved.facts()).matched()) {
                    score += number(rangeMap.getOrDefault("score", 0));
                    break;
                }
            }
        }
        Map<String, Object> actions = Map.of("score", score, "decisionResult", score >= 80 ? "REVIEW" : "PASS", "riskLevel", score >= 80 ? "HIGH" : "LOW", "reason", scoreCard.getName());
        applyActions(actions, scoreCard.getCode(), scoreCard.getName(), context);
    }

    private void executeDecisionTable(String code, DecisionContext context) {
        DecisionTable table = tableMapper.selectOne(Wrappers.lambdaQuery(DecisionTable.class).eq(DecisionTable::getCode, code).last("LIMIT 1"));
        if (table == null) {
            return;
        }
        int rowIndex = 0;
        for (Map<String, Object> row : jsonService.readMapList(table.getRowsJson())) {
            rowIndex++;
            Map<String, Object> conditions = new LinkedHashMap<>();
            row.forEach((key, value) -> {
                if (!Set.of("decisionResult", "riskLevel", "score", "tags", "reason", "outputs").contains(key)) {
                    conditions.put(key, value);
                }
            });
            boolean matched = conditions.entrySet().stream().allMatch(entry -> cellMatches(entry.getKey(), entry.getValue(), context));
            if (matched) {
                Map<String, Object> actions = new LinkedHashMap<>(row);
                actions.keySet().removeAll(conditions.keySet());
                actions.putIfAbsent("reason", "命中决策表第" + rowIndex + "行");
                applyActions(actions, table.getCode() + "#" + rowIndex, table.getName(), context);
                if (!"COLLECT".equalsIgnoreCase(table.getHitPolicy())) {
                    break;
                }
            }
        }
    }

    private boolean cellMatches(String field, Object cell, DecisionContext context) {
        String text = String.valueOf(cell);
        if (!StringUtils.hasText(text) || "*".equals(text)) {
            return true;
        }
        String operator = "=";
        Object value = cell;
        for (String prefix : List.of(">=", "<=", "!=", ">", "<")) {
            if (text.startsWith(prefix)) {
                operator = prefix;
                value = text.substring(prefix.length()).trim();
                break;
            }
        }
        String expr = statementBuilder.buildCondition(Map.of("field", field, "operator", operator, "value", value));
        VariableResolver.ResolveResult resolved = variableResolver.resolve(context.traceId, context.request.sceneCode(), context.request.params(), expr);
        return expressionEvaluator.evaluate("DECISION_TABLE", field, resolved.expression(), resolved.facts()).matched();
    }

    @SuppressWarnings("unchecked")
    private void applyActions(Map<String, Object> actions, String code, String name, DecisionContext context) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        String decisionResult = Objects.toString(actions.getOrDefault("decisionResult", "REVIEW")).toUpperCase(Locale.ROOT);
        String riskLevel = Objects.toString(actions.getOrDefault("riskLevel", "MEDIUM")).toUpperCase(Locale.ROOT);
        int score = number(actions.getOrDefault("score", 0));
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

    private String higher(String candidate, String current, List<String> priority) {
        return priority.indexOf(candidate) > priority.indexOf(current) ? candidate : current;
    }

    private void saveConditionTrace(DecisionContext context, ConditionTrace trace) {
        DecisionHitDetailLog log = new DecisionHitDetailLog();
        log.setTraceId(context.traceId);
        log.setSceneCode(context.request.sceneCode());
        log.setTargetType(trace.targetType());
        log.setTargetCode(trace.targetCode());
        log.setDetailType("CONDITION");
        log.setExpression(trace.expression());
        log.setMatched(trace.matched());
        log.setDetailJson(jsonService.write(trace));
        log.setElapsedMs(trace.elapsedMs());
        hitDetailLogMapper.insert(log);
    }

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

    private DecisionScene requireScene(String sceneCode) {
        return Optional.ofNullable(sceneMapper.selectOne(Wrappers.lambdaQuery(DecisionScene.class)
                        .eq(DecisionScene::getCode, sceneCode).last("LIMIT 1")))
                .orElseThrow(() -> new NoSuchElementException("场景不存在: " + sceneCode));
    }

    private DecisionRule requireRule(String code) {
        return findRule(code).orElseThrow(() -> new NoSuchElementException("规则不存在: " + code));
    }

    private DecisionFlow requireFlow(String code) {
        return Optional.ofNullable(flowMapper.selectOne(Wrappers.lambdaQuery(DecisionFlow.class).eq(DecisionFlow::getCode, code).last("LIMIT 1")))
                .orElseThrow(() -> new NoSuchElementException("决策流不存在: " + code));
    }

    private Optional<DecisionRule> findRule(String code) {
        return Optional.ofNullable(ruleMapper.selectOne(Wrappers.lambdaQuery(DecisionRule.class).eq(DecisionRule::getCode, code).last("LIMIT 1")));
    }

    private Optional<DecisionRuleSet> findRuleSet(String code) {
        return Optional.ofNullable(ruleSetMapper.selectOne(Wrappers.lambdaQuery(DecisionRuleSet.class).eq(DecisionRuleSet::getCode, code).last("LIMIT 1")));
    }

    private Optional<DecisionFlow> flowForScene(String sceneCode) {
        return flowMapper.selectList(Wrappers.lambdaQuery(DecisionFlow.class).eq(DecisionFlow::getSceneCode, sceneCode).orderByDesc(DecisionFlow::getUpdateTime))
                .stream().filter(item -> DecisionStatus.isRunnable(item.getStatus())).findFirst();
    }

    private List<DecisionRule> runnableRules(String sceneCode) {
        return ruleMapper.selectList(Wrappers.lambdaQuery(DecisionRule.class)
                        .eq(DecisionRule::getSceneCode, sceneCode)
                        .orderByDesc(DecisionRule::getPriority))
                .stream().filter(item -> DecisionStatus.isRunnable(item.getStatus())).toList();
    }

    private void ensureRunnable(String status, String code) {
        if (!DecisionStatus.isRunnable(status)) {
            throw new IllegalStateException("资产未发布或未启用: " + code);
        }
    }

    private List<DecisionFlowEdge> nextEdges(List<DecisionFlowEdge> edges, NodeOutcome outcome) {
        if (outcome.conditionMatched() == null) {
            return edges;
        }
        String expected = outcome.conditionMatched() ? "true" : "false";
        List<DecisionFlowEdge> matched = edges.stream().filter(edge -> expected.equalsIgnoreCase(Objects.toString(edge.getBranch(), ""))).toList();
        return matched.isEmpty() ? edges.stream().filter(edge -> !StringUtils.hasText(edge.getBranch())).toList() : matched;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private record RuleHit(DecisionRule rule, boolean matched, Map<String, Object> actions) {
    }

    private record NodeOutcome(Boolean conditionMatched, boolean end) {
    }

    private final class DecisionContext {
        private final String traceId;
        private final ExecuteDecisionRequest request;
        private final Instant started;
        private final List<HitRule> hitRules = new ArrayList<>();
        private final List<String> path = new ArrayList<>();
        private final Set<String> tags = new LinkedHashSet<>();
        private final Map<String, Object> outputs = new LinkedHashMap<>();
        private final List<ConditionTrace> conditionTraces = new ArrayList<>();
        private String decisionResult = "PASS";
        private String riskLevel = "LOW";
        private int score = 0;

        private DecisionContext(String traceId, ExecuteDecisionRequest request, Instant started) {
            this.traceId = traceId;
            this.request = request;
            this.started = started;
        }

        private DecisionResponse toResponse(Long elapsedMs) {
            return new DecisionResponse(traceId, request.eventId(), request.sceneCode(), decisionResult, riskLevel, score,
                    new ArrayList<>(tags), hitRules, path, outputs, conditionTraces,
                    elapsedMs == null ? Duration.between(started, Instant.now()).toMillis() : elapsedMs);
        }
    }
}
