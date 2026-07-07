package com.youlai.decision.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("decision_publish_request")
public class DecisionPublishRequest extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;
    private Long targetId;
    private String code;
    private Integer versionNo;
    private String status = "DRAFT";
    private String workflowBusinessKey;
    private String processInstanceId;
    private Long workflowModelId;
    private String applicant;
    private String remark;
}
