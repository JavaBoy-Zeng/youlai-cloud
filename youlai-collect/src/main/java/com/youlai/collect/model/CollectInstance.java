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
@TableName("collect_instance")
public class CollectInstance extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long jobLogId;
    private String traceId;
    private String mqMessageId;
    private String triggerType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalCount;
    private Integer validCount;
    private Integer invalidCount;
    private Integer duplicateCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer failedCount;
    private String errorMessage;
}
