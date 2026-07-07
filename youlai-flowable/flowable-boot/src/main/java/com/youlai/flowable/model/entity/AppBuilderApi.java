package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_api")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderApi extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String apiName;
    private String apiCode;
    private String method;
    private String url;
    private String headersJson;
    private String paramsJson;
    private String bodyTemplate;
    private Integer retryTimes;
    private Integer timeoutMs;
    private String status;
    private String remark;
}
