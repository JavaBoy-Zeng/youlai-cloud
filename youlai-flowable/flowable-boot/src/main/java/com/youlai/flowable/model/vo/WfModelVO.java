package com.youlai.flowable.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfModelVO {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
