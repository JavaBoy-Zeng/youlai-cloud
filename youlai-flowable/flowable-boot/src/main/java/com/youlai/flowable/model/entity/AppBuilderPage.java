package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_page")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderPage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private Long modelId;

    private String pageType;

    private String pageName;

    private String pageSchema;

    private String status;

    private String remark;
}
