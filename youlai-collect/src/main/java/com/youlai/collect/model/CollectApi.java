package com.youlai.collect.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_api")
public class CollectApi extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String apiName;
    private String apiCode;
    private String collectType;
    private Long sourceDataSourceId;
    private String sourceName;
    private Integer timeoutSeconds;
    private Integer maxFetchCount;
    private String parseConfig;
    private String configJson;
    private String status;
    private String remark;
}
