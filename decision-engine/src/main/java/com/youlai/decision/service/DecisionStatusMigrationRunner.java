package com.youlai.decision.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.DecisionArtifactMapper;
import com.youlai.decision.mapper.PublishRecordMapper;
import com.youlai.decision.model.DecisionArtifact;
import com.youlai.decision.model.DecisionStatus;
import com.youlai.decision.model.PublishRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动时将历史英文状态迁移为中文状态。
 */
@Component
@Order(0)
public class DecisionStatusMigrationRunner implements ApplicationRunner {

    private static final Map<String, String> STATUS_MAPPING = Map.of(
            "DRAFT", DecisionStatus.DRAFT,
            "ENABLED", DecisionStatus.ENABLED,
            "PUBLISHED", DecisionStatus.PUBLISHED,
            "DISABLED", DecisionStatus.DISABLED
    );

    private final DecisionArtifactMapper artifactMapper;
    private final PublishRecordMapper publishRecordMapper;

    /**
     * 创建状态迁移器。
     *
     * @param artifactMapper 资产 Mapper
     * @param publishRecordMapper 发布记录 Mapper
     */
    public DecisionStatusMigrationRunner(DecisionArtifactMapper artifactMapper, PublishRecordMapper publishRecordMapper) {
        this.artifactMapper = artifactMapper;
        this.publishRecordMapper = publishRecordMapper;
    }

    /**
     * 应用启动后自动修正历史英文状态。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        STATUS_MAPPING.forEach(this::migrateStatus);
    }

    private void migrateStatus(String oldStatus, String newStatus) {
        artifactMapper.update(Wrappers.lambdaUpdate(DecisionArtifact.class)
                .set(DecisionArtifact::getStatus, newStatus)
                .eq(DecisionArtifact::getStatus, oldStatus));
        publishRecordMapper.update(Wrappers.lambdaUpdate(PublishRecord.class)
                .set(PublishRecord::getStatus, newStatus)
                .eq(PublishRecord::getStatus, oldStatus));
    }
}
