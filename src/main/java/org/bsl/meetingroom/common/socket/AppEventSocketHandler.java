package org.bsl.meetingroom.common.socket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AppEventSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override public void afterConnectionEstablished(WebSocketSession session){ sessions.add(session); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status){ sessions.remove(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception){ sessions.remove(session); }

    public void broadcast(String json){
        TextMessage message = new TextMessage(json);
        for(WebSocketSession session:sessions){
            if(!session.isOpen()) { sessions.remove(session); continue; }
            try { session.sendMessage(message); } catch(IOException ex){ sessions.remove(session); }
        }
    }
}
