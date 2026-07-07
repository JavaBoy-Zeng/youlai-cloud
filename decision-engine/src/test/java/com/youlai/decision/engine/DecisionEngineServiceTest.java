package com.youlai.decision.engine;

import com.youlai.decision.mapper.DecisionHitDetailLogMapper;
import com.youlai.decision.model.DecisionRule;
import com.youlai.decision.model.DecisionRuleSet;
import com.youlai.decision.model.DomainApiModels.*;
import com.youlai.decision.service.DecisionDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:decision-engine-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.cloud.bootstrap.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.security.oauth2.authorizationserver.token-uri=http://localhost:9999/youlai-auth/oauth2/token",
        "decision.engine.hare.mode=mock"
})
class DecisionEngineServiceTest {

    @Autowired
    private DecisionRuntimeService runtimeService;

    @Autowired
    private DecisionDomainService domainService;

    @Autowired
    private DecisionHitDetailLogMapper hitDetailLogMapper;

    @Test
    void shouldExecuteDemoTradeRiskDecisionWithAviator() {
        DecisionResponse response = runtimeService.execute(new ExecuteDecisionRequest(
                "trade_risk",
                "TEST-EVT-001",
                "10001",
                Map.of("orderAmount", 12000, "city", "重庆")
        ));

        assertThat(response.decisionResult()).isEqualTo("REVIEW");
        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.hitRules()).extracting("ruleCode").contains("RISK_001", "RISK_002");
        assertThat(response.path()).contains("FLOW:trade_risk_flow", "RULE_SET:trade_risk_default_set");
        assertThat(response.conditionTraces()).isNotEmpty();
        assertThat(hitDetailLogMapper.selectCount(null)).isGreaterThan(0);
    }

    @Test
    void shouldResolveHareMockBeforeExpressionExecution() {
        DecisionRule rule = domainService.saveRule(null, new RuleRequest(
                "trade_risk",
                "HARE_RULE_" + System.nanoTime(),
                "Hare 风险规则",
                10,
                "AVIATOR",
                "BOOLEAN",
                0,
                "orderAmount > 10000 && ${tyc:企业风险信息:$.data.riskCount} > 0",
                Map.of(),
                Map.of("decisionResult", "REJECT", "riskLevel", "HIGH", "score", 90, "reason", "外部风险命中"),
                Map.of(),
                "已发布",
                "tester",
                null
        ));

        DecisionResponse response = runtimeService.testRule(new RuleTestRequest(
                rule.getCode(),
                Map.of("orderAmount", 20000, "mockRiskCount", 2)
        ));

        assertThat(response.decisionResult()).isEqualTo("REJECT");
        assertThat(response.conditionTraces()).extracting("expression").anyMatch(item -> String.valueOf(item).contains("tycValue"));
    }

    @Test
    void shouldApplyAllRuleSetOnlyWhenEveryRuleMatches() {
        String suffix = String.valueOf(System.nanoTime());
        domainService.saveScene(null, new SceneRequest(
                "all_scene_" + suffix,
                "ALL 场景",
                "测试",
                "已发布",
                List.of(Map.of("field", "amount", "type", "NUMBER"), Map.of("field", "city", "type", "STRING")),
                List.of(Map.of("field", "decisionResult", "type", "STRING")),
                "tester",
                null
        ));
        domainService.saveRule(null, new RuleRequest("all_scene_" + suffix, "ALL_A_" + suffix, "金额规则", 1, "MIXED", "BOOLEAN", 0, "",
                Map.of("field", "amount", "operator", ">", "value", 100), Map.of("decisionResult", "REVIEW", "score", 30), Map.of(), "已发布", "tester", null));
        domainService.saveRule(null, new RuleRequest("all_scene_" + suffix, "ALL_B_" + suffix, "城市规则", 1, "MIXED", "BOOLEAN", 0, "",
                Map.of("field", "city", "operator", "=", "value", "重庆"), Map.of("decisionResult", "REJECT", "score", 30), Map.of(), "已发布", "tester", null));
        DecisionRuleSet set = domainService.saveRuleSet(null, new RuleSetRequest("all_scene_" + suffix, "ALL_SET_" + suffix, "ALL规则集", "ALL", 0, false,
                List.of("ALL_A_" + suffix, "ALL_B_" + suffix), "已发布", null));
        domainService.saveFlow(null, new FlowRequest("all_scene_" + suffix, "ALL_FLOW_" + suffix, "ALL流", "已发布",
                List.of(
                        Map.of("id", "start", "type", "START", "code", "start", "sort", 1),
                        Map.of("id", "set", "type", "RULE_SET", "code", set.getCode(), "sort", 2),
                        Map.of("id", "end", "type", "END", "code", "end", "sort", 3)
                ),
                List.of(Map.of("source", "start", "target", "set"), Map.of("source", "set", "target", "end")),
                null));

        DecisionResponse miss = runtimeService.execute(new ExecuteDecisionRequest("all_scene_" + suffix, "E1", "u", Map.of("amount", 200, "city", "北京")));
        DecisionResponse hit = runtimeService.execute(new ExecuteDecisionRequest("all_scene_" + suffix, "E2", "u", Map.of("amount", 200, "city", "重庆")));

        assertThat(miss.decisionResult()).isEqualTo("PASS");
        assertThat(hit.decisionResult()).isEqualTo("REJECT");
    }

    @Test
    void shouldRequireWorkflowModelBeforeSubmitPublishRequest() {
        var request = domainService.createPublishRequest(new PublishRequestForm("RULE", 1L, "RISK_001", null, "tester", "测试发布"));

        assertThatThrownBy(() -> domainService.submitPublishRequest(request.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workflow-model-id");
    }
}
