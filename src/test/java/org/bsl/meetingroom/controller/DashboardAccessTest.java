package org.bsl.meetingroom.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;

class DashboardAccessTest {
    @Test void summaryEnforcesManagerOrAdminOnBackend() throws Exception {
        PreAuthorize pre=DashboardController.class.getMethod("summary").getAnnotation(PreAuthorize.class);
        assertNotNull(pre);
        assertEquals("hasAnyRole('ADMIN','ROOM_MANAGER')",pre.value());
    }
}
