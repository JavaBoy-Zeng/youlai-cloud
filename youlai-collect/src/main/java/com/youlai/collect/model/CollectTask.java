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
@TableName("collect_task")
public class CollectTask extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String taskCode;
    private Long modelId;
    private Long apiId;
    private String scheduleType;
    private String cronExpr;
    private Long jobId;
    private String collectMode;
    private LocalDateTime lastSuccessTime;
    private String lastCursor;
    private String insertStrategy;
    private Integer maxFetchCount;
    private String mappingJson;
    private String transformJson;
    private String status;
    private String remark;
}
