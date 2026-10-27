package org.bsl.meetingroom.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigCorsTest {
    @Test
    void allowsMeetingRoomLANFrontendsOnAnyPort() {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(SecurityConfig.ALLOWED_ORIGIN_PATTERNS);
        cors.setAllowCredentials(true);
        assertEquals("http://10.232.132.39:3004", cors.checkOrigin("http://10.232.132.39:3004"));
        assertEquals("http://10.232.132.61:5173", cors.checkOrigin("http://10.232.132.61:5173"));
        assertEquals("http://192.168.1.10:3004", cors.checkOrigin("http://192.168.1.10:3004"));
        assertEquals("https://homepage.youngone.com.vn", cors.checkOrigin("https://homepage.youngone.com.vn"));
    }

    @Test
    void rejectsUnexpectedPublicOrigins() {
        var cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(SecurityConfig.ALLOWED_ORIGIN_PATTERNS);
        cors.setAllowCredentials(true);
        assertNull(cors.checkOrigin("https://example.com"));
    }
}
