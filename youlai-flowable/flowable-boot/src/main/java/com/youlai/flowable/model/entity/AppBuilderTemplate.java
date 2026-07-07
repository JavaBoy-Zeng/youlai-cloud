package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_template")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderTemplate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String templateCode;
    private String category;
    private String coverUrl;
    private String configJson;
    private String status;
    private String remark;
}
