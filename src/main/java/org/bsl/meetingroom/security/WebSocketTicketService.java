package org.bsl.meetingroom.security;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketTicketService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String,Instant> pending = new ConcurrentHashMap<>();
    public String issue() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String ticket=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(ticket,now.plusSeconds(30));
        return ticket;
    }
    public boolean consume(String ticket) {
        if(ticket==null || ticket.isBlank()) return false;
        Instant expiry=pending.remove(ticket); // once, for only one connection
        return expiry!=null && expiry.isAfter(Instant.now());
    }
}
