package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_model")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderModel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private String modelCode;

    private String modelName;

    private String tableName;

    private String mainField;

    private Integer enableFlow;

    private String formKey;

    private String processKey;

    private String status;

    private String remark;
}
