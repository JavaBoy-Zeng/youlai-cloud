package com.youlai.decision.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.DecisionDomainPublishRecordMapper;
import com.youlai.decision.mapper.DecisionExecuteLogMapper;
import com.youlai.decision.mapper.DecisionRuleMapper;
import com.youlai.decision.mapper.DecisionRuleSetMapper;
import com.youlai.decision.mapper.DecisionSceneMapper;
import com.youlai.decision.model.DecisionExecuteLog;
import com.youlai.decision.model.DecisionDomainPublishRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final DecisionSceneMapper sceneMapper;
    private final DecisionRuleMapper ruleMapper;
    private final DecisionRuleSetMapper ruleSetMapper;
    private final DecisionExecuteLogMapper executeLogMapper;
    private final DecisionDomainPublishRecordMapper publishRecordMapper;
    private final JsonService jsonService;

    /**
     * 创建监控工作台服务。
     *
     * @param artifactMapper 策略资产 Mapper
     * @param executeLogMapper 执行日志 Mapper
     * @param publishRecordMapper 发布记录 Mapper
     * @param jsonService JSON 服务
     */
    public DashboardService(
            DecisionSceneMapper sceneMapper,
            DecisionRuleMapper ruleMapper,
            DecisionRuleSetMapper ruleSetMapper,
            DecisionExecuteLogMapper executeLogMapper,
            DecisionDomainPublishRecordMapper publishRecordMapper,
            JsonService jsonService
    ) {
        this.sceneMapper = sceneMapper;
        this.ruleMapper = ruleMapper;
        this.ruleSetMapper = ruleSetMapper;
        this.executeLogMapper = executeLogMapper;
        this.publishRecordMapper = publishRecordMapper;
        this.jsonService = jsonService;
    }

    /**
     * 汇总工作台指标，包括调用量、成功率、耗时、结果分布和命中排行。
     *
     * @return 工作台概览数据
     */
    public Map<String, Object> overview() {
        List<DecisionExecuteLog> logs = recentExecuteLogs();
        long total = executeLogMapper.selectCount(null);
        long success = logs.stream().filter(DecisionExecuteLog::isSuccess).count();
        double avgElapsed = logs.stream()
                .map(DecisionExecuteLog::getElapsedMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        Map<String, Long> resultDistribution = logs.stream()
                .collect(Collectors.groupingBy(log -> Objects.toString(log.getDecisionResult(), "UNKNOWN"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> hitRanking = new LinkedHashMap<>();
        for (DecisionExecuteLog log : logs) {
            Object parsed = jsonService.readMap("{\"items\":" + Optional.ofNullable(log.getHitRulesJson()).orElse("[]") + "}").get("items");
            if (parsed instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> map) {
                        String code = Objects.toString(map.get("ruleCode"), "");
                        if (!code.isBlank()) {
                            hitRanking.put(code, hitRanking.getOrDefault(code, 0L) + 1);
                        }
                    }
                }
            }
        }
        return Map.of(
                "artifactCount", sceneMapper.selectCount(null) + ruleMapper.selectCount(null) + ruleSetMapper.selectCount(null),
                "totalCalls", total,
                "recentSuccessRate", logs.isEmpty() ? 1 : success * 1.0 / logs.size(),
                "avgElapsedMs", Math.round(avgElapsed),
                "exceptionCalls", executeLogMapper.selectCount(Wrappers.lambdaQuery(DecisionExecuteLog.class)
                        .eq(DecisionExecuteLog::isSuccess, false)),
                "resultDistribution", resultDistribution,
                "hitRanking", hitRanking.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(10)
                        .map(entry -> Map.of("ruleCode", entry.getKey(), "hitCount", entry.getValue()))
                        .toList(),
                "recentPublishes", publishRecordMapper.selectList(Wrappers.lambdaQuery(DecisionDomainPublishRecord.class)
                        .orderByDesc(DecisionDomainPublishRecord::getCreateTime)
                        .last("LIMIT 20"))
        );
    }

    private List<DecisionExecuteLog> recentExecuteLogs() {
        return executeLogMapper.selectList(Wrappers.lambdaQuery(DecisionExecuteLog.class)
                .orderByDesc(DecisionExecuteLog::getCreateTime)
                .last("LIMIT 100"));
    }
}
