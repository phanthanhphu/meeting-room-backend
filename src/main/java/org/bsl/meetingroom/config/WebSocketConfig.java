package org.bsl.meetingroom.config;

import org.bsl.meetingroom.common.socket.AppEventSocketHandler;
import org.bsl.meetingroom.security.WebSocketTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final AppEventSocketHandler handler;
    private final WebSocketTicketService tickets;

    public WebSocketConfig(AppEventSocketHandler handler,WebSocketTicketService tickets) {
        this.handler = handler;
        this.tickets = tickets;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/app-events")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,
                            WebSocketHandler handler,Map<String,Object> attributes){
                        String ticket=UriComponentsBuilder.fromUri(request.getURI()).build()
                                .getQueryParams().getFirst("ticket");
                        if(!tickets.consume(ticket)) {
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return false;
                        }
                        return true;
                    }
                    @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,
                            WebSocketHandler handler,Exception exception) { }
                })
                // WebSocket su dung dung danh sach IP trong SecurityConfig.
                .setAllowedOriginPatterns(SecurityConfig.ALLOWED_ORIGIN_PATTERNS.toArray(String[]::new));
    }
}
