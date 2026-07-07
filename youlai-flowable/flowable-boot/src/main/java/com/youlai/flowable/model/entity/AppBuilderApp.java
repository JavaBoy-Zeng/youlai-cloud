package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_app")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderApp extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String appCode;

    private String appName;

    private String appDesc;

    private String appIcon;

    private String category;

    private String status;

    private String remark;
}
