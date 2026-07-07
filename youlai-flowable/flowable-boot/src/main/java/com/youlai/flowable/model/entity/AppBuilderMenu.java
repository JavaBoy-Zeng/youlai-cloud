package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_menu")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderMenu extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private Long pageId;

    private String menuName;

    private String routePath;

    private String routeName;

    private String component;

    private String perm;

    private String icon;

    private Integer visible;

    private Integer sortOrder;

    private String status;

    private String remark;
}
