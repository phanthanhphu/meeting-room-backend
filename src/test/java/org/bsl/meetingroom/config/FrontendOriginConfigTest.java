package org.bsl.meetingroom.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrontendOriginConfigTest {
    @Test
    void parsesConfiguredOriginsWithoutOverlyBroadWildcard() {
        FrontendOriginConfig origins = new FrontendOriginConfig(
                "http://localhost:3004, http://10.232.132.61:3004, http://localhost:3004 ");

        assertEquals(2, origins.getOrigins().size());
        assertTrue(origins.getOrigins().contains("http://10.232.132.61:3004"));
        assertFalse(origins.getOrigins().contains("*"));
    }

    @Test
    void rejectsEmptyOriginConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FrontendOriginConfig(" , , "));
    }
}
