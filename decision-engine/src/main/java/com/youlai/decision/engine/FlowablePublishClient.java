package com.youlai.decision.engine;

import com.youlai.decision.model.DecisionPublishRequest;

public interface FlowablePublishClient {
    String submit(DecisionPublishRequest request);

    String status(String processInstanceId);
}
