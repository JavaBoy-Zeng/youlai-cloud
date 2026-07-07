package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_notification")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderNotification extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private Long automationId;

    private Long receiverId;

    private String receiverUsername;

    private String title;

    private String content;

    private String status;

    private String remark;
}
