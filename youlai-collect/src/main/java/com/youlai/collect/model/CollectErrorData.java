package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_error_data")
public class CollectErrorData extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long instanceId;
    private String traceId;
    private Integer dataIndex;
    private String errorType;
    private String errorMessage;
    private String rawBody;
    private String transformedBody;
}
