package org.bsl.meetingroom.common.socket;

import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class AppSocketPublisher {
    private final AppEventSocketHandler handler;
    public AppSocketPublisher(AppEventSocketHandler handler){this.handler=handler;}
    public void publish(String type){
        String safe = type == null ? "DATA_CHANGED" : type.replaceAll("[^A-Z0-9_]", "");
        handler.broadcast("{\"type\":\""+safe+"\",\"at\":\""+ Instant.now()+"\"}");
    }
}
