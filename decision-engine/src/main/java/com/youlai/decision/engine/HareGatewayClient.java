package com.youlai.decision.engine;

import java.util.Map;

public interface HareGatewayClient {
    Map<String, Object> fetch(String shortName, String apiName, Map<String, Object> params);
}
