package com.youlai.flowable.model.form;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class StartProcessForm {

    private Long modelId;

    private String processDefinitionId;

    private String businessKey;

    private String formDataJson;

    private Map<String, Object> variables = new HashMap<>();
}
