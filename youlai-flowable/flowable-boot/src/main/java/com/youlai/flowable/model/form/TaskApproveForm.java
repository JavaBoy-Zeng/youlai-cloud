package com.youlai.flowable.model.form;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class TaskApproveForm {

    private String comment;

    private String targetActivityId;

    private String assignee;

    private String attachmentJson;

    private Map<String, Object> variables = new HashMap<>();
}
