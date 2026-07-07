package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_model_field")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderModelField extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private String fieldCode;

    private String fieldName;

    private String fieldType;

    private String dbType;

    private Integer required;

    private String defaultValue;

    private String optionsJson;

    private String validateJson;

    private Integer sortOrder;
}
