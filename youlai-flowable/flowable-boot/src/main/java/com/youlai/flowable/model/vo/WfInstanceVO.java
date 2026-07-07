package com.youlai.flowable.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WfInstanceVO {

    private Long id;

    private String processInstanceId;

    private String processDefinitionId;

    private String businessKey;

    private Long businessDataId;

    private Long businessModelId;

    private Long businessAppId;

    private Long modelId;

    private String modelKey;

    private String modelName;

    private Long starterId;

    private String starterUsername;

    private String status;

    private String formKey;

    private String formDataJson;

    private String currentNodeName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<WfTaskRecordVO> records;
}
