package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_api_log")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderApiLog extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long apiId;
    private String requestJson;
    private String responseText;
    private Integer statusCode;
    private Long durationMs;
    private Integer success;
    private String errorMsg;
}
