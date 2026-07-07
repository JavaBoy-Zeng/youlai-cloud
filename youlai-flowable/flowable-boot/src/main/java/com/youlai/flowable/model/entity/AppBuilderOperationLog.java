package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_operation_log")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderOperationLog extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String moduleName;
    private String operationType;
    private String operator;
    private String contentJson;
    private Integer success;
    private String remark;
}
