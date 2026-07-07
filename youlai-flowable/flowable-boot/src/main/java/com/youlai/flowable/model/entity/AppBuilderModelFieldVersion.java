package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_model_field_version")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderModelFieldVersion extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private Integer versionNo;

    private String fieldsSnapshotJson;

    private String remark;
}
