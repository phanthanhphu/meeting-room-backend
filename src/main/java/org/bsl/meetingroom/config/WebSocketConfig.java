package org.bsl.meetingroom.config;

import org.bsl.meetingroom.common.socket.AppEventSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final AppEventSocketHandler handler;
    public WebSocketConfig(AppEventSocketHandler handler){this.handler=handler;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(handler,"/ws/app-events").setAllowedOriginPatterns("*");
    }
}
