package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("wf_instance")
@EqualsAndHashCode(callSuper = true)
public class WfInstance extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String processInstanceId;

    private String processDefinitionId;

    private String businessKey;

    private Long modelId;

    private String modelKey;

    private String modelName;

    private Long starterId;

    private String starterUsername;

    private String status;

    private String formKey;

    private String formDataJson;

    private String currentNodeName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
