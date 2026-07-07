package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("wf_task_record")
@EqualsAndHashCode(callSuper = true)
public class WfTaskRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String processInstanceId;

    private String taskName;

    private Long operatorId;

    private String operatorUsername;

    private String action;

    private String comment;

    private String attachmentJson;
}
