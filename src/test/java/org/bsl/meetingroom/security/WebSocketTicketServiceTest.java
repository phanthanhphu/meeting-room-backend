package org.bsl.meetingroom.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketTicketServiceTest {
    @Test void ticketCanOnlyBeUsedOnce() {
        WebSocketTicketService service=new WebSocketTicketService();
        String ticket=service.issue();
        assertTrue(service.consume(ticket));
        assertFalse(service.consume(ticket));
        assertFalse(service.consume(""));
        assertFalse(service.consume("another-ticket"));
    }
}
