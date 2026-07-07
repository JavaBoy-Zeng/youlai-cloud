package com.youlai.collect.model;

import lombok.Data;

@Data
public class CollectRunRequest {
    private Long taskId;
    private String triggerType;
    private Long jobLogId;
}
