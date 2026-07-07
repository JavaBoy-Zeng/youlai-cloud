package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 决策资产实体，用于统一承载场景、变量、规则、规则集、决策流、模型等可配置对象。
 */
@Getter
@Setter
@TableName("decision_artifact")
public class DecisionArtifact extends BaseEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产类型。 */
    @NotNull
    private ArtifactKind kind;

    /** 资产编码，同一类型下唯一。 */
    @NotBlank
    private String code;

    /** 资产名称。 */
    @NotBlank
    private String name;

    /** 业务分类，如风控、审批、营销。 */
    private String category;

    /** 资产状态，如 草稿、已发布、已启用、已停用。 */
    private String status = DecisionStatus.DRAFT;

    /** 标签集合，使用逗号分隔，便于简单检索。 */
    private String tags;

    /** 负责人或归属团队。 */
    private String owner;

    /** 当前版本号。 */
    private Integer versionNo = 1;

    /** 资产配置内容，按资产类型存放不同 JSON 结构。 */
    private String contentJson = "{}";

    /** 备注说明。 */
    private String remark;
}
