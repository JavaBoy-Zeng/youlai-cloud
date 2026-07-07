package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 决策资产版本实体，用于保存每次新增、修改、发布和回滚时的配置快照。
 */
@Getter
@Setter
@TableName("decision_version")
public class DecisionVersion extends BaseEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应的决策资产 ID。 */
    private Long artifactId;

    /** 资产类型。 */
    private ArtifactKind kind;

    /** 资产编码。 */
    private String code;

    /** 快照版本号。 */
    private Integer versionNo;

    /** 完整资产快照 JSON。 */
    private String snapshotJson;

    /** 版本备注。 */
    private String remark;
}
