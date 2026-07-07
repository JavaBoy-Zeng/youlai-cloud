package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_form")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderForm extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private Long modelId;

    private String formKey;

    private String formName;

    private String formSchema;

    private String status;

    private Integer version;

    private String remark;

    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
