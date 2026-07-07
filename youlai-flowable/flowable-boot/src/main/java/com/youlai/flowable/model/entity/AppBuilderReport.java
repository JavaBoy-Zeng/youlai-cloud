package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_report")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderReport extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private Long modelId;
    private String reportName;
    private String reportType;
    private String chartType;
    private String dataSourceJson;
    private String chartSchema;
    private String status;
    private String remark;
}
