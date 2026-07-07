package com.youlai.flowable.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfTaskRecordVO {

    private String taskId;

    private String taskName;

    private Long operatorId;

    private String operatorUsername;

    private String action;

    private String comment;

    private String attachmentJson;

    private LocalDateTime createTime;
}
