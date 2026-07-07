package com.youlai.decision.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youlai.decision.engine.AviatorRuleStatementBuilder;
import com.youlai.decision.engine.FlowablePublishClient;
import com.youlai.decision.mapper.*;
import com.youlai.decision.model.*;
import com.youlai.decision.model.DomainApiModels.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class DecisionDomainService {

    private final DecisionSceneMapper sceneMapper;
    private final DecisionVariableMapper variableMapper;
    private final DecisionRuleMapper ruleMapper;
    private final DecisionRuleSetMapper ruleSetMapper;
    private final DecisionFlowMapper flowMapper;
    private final DecisionFlowNodeMapper flowNodeMapper;
    private final DecisionFlowEdgeMapper flowEdgeMapper;
    private final DecisionDataSourceMapper dataSourceMapper;
    private final DecisionModelConfigMapper modelConfigMapper;
    private final DecisionScoreCardMapper scoreCardMapper;
    private final DecisionTableMapper tableMapper;
    private final DecisionDomainVersionMapper versionMapper;
    private final DecisionDomainPublishRecordMapper publishRecordMapper;
    private final DecisionPublishRequestMapper publishRequestMapper;
    private final DecisionGrayPolicyMapper grayPolicyMapper;
    private final DecisionSimulationJobMapper simulationJobMapper;
    private final JsonService jsonService;
    private final ObjectMapper objectMapper;
    private final AviatorRuleStatementBuilder statementBuilder;
    private final FlowablePublishClient flowablePublishClient;
    private final DecisionEngineProperties properties;

    public DecisionDomainService(
            DecisionSceneMapper sceneMapper,
            DecisionVariableMapper variableMapper,
            DecisionRuleMapper ruleMapper,
            DecisionRuleSetMapper ruleSetMapper,
            DecisionFlowMapper flowMapper,
            DecisionFlowNodeMapper flowNodeMapper,
            DecisionFlowEdgeMapper flowEdgeMapper,
            DecisionDataSourceMapper dataSourceMapper,
            DecisionModelConfigMapper modelConfigMapper,
            DecisionScoreCardMapper scoreCardMapper,
            DecisionTableMapper tableMapper,
            DecisionDomainVersionMapper versionMapper,
            DecisionDomainPublishRecordMapper publishRecordMapper,
            DecisionPublishRequestMapper publishRequestMapper,
            DecisionGrayPolicyMapper grayPolicyMapper,
            DecisionSimulationJobMapper simulationJobMapper,
            JsonService jsonService,
            ObjectMapper objectMapper,
            AviatorRuleStatementBuilder statementBuilder,
            FlowablePublishClient flowablePublishClient,
            DecisionEngineProperties properties
    ) {
        this.sceneMapper = sceneMapper;
        this.variableMapper = variableMapper;
        this.ruleMapper = ruleMapper;
        this.ruleSetMapper = ruleSetMapper;
        this.flowMapper = flowMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.flowEdgeMapper = flowEdgeMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.scoreCardMapper = scoreCardMapper;
        this.tableMapper = tableMapper;
        this.versionMapper = versionMapper;
        this.publishRecordMapper = publishRecordMapper;
        this.publishRequestMapper = publishRequestMapper;
        this.grayPolicyMapper = grayPolicyMapper;
        this.simulationJobMapper = simulationJobMapper;
        this.jsonService = jsonService;
        this.objectMapper = objectMapper;
        this.statementBuilder = statementBuilder;
        this.flowablePublishClient = flowablePublishClient;
        this.properties = properties;
    }

    public List<DecisionScene> scenes() {
        return list(sceneMapper);
    }

    @Transactional
    public DecisionScene saveScene(Long id, SceneRequest request) {
        DecisionScene scene = id == null ? new DecisionScene() : require(sceneMapper, id, "场景不存在");
        scene.setCode(required(request.code(), "场景编码不能为空"));
        scene.setName(required(request.name(), "场景名称不能为空"));
        scene.setCategory(request.category());
        scene.setStatus(DecisionStatus.normalize(request.status()));
        scene.setInputSchemaJson(jsonService.write(request.inputs() == null ? List.of() : request.inputs()));
        scene.setOutputSchemaJson(jsonService.write(request.outputs() == null ? List.of() : request.outputs()));
        scene.setOwner(request.owner());
        scene.setRemark(request.remark());
        upsert(sceneMapper, scene, id);
        snapshot("SCENE", scene.getId(), scene.getCode(), 1, scene, "保存场景");
        return scene;
    }

    public List<DecisionVariable> variables(String sceneCode) {
        return variableMapper.selectList(Wrappers.lambdaQuery(DecisionVariable.class)
                .eq(StringUtils.hasText(sceneCode), DecisionVariable::getSceneCode, sceneCode)
                .orderByDesc(DecisionVariable::getUpdateTime));
    }

    @Transactional
    public DecisionVariable saveVariable(Long id, VariableRequest request) {
        DecisionVariable variable = id == null ? new DecisionVariable() : require(variableMapper, id, "变量不存在");
        variable.setSceneCode(request.sceneCode());
        variable.setCode(required(request.code(), "变量编码不能为空"));
        variable.setName(required(request.name(), "变量名称不能为空"));
        variable.setType(StringUtils.hasText(request.type()) ? request.type() : "STRING");
        variable.setSource(StringUtils.hasText(request.source()) ? request.source() : "REQUEST");
        variable.setSourceConfigJson(jsonService.write(request.sourceConfig() == null ? Map.of() : request.sourceConfig()));
        variable.setDefaultValueJson(request.defaultValue() == null ? null : jsonService.write(request.defaultValue()));
        variable.setStatus(DecisionStatus.normalize(request.status()));
        variable.setRemark(request.remark());
        upsert(variableMapper, variable, id);
        snapshot("VARIABLE", variable.getId(), variable.getCode(), 1, variable, "保存变量");
        return variable;
    }

    public List<DecisionRule> rules(String sceneCode) {
        return ruleMapper.selectList(Wrappers.lambdaQuery(DecisionRule.class)
                .eq(StringUtils.hasText(sceneCode), DecisionRule::getSceneCode, sceneCode)
                .orderByDesc(DecisionRule::getUpdateTime));
    }

    @Transactional
    public DecisionRule saveRule(Long id, RuleRequest request) {
        DecisionRule rule = id == null ? new DecisionRule() : require(ruleMapper, id, "规则不存在");
        rule.setSceneCode(required(request.sceneCode(), "规则必须绑定场景"));
        rule.setCode(required(request.code(), "规则编码不能为空"));
        rule.setName(required(request.name(), "规则名称不能为空"));
        rule.setPriority(request.priority() == null ? 0 : request.priority());
        rule.setExpressionType(StringUtils.hasText(request.expressionType()) ? request.expressionType() : "MIXED");
        rule.setMatchMode(StringUtils.hasText(request.matchMode()) ? request.matchMode() : "BOOLEAN");
        rule.setRequiredMatch(request.requiredMatch() == null ? 0 : request.requiredMatch());
        Map<String, Object> conditions = request.conditions() == null ? Map.of() : request.conditions();
        rule.setConditionExpression(statementBuilder.build(rule.getExpressionType(), request.conditionExpression(), conditions));
        rule.setConditionsJson(jsonService.write(conditions));
        rule.setActionsJson(jsonService.write(request.actions() == null ? Map.of("decisionResult", "REVIEW") : request.actions()));
        rule.setFallbackActionJson(jsonService.write(request.fallbackAction() == null ? Map.of() : request.fallbackAction()));
        rule.setStatus(DecisionStatus.normalize(request.status()));
        rule.setOwner(request.owner());
        rule.setRemark(request.remark());
        rule.setVersionNo(rule.getVersionNo() == null ? 1 : rule.getVersionNo() + (id == null ? 0 : 1));
        upsert(ruleMapper, rule, id);
        snapshot("RULE", rule.getId(), rule.getCode(), rule.getVersionNo(), rule, "保存规则");
        return rule;
    }

    public List<DecisionRuleSet> ruleSets(String sceneCode) {
        return ruleSetMapper.selectList(Wrappers.lambdaQuery(DecisionRuleSet.class)
                .eq(StringUtils.hasText(sceneCode), DecisionRuleSet::getSceneCode, sceneCode)
                .orderByDesc(DecisionRuleSet::getUpdateTime));
    }

    @Transactional
    public DecisionRuleSet saveRuleSet(Long id, RuleSetRequest request) {
        DecisionRuleSet ruleSet = id == null ? new DecisionRuleSet() : require(ruleSetMapper, id, "规则集不存在");
        ruleSet.setSceneCode(required(request.sceneCode(), "规则集必须绑定场景"));
        ruleSet.setCode(required(request.code(), "规则集编码不能为空"));
        ruleSet.setName(required(request.name(), "规则集名称不能为空"));
        ruleSet.setStrategy(StringUtils.hasText(request.strategy()) ? request.strategy().toUpperCase(Locale.ROOT) : "ANY");
        ruleSet.setRequiredMatch(request.requiredMatch() == null ? 0 : request.requiredMatch());
        ruleSet.setShortCircuit(Boolean.TRUE.equals(request.shortCircuit()));
        ruleSet.setRuleCodesJson(jsonService.write(request.ruleCodes() == null ? List.of() : request.ruleCodes()));
        ruleSet.setStatus(DecisionStatus.normalize(request.status()));
        ruleSet.setRemark(request.remark());
        ruleSet.setVersionNo(ruleSet.getVersionNo() == null ? 1 : ruleSet.getVersionNo() + (id == null ? 0 : 1));
        upsert(ruleSetMapper, ruleSet, id);
        snapshot("RULE_SET", ruleSet.getId(), ruleSet.getCode(), ruleSet.getVersionNo(), ruleSet, "保存规则集");
        return ruleSet;
    }

    public List<FlowView> flows(String sceneCode) {
        return flowMapper.selectList(Wrappers.lambdaQuery(DecisionFlow.class)
                .eq(StringUtils.hasText(sceneCode), DecisionFlow::getSceneCode, sceneCode)
                .orderByDesc(DecisionFlow::getUpdateTime))
                .stream()
                .map(this::flowView)
                .toList();
    }

    @Transactional
    public DecisionFlow saveFlow(Long id, FlowRequest request) {
        DecisionFlow flow = id == null ? new DecisionFlow() : require(flowMapper, id, "决策流不存在");
        flow.setSceneCode(required(request.sceneCode(), "决策流必须绑定场景"));
        flow.setCode(required(request.code(), "决策流编码不能为空"));
        flow.setName(required(request.name(), "决策流名称不能为空"));
        flow.setStatus(DecisionStatus.normalize(request.status()));
        flow.setRemark(request.remark());
        flow.setVersionNo(flow.getVersionNo() == null ? 1 : flow.getVersionNo() + (id == null ? 0 : 1));
        upsert(flowMapper, flow, id);
        flowNodeMapper.delete(Wrappers.lambdaQuery(DecisionFlowNode.class).eq(DecisionFlowNode::getFlowId, flow.getId()));
        flowEdgeMapper.delete(Wrappers.lambdaQuery(DecisionFlowEdge.class).eq(DecisionFlowEdge::getFlowId, flow.getId()));
        saveFlowNodes(flow.getId(), request.nodes());
        saveFlowEdges(flow.getId(), request.edges());
        snapshot("FLOW", flow.getId(), flow.getCode(), flow.getVersionNo(), Map.of("flow", flow, "nodes", request.nodes(), "edges", request.edges()), "保存决策流");
        return flow;
    }

    public List<?> advanced(String type) {
        return switch (type) {
            case "data-sources" -> list(dataSourceMapper);
            case "models" -> list(modelConfigMapper);
            case "score-cards" -> list(scoreCardMapper);
            case "decision-tables" -> list(tableMapper);
            default -> throw new IllegalArgumentException("不支持的类型: " + type);
        };
    }

    @Transactional
    public Object saveAdvanced(String type, Long id, AdvancedAssetRequest request) {
        return switch (type) {
            case "data-sources" -> saveDataSource(id, request);
            case "models" -> saveModel(id, request);
            case "score-cards" -> saveScoreCard(id, request);
            case "decision-tables" -> saveDecisionTable(id, request);
            default -> throw new IllegalArgumentException("不支持的类型: " + type);
        };
    }

    public List<DecisionDomainVersion> versions(String targetType, Long targetId) {
        return versionMapper.selectList(Wrappers.lambdaQuery(DecisionDomainVersion.class)
                .eq(DecisionDomainVersion::getTargetType, targetType)
                .eq(DecisionDomainVersion::getTargetId, targetId)
                .orderByDesc(DecisionDomainVersion::getVersionNo));
    }

    public List<DecisionPublishRequest> publishRequests() {
        return publishRequestMapper.selectList(Wrappers.lambdaQuery(DecisionPublishRequest.class)
                .orderByDesc(DecisionPublishRequest::getUpdateTime));
    }

    @Transactional
    public DecisionDomainVersion rollback(Long versionId) {
        DecisionDomainVersion version = require(versionMapper, versionId, "版本不存在");
        rollbackSnapshot(version);
        snapshot(version.getTargetType(), version.getTargetId(), version.getCode(), currentVersion(version.getTargetType(), version.getTargetId()) + 1, jsonService.readMap(version.getSnapshotJson()), "回滚到版本 " + version.getVersionNo());
        return version;
    }

    @Transactional
    public DecisionPublishRequest createPublishRequest(PublishRequestForm form) {
        DecisionPublishRequest request = new DecisionPublishRequest();
        request.setTargetType(form.targetType());
        request.setTargetId(form.targetId());
        request.setCode(form.code());
        request.setVersionNo(currentVersion(form.targetType(), form.targetId()));
        request.setWorkflowModelId(form.workflowModelId() == null ? properties.getPublish().getWorkflowModelId() : form.workflowModelId());
        request.setApplicant(StringUtils.hasText(form.applicant()) ? form.applicant() : "console");
        request.setRemark(form.remark());
        publishRequestMapper.insert(request);
        return request;
    }

    @Transactional
    public DecisionPublishRequest submitPublishRequest(Long id) {
        DecisionPublishRequest request = require(publishRequestMapper, id, "发布申请不存在");
        if (request.getWorkflowModelId() == null) {
            throw new IllegalStateException("未配置 decision.engine.publish.workflow-model-id，无法提交 Flowable 审批");
        }
        request.setWorkflowBusinessKey("DECISION_PUBLISH:" + id);
        request.setProcessInstanceId(flowablePublishClient.submit(request));
        request.setStatus("APPROVING");
        publishRequestMapper.updateById(request);
        return request;
    }

    @Transactional
    public DecisionPublishRequest refreshPublishRequest(Long id) {
        DecisionPublishRequest request = require(publishRequestMapper, id, "发布申请不存在");
        String status = StringUtils.hasText(request.getProcessInstanceId()) ? flowablePublishClient.status(request.getProcessInstanceId()) : request.getStatus();
        if ("COMPLETED".equalsIgnoreCase(status)) {
            request.setStatus("APPROVED");
            publish(request.getTargetType(), request.getTargetId(), request.getCode(), request.getVersionNo(), request.getApplicant(), request.getRemark());
        } else if (Set.of("REJECTED", "REVOKED", "TERMINATED").contains(status.toUpperCase(Locale.ROOT))) {
            request.setStatus(status.toUpperCase(Locale.ROOT));
        }
        publishRequestMapper.updateById(request);
        return request;
    }

    public List<DecisionGrayPolicy> grayPolicies() {
        return list(grayPolicyMapper);
    }

    public DecisionGrayPolicy saveGrayPolicy(Long id, GrayPolicyRequest request) {
        DecisionGrayPolicy policy = id == null ? new DecisionGrayPolicy() : require(grayPolicyMapper, id, "灰度策略不存在");
        policy.setSceneCode(request.sceneCode());
        policy.setTargetType(request.targetType());
        policy.setTargetCode(request.targetCode());
        policy.setVersionNo(request.versionNo());
        policy.setPercent(request.percent() == null ? 0 : request.percent());
        policy.setConditionJson(jsonService.write(request.condition() == null ? Map.of() : request.condition()));
        policy.setEnabled(Boolean.TRUE.equals(request.enabled()));
        policy.setRemark(request.remark());
        upsert(grayPolicyMapper, policy, id);
        return policy;
    }

    public DecisionSimulationJob saveSimulationJob(SimulationJobRequest request) {
        DecisionSimulationJob job = new DecisionSimulationJob();
        job.setSceneCode(request.sceneCode());
        job.setName(required(request.name(), "仿真任务名称不能为空"));
        job.setSampleJson(jsonService.write(request.samples() == null ? List.of() : request.samples()));
        job.setRemark(request.remark());
        simulationJobMapper.insert(job);
        return job;
    }

    public List<DecisionSimulationJob> simulationJobs() {
        return list(simulationJobMapper);
    }

    private void saveFlowNodes(Long flowId, List<Map<String, Object>> nodes) {
        int index = 1;
        for (Map<String, Object> item : nodes == null ? List.<Map<String, Object>>of() : nodes) {
            DecisionFlowNode node = new DecisionFlowNode();
            node.setFlowId(flowId);
            node.setNodeKey(Objects.toString(item.getOrDefault("id", item.getOrDefault("code", "node" + index))));
            node.setType(Objects.toString(item.getOrDefault("type", "RULE")));
            node.setCode(Objects.toString(item.getOrDefault("code", "")));
            node.setLabel(Objects.toString(item.getOrDefault("label", node.getCode())));
            node.setEnabled(!Boolean.FALSE.equals(item.get("enabled")));
            node.setSort(number(item.getOrDefault("sort", index)));
            node.setX(number(item.getOrDefault("x", 0)));
            node.setY(number(item.getOrDefault("y", 0)));
            node.setConfigJson(jsonService.write(item));
            flowNodeMapper.insert(node);
            index++;
        }
    }

    private void saveFlowEdges(Long flowId, List<Map<String, Object>> edges) {
        int index = 1;
        for (Map<String, Object> item : edges == null ? List.<Map<String, Object>>of() : edges) {
            DecisionFlowEdge edge = new DecisionFlowEdge();
            edge.setFlowId(flowId);
            edge.setEdgeKey(Objects.toString(item.getOrDefault("id", "edge" + index)));
            edge.setSourceKey(Objects.toString(item.get("source")));
            edge.setTargetKey(Objects.toString(item.get("target")));
            edge.setBranch(Objects.toString(item.getOrDefault("branch", ""), null));
            edge.setLabel(Objects.toString(item.getOrDefault("label", ""), null));
            flowEdgeMapper.insert(edge);
            index++;
        }
    }

    private FlowView flowView(DecisionFlow flow) {
        List<Map<String, Object>> nodes = flowNodeMapper.selectList(Wrappers.lambdaQuery(DecisionFlowNode.class)
                        .eq(DecisionFlowNode::getFlowId, flow.getId())
                        .orderByAsc(DecisionFlowNode::getSort)
                        .orderByAsc(DecisionFlowNode::getId))
                .stream()
                .map(this::flowNodeView)
                .toList();
        List<Map<String, Object>> edges = flowEdgeMapper.selectList(Wrappers.lambdaQuery(DecisionFlowEdge.class)
                        .eq(DecisionFlowEdge::getFlowId, flow.getId())
                        .orderByAsc(DecisionFlowEdge::getId))
                .stream()
                .map(this::flowEdgeView)
                .toList();
        if (edges.isEmpty() && nodes.size() > 1) {
            edges = sequentialFlowEdges(nodes);
        }
        return new FlowView(
                flow.getId(),
                flow.getSceneCode(),
                flow.getCode(),
                flow.getName(),
                flow.getStatus(),
                flow.getVersionNo(),
                flow.getRemark(),
                flow.getCreateTime(),
                flow.getUpdateTime(),
                nodes,
                edges
        );
    }

    private Map<String, Object> flowNodeView(DecisionFlowNode node) {
        Map<String, Object> payload = new LinkedHashMap<>(jsonService.readMap(node.getConfigJson()));
        payload.put("id", node.getNodeKey());
        payload.put("type", node.getType());
        payload.put("code", node.getCode());
        payload.put("label", node.getLabel());
        payload.put("enabled", node.getEnabled());
        payload.put("sort", node.getSort());
        payload.put("x", node.getX());
        payload.put("y", node.getY());
        return payload;
    }

    private List<Map<String, Object>> sequentialFlowEdges(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (int index = 0; index < nodes.size() - 1; index++) {
            String source = Objects.toString(nodes.get(index).get("id"), "");
            String target = Objects.toString(nodes.get(index + 1).get("id"), "");
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", "edge_" + source + "_" + target);
            edge.put("source", source);
            edge.put("target", target);
            edges.add(edge);
        }
        return edges;
    }

    private Map<String, Object> flowEdgeView(DecisionFlowEdge edge) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", edge.getEdgeKey());
        payload.put("source", edge.getSourceKey());
        payload.put("target", edge.getTargetKey());
        if (StringUtils.hasText(edge.getBranch())) {
            payload.put("branch", edge.getBranch());
        }
        if (StringUtils.hasText(edge.getLabel())) {
            payload.put("label", edge.getLabel());
        }
        return payload;
    }

    private DecisionDataSource saveDataSource(Long id, AdvancedAssetRequest request) {
        DecisionDataSource item = id == null ? new DecisionDataSource() : require(dataSourceMapper, id, "数据源不存在");
        item.setCode(required(request.code(), "数据源编码不能为空"));
        item.setName(required(request.name(), "数据源名称不能为空"));
        item.setType(StringUtils.hasText(request.type()) ? request.type() : "HTTP");
        item.setConfigJson(jsonService.write(request.config() == null ? Map.of() : request.config()));
        item.setStatus(DecisionStatus.normalize(request.status()));
        item.setRemark(request.remark());
        upsert(dataSourceMapper, item, id);
        snapshot("DATA_SOURCE", item.getId(), item.getCode(), 1, item, "保存数据源");
        return item;
    }

    private DecisionModelConfig saveModel(Long id, AdvancedAssetRequest request) {
        DecisionModelConfig item = id == null ? new DecisionModelConfig() : require(modelConfigMapper, id, "模型不存在");
        item.setCode(required(request.code(), "模型编码不能为空"));
        item.setName(required(request.name(), "模型名称不能为空"));
        item.setProvider(request.provider());
        item.setConfigJson(jsonService.write(request.config() == null ? Map.of() : request.config()));
        item.setStatus(DecisionStatus.normalize(request.status()));
        item.setRemark(request.remark());
        upsert(modelConfigMapper, item, id);
        snapshot("MODEL", item.getId(), item.getCode(), 1, item, "保存模型");
        return item;
    }

    private DecisionScoreCard saveScoreCard(Long id, AdvancedAssetRequest request) {
        DecisionScoreCard item = id == null ? new DecisionScoreCard() : require(scoreCardMapper, id, "评分卡不存在");
        item.setSceneCode(request.sceneCode());
        item.setCode(required(request.code(), "评分卡编码不能为空"));
        item.setName(required(request.name(), "评分卡名称不能为空"));
        item.setItemsJson(jsonService.write(request.items() == null ? List.of() : request.items()));
        item.setMappingJson(jsonService.write(request.mapping() == null ? List.of() : request.mapping()));
        item.setStatus(DecisionStatus.normalize(request.status()));
        item.setRemark(request.remark());
        item.setVersionNo(item.getVersionNo() == null ? 1 : item.getVersionNo() + (id == null ? 0 : 1));
        upsert(scoreCardMapper, item, id);
        snapshot("SCORE_CARD", item.getId(), item.getCode(), item.getVersionNo(), item, "保存评分卡");
        return item;
    }

    private DecisionTable saveDecisionTable(Long id, AdvancedAssetRequest request) {
        DecisionTable item = id == null ? new DecisionTable() : require(tableMapper, id, "决策表不存在");
        item.setSceneCode(request.sceneCode());
        item.setCode(required(request.code(), "决策表编码不能为空"));
        item.setName(required(request.name(), "决策表名称不能为空"));
        item.setHitPolicy(StringUtils.hasText(request.hitPolicy()) ? request.hitPolicy() : "FIRST");
        item.setRowsJson(jsonService.write(request.rows() == null ? List.of() : request.rows()));
        item.setStatus(DecisionStatus.normalize(request.status()));
        item.setRemark(request.remark());
        item.setVersionNo(item.getVersionNo() == null ? 1 : item.getVersionNo() + (id == null ? 0 : 1));
        upsert(tableMapper, item, id);
        snapshot("DECISION_TABLE", item.getId(), item.getCode(), item.getVersionNo(), item, "保存决策表");
        return item;
    }

    private void publish(String targetType, Long targetId, String code, Integer versionNo, String publishBy, String remark) {
        DecisionDomainPublishRecord record = new DecisionDomainPublishRecord();
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setCode(code);
        record.setVersionNo(versionNo);
        record.setPublishBy(publishBy);
        record.setRemark(remark);
        publishRecordMapper.insert(record);
        markPublished(targetType, targetId);
    }

    private void markPublished(String targetType, Long targetId) {
        switch (targetType) {
            case "SCENE" -> setStatus(sceneMapper, targetId, DecisionStatus.PUBLISHED);
            case "VARIABLE" -> setStatus(variableMapper, targetId, DecisionStatus.PUBLISHED);
            case "RULE" -> setStatus(ruleMapper, targetId, DecisionStatus.PUBLISHED);
            case "RULE_SET" -> setStatus(ruleSetMapper, targetId, DecisionStatus.PUBLISHED);
            case "FLOW" -> setStatus(flowMapper, targetId, DecisionStatus.PUBLISHED);
            case "DATA_SOURCE" -> setStatus(dataSourceMapper, targetId, DecisionStatus.PUBLISHED);
            case "MODEL" -> setStatus(modelConfigMapper, targetId, DecisionStatus.PUBLISHED);
            case "SCORE_CARD" -> setStatus(scoreCardMapper, targetId, DecisionStatus.PUBLISHED);
            case "DECISION_TABLE" -> setStatus(tableMapper, targetId, DecisionStatus.PUBLISHED);
            default -> throw new IllegalArgumentException("不支持的发布对象: " + targetType);
        }
    }

    private <T> void setStatus(BaseMapper<T> mapper, Long id, String status) {
        T entity = mapper.selectById(id);
        try {
            entity.getClass().getMethod("setStatus", String.class).invoke(entity, status);
            mapper.updateById(entity);
        } catch (Exception ex) {
            throw new IllegalStateException("更新发布状态失败", ex);
        }
    }

    private Integer currentVersion(String targetType, Long targetId) {
        return versionMapper.selectList(Wrappers.lambdaQuery(DecisionDomainVersion.class)
                        .eq(DecisionDomainVersion::getTargetType, targetType)
                        .eq(DecisionDomainVersion::getTargetId, targetId)
                        .orderByDesc(DecisionDomainVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(DecisionDomainVersion::getVersionNo)
                .orElse(1);
    }

    private void snapshot(String targetType, Long targetId, String code, Integer versionNo, Object snapshot, String remark) {
        DecisionDomainVersion version = new DecisionDomainVersion();
        version.setTargetType(targetType);
        version.setTargetId(targetId);
        version.setCode(code);
        version.setVersionNo(versionNo == null ? 1 : versionNo);
        version.setSnapshotJson(jsonService.write(snapshot));
        version.setRemark(remark);
        versionMapper.insert(version);
    }

    private <T> List<T> list(BaseMapper<T> mapper) {
        return mapper.selectList(null);
    }

    private <T> T require(BaseMapper<T> mapper, Long id, String message) {
        return Optional.ofNullable(mapper.selectById(id)).orElseThrow(() -> new NoSuchElementException(message));
    }

    private <T> void upsert(BaseMapper<T> mapper, T entity, Long id) {
        if (id == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Integer number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void rollbackSnapshot(DecisionDomainVersion version) {
        try {
            switch (version.getTargetType()) {
                case "SCENE" -> updateFromSnapshot(sceneMapper, version, DecisionScene.class);
                case "VARIABLE" -> updateFromSnapshot(variableMapper, version, DecisionVariable.class);
                case "RULE" -> updateFromSnapshot(ruleMapper, version, DecisionRule.class);
                case "RULE_SET" -> updateFromSnapshot(ruleSetMapper, version, DecisionRuleSet.class);
                case "DATA_SOURCE" -> updateFromSnapshot(dataSourceMapper, version, DecisionDataSource.class);
                case "MODEL" -> updateFromSnapshot(modelConfigMapper, version, DecisionModelConfig.class);
                case "SCORE_CARD" -> updateFromSnapshot(scoreCardMapper, version, DecisionScoreCard.class);
                case "DECISION_TABLE" -> updateFromSnapshot(tableMapper, version, DecisionTable.class);
                case "FLOW" -> {
                    Map<String, Object> snapshot = jsonService.readMap(version.getSnapshotJson());
                    DecisionFlow flow = objectMapper.convertValue(snapshot.get("flow"), DecisionFlow.class);
                    flow.setId(version.getTargetId());
                    flowMapper.updateById(flow);
                    flowNodeMapper.delete(Wrappers.lambdaQuery(DecisionFlowNode.class).eq(DecisionFlowNode::getFlowId, flow.getId()));
                    flowEdgeMapper.delete(Wrappers.lambdaQuery(DecisionFlowEdge.class).eq(DecisionFlowEdge::getFlowId, flow.getId()));
                    saveFlowNodes(flow.getId(), (List<Map<String, Object>>) snapshot.getOrDefault("nodes", List.of()));
                    saveFlowEdges(flow.getId(), (List<Map<String, Object>>) snapshot.getOrDefault("edges", List.of()));
                }
                default -> throw new IllegalArgumentException("不支持的回滚对象: " + version.getTargetType());
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("回滚版本失败", ex);
        }
    }

    private <T> void updateFromSnapshot(BaseMapper<T> mapper, DecisionDomainVersion version, Class<T> type) {
        T entity = objectMapper.convertValue(jsonService.readMap(version.getSnapshotJson()), type);
        try {
            entity.getClass().getMethod("setId", Long.class).invoke(entity, version.getTargetId());
            mapper.updateById(entity);
        } catch (Exception ex) {
            throw new IllegalStateException("更新回滚对象失败", ex);
        }
    }
}
