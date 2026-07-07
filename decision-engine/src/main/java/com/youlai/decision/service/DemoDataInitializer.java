package com.youlai.decision.service;

import com.youlai.decision.mapper.DecisionSceneMapper;
import com.youlai.decision.model.DecisionRule;
import com.youlai.decision.model.DecisionRuleSet;
import com.youlai.decision.model.DomainApiModels.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final DecisionSceneMapper sceneMapper;
    private final DecisionDomainService domainService;

    public DemoDataInitializer(DecisionSceneMapper sceneMapper, DecisionDomainService domainService) {
        this.sceneMapper = sceneMapper;
        this.domainService = domainService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sceneMapper.selectCount(null) > 0) {
            return;
        }
        createScene();
        createVariables();
        createRules();
        createRuleSet();
        createFlow();
        createAdvancedAssets();
    }

    private void createScene() {
        domainService.saveScene(null, new SceneRequest(
                "trade_risk",
                "交易风控",
                "风控",
                "已发布",
                List.of(
                        Map.of("field", "orderAmount", "type", "NUMBER", "required", true),
                        Map.of("field", "city", "type", "STRING", "required", false),
                        Map.of("field", "deviceId", "type", "STRING", "required", false)
                ),
                List.of(
                        Map.of("field", "decisionResult", "type", "STRING"),
                        Map.of("field", "riskLevel", "type", "STRING"),
                        Map.of("field", "score", "type", "NUMBER")
                ),
                "risk-team",
                "初始化交易风控场景"
        ));
    }

    private void createVariables() {
        domainService.saveVariable(null, new VariableRequest("trade_risk", "orderAmount", "订单金额", "NUMBER", "REQUEST", Map.of(), 0, "已发布", "请求入参变量"));
        domainService.saveVariable(null, new VariableRequest("trade_risk", "city", "交易城市", "STRING", "REQUEST", Map.of(), null, "已发布", "请求入参变量"));
        domainService.saveVariable(null, new VariableRequest(
                "trade_risk",
                "tycRiskCount",
                "天眼查风险数",
                "NUMBER",
                "HARE",
                Map.of("shortName", "tyc", "apiName", "企业风险信息", "absoluteKey", "$.data.riskCount"),
                0,
                "已发布",
                "Hare mock 变量"
        ));
    }

    private void createRules() {
        DecisionRule large = domainService.saveRule(null, new RuleRequest(
                "trade_risk",
                "RISK_001",
                "大额交易转人工",
                100,
                "MIXED",
                "BOOLEAN",
                0,
                "",
                Map.of("logic", "AND", "items", List.of(Map.of("field", "orderAmount", "operator", ">", "value", 10000))),
                Map.of("decisionResult", "REVIEW", "riskLevel", "HIGH", "score", 82, "tags", List.of("大额交易", "人工审核"), "reason", "订单金额超过 10000", "outputs", Map.of("reviewQueue", "trade-risk")),
                Map.of(),
                "已发布",
                "risk-team",
                "订单金额超过一万进入人工审核"
        ));
        large.setStatus("已发布");

        DecisionRule city = domainService.saveRule(null, new RuleRequest(
                "trade_risk",
                "RISK_002",
                "敏感城市观察",
                60,
                "MIXED",
                "BOOLEAN",
                0,
                "",
                Map.of("field", "city", "operator", "in", "value", List.of("重庆", "深圳")),
                Map.of("decisionResult", "OBSERVE", "riskLevel", "MEDIUM", "score", 20, "tags", List.of("地域观察"), "reason", "交易城市命中观察名单"),
                Map.of(),
                "已发布",
                "risk-team",
                "敏感城市交易进入观察"
        ));
        city.setStatus("已发布");
    }

    private void createRuleSet() {
        DecisionRuleSet ruleSet = domainService.saveRuleSet(null, new RuleSetRequest(
                "trade_risk",
                "trade_risk_default_set",
                "交易风控默认规则集",
                "ANY",
                0,
                false,
                List.of("RISK_001", "RISK_002"),
                "已发布",
                "按优先级执行交易风控规则"
        ));
        ruleSet.setStatus("已发布");
    }

    private void createFlow() {
        domainService.saveFlow(null, new FlowRequest(
                "trade_risk",
                "trade_risk_flow",
                "交易风控决策流",
                "已发布",
                List.of(
                        Map.of("id", "start", "type", "START", "code", "start", "label", "开始", "enabled", true, "sort", 1),
                        Map.of("id", "rule_set", "type", "RULE_SET", "code", "trade_risk_default_set", "label", "默认规则集", "enabled", true, "sort", 2),
                        Map.of("id", "end", "type", "END", "code", "end", "label", "结束", "enabled", true, "sort", 3)
                ),
                List.of(
                        Map.of("id", "edge_start_rule_set", "source", "start", "target", "rule_set"),
                        Map.of("id", "edge_rule_set_end", "source", "rule_set", "target", "end")
                ),
                "初始化交易风控决策流"
        ));
    }

    private void createAdvancedAssets() {
        domainService.saveAdvanced("data-sources", null, new AdvancedAssetRequest(null, "redis_blacklist", "Redis 黑名单", "STATIC", null, null, Map.of("output", Map.of("blackDevice", false)), null, null, null, "已发布", "演示数据源"));
        domainService.saveAdvanced("models", null, new AdvancedAssetRequest(null, "credit_score_model", "信用评分模型", null, "MOCK", null, Map.of("fallbackOutput", Map.of("score", 70)), null, null, null, "已发布", "演示模型"));
        domainService.saveAdvanced("score-cards", null, new AdvancedAssetRequest("trade_risk", "trade_score_card", "交易评分卡", null, null, null, null,
                List.of(Map.of("field", "orderAmount", "ranges", List.of(Map.of("between", List.of(0, 10000), "score", 10), Map.of("between", List.of(10000, 999999), "score", 80)))),
                List.of(Map.of("min", 80, "decisionResult", "REVIEW")), null, "已发布", "演示评分卡"));
        domainService.saveAdvanced("decision-tables", null, new AdvancedAssetRequest("trade_risk", "trade_decision_table", "交易决策表", null, null, "FIRST", null, null, null,
                List.of(Map.of("city", "重庆", "decisionResult", "OBSERVE", "riskLevel", "MEDIUM")), "已发布", "演示决策表"));
    }
}
