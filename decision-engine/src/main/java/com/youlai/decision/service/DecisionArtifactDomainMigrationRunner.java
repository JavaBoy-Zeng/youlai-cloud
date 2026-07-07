package com.youlai.decision.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.*;
import com.youlai.decision.model.*;
import com.youlai.decision.model.DomainApiModels.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将旧 decision_artifact 归档数据一次性迁移到领域表；新执行链路不再读取旧表。
 */
@Component
@Order(1)
public class DecisionArtifactDomainMigrationRunner implements ApplicationRunner {

    private final DecisionArtifactMapper artifactMapper;
    private final DecisionSceneMapper sceneMapper;
    private final DecisionVariableMapper variableMapper;
    private final DecisionRuleMapper ruleMapper;
    private final DecisionRuleSetMapper ruleSetMapper;
    private final DecisionFlowMapper flowMapper;
    private final DecisionDataSourceMapper dataSourceMapper;
    private final DecisionModelConfigMapper modelConfigMapper;
    private final DecisionScoreCardMapper scoreCardMapper;
    private final DecisionTableMapper decisionTableMapper;
    private final AuditLogMapper auditLogMapper;
    private final DecisionDomainService domainService;
    private final JsonService jsonService;

    public DecisionArtifactDomainMigrationRunner(
            DecisionArtifactMapper artifactMapper,
            DecisionSceneMapper sceneMapper,
            DecisionVariableMapper variableMapper,
            DecisionRuleMapper ruleMapper,
            DecisionRuleSetMapper ruleSetMapper,
            DecisionFlowMapper flowMapper,
            DecisionDataSourceMapper dataSourceMapper,
            DecisionModelConfigMapper modelConfigMapper,
            DecisionScoreCardMapper scoreCardMapper,
            DecisionTableMapper tableMapper,
            AuditLogMapper auditLogMapper,
            DecisionDomainService domainService,
            JsonService jsonService
    ) {
        this.artifactMapper = artifactMapper;
        this.sceneMapper = sceneMapper;
        this.variableMapper = variableMapper;
        this.ruleMapper = ruleMapper;
        this.ruleSetMapper = ruleSetMapper;
        this.flowMapper = flowMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.scoreCardMapper = scoreCardMapper;
        this.decisionTableMapper = tableMapper;
        this.auditLogMapper = auditLogMapper;
        this.domainService = domainService;
        this.jsonService = jsonService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DecisionArtifact> artifacts = artifactMapper.selectList(null);
        if (CollectionUtils.isEmpty(artifacts)) {
            return;
        }
        for (DecisionArtifact artifact : artifacts) {
            try {
                migrate(artifact);
            } catch (Exception ex) {
                audit("MIGRATE_FAILED", artifact, ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void migrate(DecisionArtifact artifact) {
        ArtifactKind kind = artifact.getKind();
        if (kind == null) {
            audit("MIGRATE_SKIPPED", artifact, "旧资产 kind 为空");
            return;
        }
        Map<String, Object> content = jsonService.readMap(artifact.getContentJson());
        switch (kind) {
            case SCENE -> {
                if (existsScene(artifact.getCode())) return;
                domainService.saveScene(null, new SceneRequest(
                        artifact.getCode(),
                        artifact.getName(),
                        artifact.getCategory(),
                        artifact.getStatus(),
                        list(content.get("inputs")),
                        list(content.get("outputs")),
                        artifact.getOwner(),
                        artifact.getRemark()
                ));
            }
            case VARIABLE -> {
                if (existsVariable(sceneCode(content), artifact.getCode())) return;
                domainService.saveVariable(null, new VariableRequest(
                        sceneCode(content),
                        artifact.getCode(),
                        artifact.getName(),
                        text(content, "type", "STRING"),
                        text(content, "source", "REQUEST"),
                        map(content.get("sourceConfig")),
                        content.get("defaultValue"),
                        artifact.getStatus(),
                        artifact.getRemark()
                ));
            }
            case RULE -> {
                if (existsRule(artifact.getCode())) return;
                Object conditions = content.getOrDefault("conditions", Map.of());
                domainService.saveRule(null, new RuleRequest(
                        requiredScene(content),
                        artifact.getCode(),
                        artifact.getName(),
                        number(content.get("priority")),
                        text(content, "expressionType", "MIXED"),
                        text(content, "matchMode", "BOOLEAN"),
                        number(content.get("requiredMatch")),
                        Objects.toString(content.getOrDefault("conditionExpression", ""), ""),
                        conditions instanceof List<?> items ? Map.of("logic", "AND", "items", items) : map(conditions),
                        map(content.get("actions")),
                        map(content.get("fallbackAction")),
                        artifact.getStatus(),
                        artifact.getOwner(),
                        artifact.getRemark()
                ));
            }
            case RULE_SET -> {
                if (existsRuleSet(artifact.getCode())) return;
                domainService.saveRuleSet(null, new RuleSetRequest(
                        requiredScene(content),
                        artifact.getCode(),
                        artifact.getName(),
                        text(content, "strategy", "ANY"),
                        number(content.get("requiredMatch")),
                        Boolean.TRUE.equals(content.get("shortCircuit")),
                        stringList(content.get("ruleCodes")),
                        artifact.getStatus(),
                        artifact.getRemark()
                ));
            }
            case FLOW -> {
                if (existsFlow(artifact.getCode())) return;
                domainService.saveFlow(null, new FlowRequest(
                        requiredScene(content),
                        artifact.getCode(),
                        artifact.getName(),
                        artifact.getStatus(),
                        list(content.get("nodes")),
                        list(content.get("edges")),
                        artifact.getRemark()
                ));
            }
            case DATA_SOURCE -> {
                if (existsDataSource(artifact.getCode())) return;
                domainService.saveAdvanced("data-sources", null, new AdvancedAssetRequest(
                        null,
                        artifact.getCode(),
                        artifact.getName(),
                        text(content, "type", "HTTP"),
                        null,
                        null,
                        content,
                        null,
                        null,
                        null,
                        artifact.getStatus(),
                        artifact.getRemark()
                ));
            }
            case MODEL -> {
                if (existsModel(artifact.getCode())) return;
                domainService.saveAdvanced("models", null, new AdvancedAssetRequest(
                        null,
                        artifact.getCode(),
                        artifact.getName(),
                        null,
                        text(content, "provider", "LOCAL"),
                        null,
                        content,
                        null,
                        null,
                        null,
                        artifact.getStatus(),
                        artifact.getRemark()
                ));
            }
            case SCORE_CARD -> {
                if (existsScoreCard(artifact.getCode())) return;
                domainService.saveAdvanced("score-cards", null, new AdvancedAssetRequest(
                        sceneCode(content),
                        artifact.getCode(),
                        artifact.getName(),
                        null,
                        null,
                        null,
                        null,
                        list(content.get("items")),
                        list(content.get("mapping")),
                        null,
                        artifact.getStatus(),
                        artifact.getRemark()
                ));
            }
            case DECISION_TABLE -> {
                if (existsDecisionTable(artifact.getCode())) return;
                domainService.saveAdvanced("decision-tables", null, new AdvancedAssetRequest(
                        sceneCode(content),
                        artifact.getCode(),
                        artifact.getName(),
                        null,
                        null,
                        text(content, "hitPolicy", "FIRST"),
                        null,
                        null,
                        null,
                        list(content.get("rows")),
                        artifact.getStatus(),
                        artifact.getRemark()
                ));
            }
        }
    }

    private void audit(String action, DecisionArtifact artifact, String message) {
        AuditLog log = new AuditLog();
        log.setOperator("system");
        log.setAction(action);
        log.setTargetKind(artifact.getKind() == null ? "UNKNOWN" : artifact.getKind().name());
        log.setTargetCode(artifact.getCode());
        log.setDetailJson(jsonService.write(Map.of("message", Objects.toString(message, ""), "artifactId", artifact.getId())));
        auditLogMapper.insert(log);
    }

    private boolean existsScene(String code) {
        return sceneMapper.selectCount(Wrappers.lambdaQuery(DecisionScene.class).eq(DecisionScene::getCode, code)) > 0;
    }

    private boolean existsVariable(String sceneCode, String code) {
        return variableMapper.selectCount(Wrappers.lambdaQuery(DecisionVariable.class)
                .eq(DecisionVariable::getCode, code)
                .eq(sceneCode != null, DecisionVariable::getSceneCode, sceneCode)
                .isNull(sceneCode == null, DecisionVariable::getSceneCode)) > 0;
    }

    private boolean existsRule(String code) {
        return ruleMapper.selectCount(Wrappers.lambdaQuery(DecisionRule.class).eq(DecisionRule::getCode, code)) > 0;
    }

    private boolean existsRuleSet(String code) {
        return ruleSetMapper.selectCount(Wrappers.lambdaQuery(DecisionRuleSet.class).eq(DecisionRuleSet::getCode, code)) > 0;
    }

    private boolean existsFlow(String code) {
        return flowMapper.selectCount(Wrappers.lambdaQuery(DecisionFlow.class).eq(DecisionFlow::getCode, code)) > 0;
    }

    private boolean existsDataSource(String code) {
        return dataSourceMapper.selectCount(Wrappers.lambdaQuery(DecisionDataSource.class).eq(DecisionDataSource::getCode, code)) > 0;
    }

    private boolean existsModel(String code) {
        return modelConfigMapper.selectCount(Wrappers.lambdaQuery(DecisionModelConfig.class).eq(DecisionModelConfig::getCode, code)) > 0;
    }

    private boolean existsScoreCard(String code) {
        return scoreCardMapper.selectCount(Wrappers.lambdaQuery(DecisionScoreCard.class).eq(DecisionScoreCard::getCode, code)) > 0;
    }

    private boolean existsDecisionTable(String code) {
        return decisionTableMapper.selectCount(Wrappers.lambdaQuery(DecisionTable.class).eq(DecisionTable::getCode, code)) > 0;
    }

    private String requiredScene(Map<String, Object> content) {
        String sceneCode = sceneCode(content);
        if (sceneCode == null || sceneCode.isBlank()) {
            throw new IllegalArgumentException("旧资产缺少 sceneCode");
        }
        return sceneCode;
    }

    private String sceneCode(Map<String, Object> content) {
        String value = Objects.toString(content.getOrDefault("sceneCode", ""), "");
        return value.isBlank() ? null : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String text(Map<String, Object> content, String key, String fallback) {
        String value = Objects.toString(content.getOrDefault(key, fallback), fallback);
        return value.isBlank() ? fallback : value;
    }

    private Integer number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
