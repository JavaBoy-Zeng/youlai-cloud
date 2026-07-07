package com.youlai.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youlai.decision.mapper.DecisionArtifactMapper;
import com.youlai.decision.mapper.DecisionVersionMapper;
import com.youlai.decision.mapper.PublishRecordMapper;
import com.youlai.decision.model.*;
import com.youlai.decision.model.ApiModels.ArtifactRequest;
import com.youlai.decision.model.ApiModels.ArtifactResponse;
import com.youlai.decision.model.ApiModels.PublishRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class ArtifactService {

    private final DecisionArtifactMapper artifactMapper;
    private final DecisionVersionMapper versionMapper;
    private final PublishRecordMapper publishRecordMapper;
    private final JsonService jsonService;
    private final AuditService auditService;
    private final ArtifactValidator artifactValidator;

    /**
     * 创建策略资产管理服务。
     *
     * @param artifactMapper 策略资产 Mapper
     * @param versionMapper 版本快照 Mapper
     * @param publishRecordMapper 发布记录 Mapper
     * @param jsonService JSON 处理服务
     * @param auditService 审计服务
     */
    public ArtifactService(
            DecisionArtifactMapper artifactMapper,
            DecisionVersionMapper versionMapper,
            PublishRecordMapper publishRecordMapper,
            JsonService jsonService,
            AuditService auditService,
            ArtifactValidator artifactValidator
    ) {
        this.artifactMapper = artifactMapper;
        this.versionMapper = versionMapper;
        this.publishRecordMapper = publishRecordMapper;
        this.jsonService = jsonService;
        this.auditService = auditService;
        this.artifactValidator = artifactValidator;
    }

    /**
     * 按资产类型、关键字和状态查询资产列表。
     *
     * @param kind 资产类型
     * @param keywords 名称、编码或标签关键字
     * @param status 资产状态
     * @return 资产响应列表
     */
    public List<ArtifactResponse> list(ArtifactKind kind, String keywords, String status) {
        return findByKind(kind).stream()
                .filter(item -> !StringUtils.hasText(status)
                        || DecisionStatus.normalize(status).equals(DecisionStatus.normalize(item.getStatus())))
                .filter(item -> !StringUtils.hasText(keywords)
                        || item.getCode().contains(keywords)
                        || item.getName().contains(keywords)
                        || String.valueOf(item.getTags()).contains(keywords))
                .map(this::toResponse)
                .toList();
    }

    /**
     * 获取单个资产详情。
     *
     * @param id 资产 ID
     * @return 资产响应
     */
    public ArtifactResponse get(Long id) {
        return toResponse(requireArtifact(id));
    }

    /**
     * 按类型和编码查找资产。
     *
     * @param kind 资产类型
     * @param code 资产编码
     * @return 资产实体可选值
     */
    public Optional<DecisionArtifact> find(ArtifactKind kind, String code) {
        return Optional.ofNullable(findByKindAndCode(kind, code));
    }

    /**
     * 新增策略资产并创建初始版本。
     *
     * @param kind 资产类型
     * @param request 新增请求
     * @return 新增后的资产响应
     */
    @Transactional
    public ArtifactResponse save(ArtifactKind kind, ArtifactRequest request) {
        validateRequest(request);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setKind(kind);
        artifact.setCode(request.code().trim());
        applyRequest(artifact, request, false);
        artifactMapper.insert(artifact);
        snapshot(artifact, "初始版本");
        auditService.record("CREATE", kind, artifact.getCode(), toResponse(artifact));
        return toResponse(artifact);
    }

    /**
     * 更新策略资产并生成新版本快照。
     *
     * @param id 资产 ID
     * @param request 更新请求
     * @return 更新后的资产响应
     */
    @Transactional
    public ArtifactResponse update(Long id, ArtifactRequest request) {
        validateRequest(request);
        DecisionArtifact artifact = requireArtifact(id);
        if (existsByKindAndCodeAndIdNot(artifact.getKind(), request.code().trim(), id)) {
            throw new IllegalArgumentException("编码已存在: " + request.code());
        }
        artifact.setCode(request.code().trim());
        artifact.setVersionNo(artifact.getVersionNo() + 1);
        applyRequest(artifact, request, true);
        artifactMapper.updateById(artifact);
        snapshot(artifact, request.remark() == null ? "更新版本" : request.remark());
        auditService.record("UPDATE", artifact.getKind(), artifact.getCode(), toResponse(artifact));
        return toResponse(artifact);
    }

    /**
     * 复制策略资产为新的草稿资产。
     *
     * @param id 源资产 ID
     * @return 复制后的资产响应
     */
    @Transactional
    public ArtifactResponse copy(Long id) {
        DecisionArtifact source = requireArtifact(id);
        DecisionArtifact target = new DecisionArtifact();
        target.setKind(source.getKind());
        target.setCode(source.getCode() + "_copy_" + System.currentTimeMillis());
        target.setName(source.getName() + "副本");
        target.setCategory(source.getCategory());
        target.setStatus(DecisionStatus.DRAFT);
        target.setTags(source.getTags());
        target.setOwner(source.getOwner());
        target.setVersionNo(1);
        target.setContentJson(source.getContentJson());
        target.setRemark(source.getRemark());
        artifactMapper.insert(target);
        snapshot(target, "复制生成");
        auditService.record("COPY", target.getKind(), target.getCode(), Map.of("sourceId", id, "targetId", target.getId()));
        return toResponse(target);
    }

    /**
     * 发布策略资产并记录发布流水。
     *
     * @param id 资产 ID
     * @param request 发布请求
     * @return 发布记录
     */
    @Transactional
    public PublishRecord publish(Long id, PublishRequest request) {
        DecisionArtifact artifact = requireArtifact(id);
        artifactValidator.validateBeforePublish(artifact);
        artifact.setStatus(DecisionStatus.PUBLISHED);
        artifact.setVersionNo(artifact.getVersionNo() + 1);
        artifactMapper.updateById(artifact);
        snapshot(artifact, request == null ? "发布快照" : request.remark());

        PublishRecord record = new PublishRecord();
        record.setArtifactId(artifact.getId());
        record.setKind(artifact.getKind());
        record.setCode(artifact.getCode());
        record.setVersionNo(artifact.getVersionNo());
        record.setEnvironment(request == null || request.environment() == null ? "PROD" : request.environment());
        record.setPublishBy(request == null || request.publishBy() == null ? "system" : request.publishBy());
        record.setRemark(request == null ? "发布" : request.remark());
        publishRecordMapper.insert(record);
        auditService.record("PUBLISH", artifact.getKind(), artifact.getCode(), record);
        return record;
    }

    /**
     * 将资产回滚到指定版本快照并生成新的草稿版本。
     *
     * @param versionId 版本快照 ID
     * @return 回滚后的资产响应
     */
    @Transactional
    public ArtifactResponse rollback(Long versionId) {
        DecisionVersion version = Optional.ofNullable(versionMapper.selectById(versionId))
                .orElseThrow(() -> new NoSuchElementException("版本不存在"));
        DecisionArtifact artifact = requireArtifact(version.getArtifactId());
        Map<String, Object> snapshot = jsonService.readMap(version.getSnapshotJson());
        ArtifactRequest request = new ArtifactRequest(
                Objects.toString(snapshot.get("code"), artifact.getCode()),
                Objects.toString(snapshot.get("name"), artifact.getName()),
                Objects.toString(snapshot.get("category"), null),
                DecisionStatus.DRAFT,
                Objects.toString(snapshot.get("tags"), null),
                Objects.toString(snapshot.get("owner"), null),
                "回滚到 v" + version.getVersionNo(),
                castMap(snapshot.get("content"))
        );
        artifact.setVersionNo(artifact.getVersionNo() + 1);
        applyRequest(artifact, request, true);
        artifactMapper.updateById(artifact);
        snapshot(artifact, request.remark());
        auditService.record("ROLLBACK", artifact.getKind(), artifact.getCode(), Map.of("versionId", versionId));
        return toResponse(artifact);
    }

    /**
     * 查询资产的历史版本。
     *
     * @param artifactId 资产 ID
     * @return 版本快照列表
     */
    public List<DecisionVersion> versions(Long artifactId) {
        return versionMapper.selectList(Wrappers.lambdaQuery(DecisionVersion.class)
                .eq(DecisionVersion::getArtifactId, artifactId)
                .orderByDesc(DecisionVersion::getVersionNo));
    }

    /**
     * 删除策略资产并写入审计日志。
     *
     * @param id 资产 ID
     */
    public void delete(Long id) {
        DecisionArtifact artifact = requireArtifact(id);
        artifactMapper.deleteById(id);
        auditService.record("DELETE", artifact.getKind(), artifact.getCode(), Map.of("id", id));
    }

    /**
     * 将资产实体转换为前端友好的响应结构。
     *
     * @param artifact 资产实体
     * @return 资产响应
     */
    public ArtifactResponse toResponse(DecisionArtifact artifact) {
        return new ArtifactResponse(
                artifact.getId(),
                artifact.getKind(),
                artifact.getCode(),
                artifact.getName(),
                artifact.getCategory(),
                DecisionStatus.normalize(artifact.getStatus()),
                artifact.getTags(),
                artifact.getOwner(),
                artifact.getVersionNo(),
                jsonService.readMap(artifact.getContentJson()),
                artifact.getRemark(),
                artifact.getCreateTime(),
                artifact.getUpdateTime()
        );
    }

    /**
     * 根据 ID 查询资产，不存在时抛出异常。
     *
     * @param id 资产 ID
     * @return 资产实体
     */
    private DecisionArtifact requireArtifact(Long id) {
        return Optional.ofNullable(artifactMapper.selectById(id))
                .orElseThrow(() -> new NoSuchElementException("资产不存在"));
    }

    /**
     * 将请求字段应用到资产实体。
     *
     * @param artifact 资产实体
     * @param request 请求对象
     * @param keepKind 是否保持已有资产类型
     */
    private void applyRequest(DecisionArtifact artifact, ArtifactRequest request, boolean keepKind) {
        if (!keepKind && findByKindAndCode(artifact.getKind(), request.code().trim()) != null) {
            throw new IllegalArgumentException("编码已存在: " + request.code());
        }
        artifact.setName(request.name().trim());
        artifact.setCategory(request.category());
        artifact.setStatus(DecisionStatus.normalize(request.status()));
        artifact.setTags(request.tags());
        artifact.setOwner(request.owner());
        artifact.setRemark(request.remark());
        artifact.setContentJson(jsonService.write(request.content() == null ? Map.of() : request.content()));
    }

    /**
     * 校验资产新增或更新请求。
     *
     * @param request 请求对象
     */
    private void validateRequest(ArtifactRequest request) {
        if (request == null || !StringUtils.hasText(request.code())) {
            throw new IllegalArgumentException("编码不能为空");
        }
        if (!StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("名称不能为空");
        }
    }

    /**
     * 创建资产版本快照。
     *
     * @param artifact 资产实体
     * @param remark 快照备注
     */
    private void snapshot(DecisionArtifact artifact, String remark) {
        DecisionVersion version = new DecisionVersion();
        version.setArtifactId(artifact.getId());
        version.setKind(artifact.getKind());
        version.setCode(artifact.getCode());
        version.setVersionNo(artifact.getVersionNo());
        version.setRemark(remark);
        version.setSnapshotJson(jsonService.write(toResponse(artifact)));
        versionMapper.insert(version);
    }

    private DecisionArtifact findByKindAndCode(ArtifactKind kind, String code) {
        return artifactMapper.selectOne(Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind)
                .eq(DecisionArtifact::getCode, code)
                .last("LIMIT 1"));
    }

    private boolean existsByKindAndCodeAndIdNot(ArtifactKind kind, String code, Long id) {
        Long count = artifactMapper.selectCount(Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind)
                .eq(DecisionArtifact::getCode, code)
                .ne(DecisionArtifact::getId, id));
        return count != null && count > 0;
    }

    private List<DecisionArtifact> findByKind(ArtifactKind kind) {
        LambdaQueryWrapper<DecisionArtifact> wrapper = Wrappers.lambdaQuery(DecisionArtifact.class)
                .eq(DecisionArtifact::getKind, kind)
                .orderByDesc(DecisionArtifact::getUpdateTime)
                .orderByDesc(DecisionArtifact::getId);
        return artifactMapper.selectList(wrapper);
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
        return new LinkedHashMap<>();
    }
}
