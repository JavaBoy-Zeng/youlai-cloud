package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_task_message")
public class CollectTaskMessage extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long instanceId;
    private String traceId;
    private String mqTopic;
    private String mqMessageId;
    private String messageBody;
    private String sendStatus;
    private String consumeStatus;
    private LocalDateTime sendTime;
    private LocalDateTime consumeTime;
    private LocalDateTime finishTime;
    private String errorMessage;
}
