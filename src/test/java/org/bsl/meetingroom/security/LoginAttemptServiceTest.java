package org.bsl.meetingroom.security;

import org.bsl.meetingroom.exception.AppException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {
    @Test void blocksSixthAttemptAndSuccessClearsFailures() {
        var service=new LoginAttemptService();
        String key=service.key("10.232.132.39","Admin");
        assertEquals(service.key("10.232.132.39","admin"),key);
        for(int i=0;i<5;i++) { service.check(key); service.failed(key); }
        AppException error=assertThrows(AppException.class,()->service.check(key));
        assertEquals("LOGIN_RATE_LIMIT",error.getCode());
        service.succeeded(key);
        assertDoesNotThrow(()->service.check(key));
    }
}
