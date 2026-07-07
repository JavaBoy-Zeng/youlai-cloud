package com.youlai.decision.controller;

import com.youlai.common.result.Result;
import com.youlai.decision.model.DomainApiModels.*;
import com.youlai.decision.service.DecisionDomainService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decision-engine")
public class DecisionDomainController {

    private final DecisionDomainService domainService;

    public DecisionDomainController(DecisionDomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping("/scenes")
    public Result<?> scenes() {
        return Result.success(domainService.scenes());
    }

    @PostMapping("/scenes")
    public Result<?> createScene(@RequestBody SceneRequest request) {
        return Result.success(domainService.saveScene(null, request));
    }

    @PutMapping("/scenes/{id}")
    public Result<?> updateScene(@PathVariable Long id, @RequestBody SceneRequest request) {
        return Result.success(domainService.saveScene(id, request));
    }

    @GetMapping("/variables")
    public Result<?> variables(@RequestParam(required = false) String sceneCode) {
        return Result.success(domainService.variables(sceneCode));
    }

    @PostMapping("/variables")
    public Result<?> createVariable(@RequestBody VariableRequest request) {
        return Result.success(domainService.saveVariable(null, request));
    }

    @PutMapping("/variables/{id}")
    public Result<?> updateVariable(@PathVariable Long id, @RequestBody VariableRequest request) {
        return Result.success(domainService.saveVariable(id, request));
    }

    @GetMapping("/rules")
    public Result<?> rules(@RequestParam(required = false) String sceneCode) {
        return Result.success(domainService.rules(sceneCode));
    }

    @PostMapping("/rules")
    public Result<?> createRule(@RequestBody RuleRequest request) {
        return Result.success(domainService.saveRule(null, request));
    }

    @PutMapping("/rules/{id}")
    public Result<?> updateRule(@PathVariable Long id, @RequestBody RuleRequest request) {
        return Result.success(domainService.saveRule(id, request));
    }

    @GetMapping("/rule-sets")
    public Result<?> ruleSets(@RequestParam(required = false) String sceneCode) {
        return Result.success(domainService.ruleSets(sceneCode));
    }

    @PostMapping("/rule-sets")
    public Result<?> createRuleSet(@RequestBody RuleSetRequest request) {
        return Result.success(domainService.saveRuleSet(null, request));
    }

    @PutMapping("/rule-sets/{id}")
    public Result<?> updateRuleSet(@PathVariable Long id, @RequestBody RuleSetRequest request) {
        return Result.success(domainService.saveRuleSet(id, request));
    }

    @GetMapping("/flows")
    public Result<?> flows(@RequestParam(required = false) String sceneCode) {
        return Result.success(domainService.flows(sceneCode));
    }

    @PostMapping("/flows")
    public Result<?> createFlow(@RequestBody FlowRequest request) {
        return Result.success(domainService.saveFlow(null, request));
    }

    @PutMapping("/flows/{id}")
    public Result<?> updateFlow(@PathVariable Long id, @RequestBody FlowRequest request) {
        return Result.success(domainService.saveFlow(id, request));
    }

    @GetMapping("/{type:data-sources|models|score-cards|decision-tables}")
    public Result<?> advanced(@PathVariable String type) {
        return Result.success(domainService.advanced(type));
    }

    @PostMapping("/{type:data-sources|models|score-cards|decision-tables}")
    public Result<?> createAdvanced(@PathVariable String type, @RequestBody AdvancedAssetRequest request) {
        return Result.success(domainService.saveAdvanced(type, null, request));
    }

    @PutMapping("/{type:data-sources|models|score-cards|decision-tables}/{id}")
    public Result<?> updateAdvanced(@PathVariable String type, @PathVariable Long id, @RequestBody AdvancedAssetRequest request) {
        return Result.success(domainService.saveAdvanced(type, id, request));
    }

    @GetMapping("/versions/{targetType}/{targetId}")
    public Result<?> versions(@PathVariable String targetType, @PathVariable Long targetId) {
        return Result.success(domainService.versions(targetType, targetId));
    }

    @PostMapping("/versions/{versionId}/rollback")
    public Result<?> rollback(@PathVariable Long versionId) {
        return Result.success(domainService.rollback(versionId));
    }

    @GetMapping("/publish-requests")
    public Result<?> publishRequests() {
        return Result.success(domainService.publishRequests());
    }

    @PostMapping("/publish-requests")
    public Result<?> createPublishRequest(@RequestBody PublishRequestForm request) {
        return Result.success(domainService.createPublishRequest(request));
    }

    @PostMapping("/publish-requests/{id}/submit")
    public Result<?> submitPublishRequest(@PathVariable Long id) {
        return Result.success(domainService.submitPublishRequest(id));
    }

    @PostMapping("/publish-requests/{id}/refresh")
    public Result<?> refreshPublishRequest(@PathVariable Long id) {
        return Result.success(domainService.refreshPublishRequest(id));
    }

    @GetMapping("/gray-policies")
    public Result<?> grayPolicies() {
        return Result.success(domainService.grayPolicies());
    }

    @PostMapping("/gray-policies")
    public Result<?> createGrayPolicy(@RequestBody GrayPolicyRequest request) {
        return Result.success(domainService.saveGrayPolicy(null, request));
    }

    @PutMapping("/gray-policies/{id}")
    public Result<?> updateGrayPolicy(@PathVariable Long id, @RequestBody GrayPolicyRequest request) {
        return Result.success(domainService.saveGrayPolicy(id, request));
    }

    @GetMapping("/simulation/jobs")
    public Result<?> simulationJobs() {
        return Result.success(domainService.simulationJobs());
    }

    @PostMapping("/simulation/jobs")
    public Result<?> createSimulationJob(@RequestBody SimulationJobRequest request) {
        return Result.success(domainService.saveSimulationJob(request));
    }
}
