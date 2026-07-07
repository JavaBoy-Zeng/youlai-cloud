package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_quality_report")
public class CollectQualityReport extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long instanceId;
    private String traceId;
    private Integer totalCount;
    private Integer validCount;
    private Integer invalidCount;
    private Integer duplicateCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer failedCount;
    private String fieldCompletenessJson;
    private String summaryJson;
}
