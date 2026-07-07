package com.youlai.decision.controller;

import com.youlai.common.result.Result;
import com.youlai.decision.engine.DecisionRuntimeService;
import com.youlai.decision.model.DomainApiModels.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 决策执行接口，提供实时决策、规则测试和决策流测试能力。
 */
@RestController
@RequestMapping("/api/v1/decision-engine")
public class DecisionController {

    /** 决策执行服务。 */
    private final DecisionRuntimeService decisionRuntimeService;

    /**
     * 创建决策执行控制器。
     *
     * @param decisionEngineService 决策执行服务
     */
    public DecisionController(DecisionRuntimeService decisionRuntimeService) {
        this.decisionRuntimeService = decisionRuntimeService;
    }

    /**
     * 执行实时决策。
     *
     * @param request 决策请求
     * @return 决策结果
     */
    @PostMapping("/decision/execute")
    public Result<DecisionResponse> execute(@Valid @RequestBody ExecuteDecisionRequest request) {
        return Result.success(decisionRuntimeService.execute(request));
    }

    /**
     * 测试单条规则。
     *
     * @param request 规则测试请求
     * @return 测试结果
     */
    @PostMapping("/rules/{code}/test")
    public Result<DecisionResponse> testRule(@PathVariable String code, @RequestBody(required = false) RuleTestRequest request) {
        RuleTestRequest actual = request == null ? new RuleTestRequest(code, null) : new RuleTestRequest(code, request.params());
        return Result.success(decisionRuntimeService.testRule(actual));
    }

    /**
     * 测试指定决策流。
     *
     * @param request 决策流测试请求
     * @return 测试结果
     */
    @PostMapping("/flows/{code}/test")
    public Result<DecisionResponse> testFlow(@PathVariable String code, @RequestBody(required = false) FlowTestRequest request) {
        FlowTestRequest actual = request == null
                ? new FlowTestRequest(code, null, null, null)
                : new FlowTestRequest(code, request.eventId(), request.userId(), request.params());
        return Result.success(decisionRuntimeService.testFlow(actual));
    }
}
