package org.bsl.meetingroom.config;

import org.bsl.meetingroom.common.socket.AppEventSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final AppEventSocketHandler handler;
    private final FrontendOriginConfig frontendOrigins;

    public WebSocketConfig(AppEventSocketHandler handler, FrontendOriginConfig frontendOrigins) {
        this.handler = handler;
        this.frontendOrigins = frontendOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/app-events")
                .setAllowedOriginPatterns(frontendOrigins.getOrigins().toArray(String[]::new));
    }
}
