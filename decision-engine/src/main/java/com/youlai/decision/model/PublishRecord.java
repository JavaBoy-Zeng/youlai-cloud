package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布记录实体，用于记录策略资产从草稿到指定环境的发布动作。
 */
@Getter
@Setter
@TableName("decision_publish_record")
public class PublishRecord extends BaseEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被发布的资产 ID。 */
    private Long artifactId;

    /** 被发布的资产类型。 */
    private ArtifactKind kind;

    /** 被发布的资产编码。 */
    private String code;

    /** 发布时的资产版本号。 */
    private Integer versionNo;

    /** 发布环境，如 DEV、TEST、STAGING、PROD。 */
    private String environment = "PROD";

    /** 发布状态。 */
    private String status = DecisionStatus.PUBLISHED;

    /** 发布操作人。 */
    private String publishBy;

    /** 发布说明。 */
    private String remark;
}
