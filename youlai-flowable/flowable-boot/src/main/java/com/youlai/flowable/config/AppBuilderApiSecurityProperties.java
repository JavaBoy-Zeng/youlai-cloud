package com.youlai.flowable.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app-builder.api-security")
public class AppBuilderApiSecurityProperties {

    /**
     * Allowed URL schemes for API integration calls.
     */
    private List<String> allowedSchemes = new ArrayList<>(List.of("http", "https"));

    /**
     * Empty means every public host is allowed. Supports exact hosts and wildcard suffixes like *.example.com.
     */
    private List<String> allowedHosts = new ArrayList<>();

    /**
     * Block loopback, link-local, site-local and multicast targets by default to reduce SSRF risk.
     */
    private boolean blockPrivateAddress = true;
}
