package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作审计日志实体，用于记录策略资产新增、修改、发布、删除和回滚等管理动作。
 */
@Getter
@Setter
@TableName("decision_audit_log")
public class AuditLog extends BaseEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人。 */
    private String operator;

    /** 操作类型。 */
    private String action;

    /** 操作对象类型。 */
    private String targetKind;

    /** 操作对象编码。 */
    private String targetCode;

    /** 操作详情 JSON。 */
    private String detailJson;
}
