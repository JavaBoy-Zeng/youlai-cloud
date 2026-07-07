package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("app_builder_automation")
@EqualsAndHashCode(callSuper = true)
public class AppBuilderAutomation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private Long modelId;
    private String ruleName;
    private String triggerType;
    private String triggerConfigJson;
    private String actionType;
    private String actionConfigJson;
    private String status;
    private String remark;
}
