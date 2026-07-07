package com.youlai.decision.engine;

import com.youlai.decision.service.JsonService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultHareGatewayClient implements HareGatewayClient {

    private final DecisionEngineProperties properties;
    private final JsonService jsonService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public DefaultHareGatewayClient(DecisionEngineProperties properties, JsonService jsonService) {
        this.properties = properties;
        this.jsonService = jsonService;
    }

    @Override
    public Map<String, Object> fetch(String shortName, String apiName, Map<String, Object> params) {
        if (!"http".equalsIgnoreCase(properties.getHare().getMode()) || !StringUtils.hasText(properties.getHare().getBaseUrl())) {
            return Map.of(
                    "data", Map.of(
                            "riskCount", params.getOrDefault("mockRiskCount", 0),
                            "hit", params.getOrDefault("mockHit", false),
                            "score", params.getOrDefault("mockScore", 0)
                    ),
                    "mock", true
            );
        }
        try {
            String url = properties.getHare().getBaseUrl().replaceAll("/$", "")
                    + "/service-interface-gateway/results/" + shortName + "/" + apiName;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonService.write(params)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Map.of("error", "HTTP " + response.statusCode());
            }
            return jsonService.readMap(response.body());
        } catch (Exception ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", ex.getMessage());
            return error;
        }
    }
}
