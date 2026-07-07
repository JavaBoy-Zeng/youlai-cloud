package com.youlai.flowable.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("wf_model")
@EqualsAndHashCode(callSuper = true)
public class WfModel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String modelKey;

    private String name;

    private Integer version;

    private String status;

    private String formKey;

    private String bpmnXml;

    private String configJson;

    private String deploymentId;

    private String processDefinitionId;

    private String remark;
}
