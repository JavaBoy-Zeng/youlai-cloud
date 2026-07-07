package com.youlai.flowable.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfTaskVO {

    private String taskId;

    private String taskName;

    private String taskDefinitionKey;

    private String processInstanceId;

    private String processDefinitionId;

    private String assignee;

    private String businessKey;

    private String processName;

    private String starterUsername;

    private LocalDateTime createTime;

    private LocalDateTime endTime;
}
