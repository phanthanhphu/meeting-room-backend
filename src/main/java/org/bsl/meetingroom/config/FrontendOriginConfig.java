package org.bsl.meetingroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Exact browser origins allowed to call the Meeting Room backend or connect by WebSocket.
 * Configure APP_FRONTEND_ORIGINS as a comma-separated list of full origins (scheme, host, port).
 * Never use "*" with credentialed requests.
 */
@Component
public class FrontendOriginConfig {
    private final List<String> origins;

    public FrontendOriginConfig(@Value("${app.cors.allowed-origin-patterns}") String configuredOrigins) {
        this.origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origin-patterns must include at least one frontend origin");
        }
    }

    public List<String> getOrigins() {
        return origins;
    }
}
