package org.bsl.meetingroom.config;

import org.bsl.meetingroom.model.MeetingRoom;
import org.bsl.meetingroom.repository.MeetingRoomRepository;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerRoomColorTest {
    private void runColorAssignment(MeetingRoomRepository repo) throws Exception {
        Method method = DataInitializer.class.getDeclaredMethod("assignColorsToUnconfiguredRooms", MeetingRoomRepository.class);
        method.setAccessible(true);
        method.invoke(new DataInitializer(), repo);
    }

    @Test void startupNeverRecolorsExistingRooms() throws Exception {
        MeetingRoom a = new MeetingRoom(); a.setId("103"); a.setCode("103"); a.setColor("#426B91");
        MeetingRoom b = new MeetingRoom(); b.setId("104"); b.setCode("104"); b.setColor("#2D79C7");
        MeetingRoom c = new MeetingRoom(); c.setId("105"); c.setCode("105"); c.setColor("#168F91");
        MeetingRoomRepository repo=mock(MeetingRoomRepository.class);
        when(repo.findAll()).thenReturn(List.of(a,b,c));
        runColorAssignment(repo);
        assertEquals("#426B91",a.getColor());
        assertEquals("#2D79C7",b.getColor());
        assertEquals("#168F91",c.getColor());
        verify(repo, never()).save(any(MeetingRoom.class));
    }

    @Test void startupAssignsOnlyUnconfiguredRoom() throws Exception {
        MeetingRoom a = new MeetingRoom(); a.setId("103"); a.setCode("103"); a.setColor("#426B91");
        MeetingRoom b = new MeetingRoom(); b.setId("104"); b.setCode("104"); b.setColor("#2D79C7");
        MeetingRoom c = new MeetingRoom(); c.setId("105"); c.setCode("105"); c.setColor(null);
        MeetingRoomRepository repo=mock(MeetingRoomRepository.class);
        when(repo.findAll()).thenReturn(List.of(a,b,c));
        runColorAssignment(repo);
        assertEquals("#426B91",a.getColor());
        assertEquals("#2D79C7",b.getColor());
        assertEquals("#168F91",c.getColor());
        verify(repo, times(1)).save(same(c));
        verify(repo, never()).save(same(a));
        verify(repo, never()).save(same(b));
    }
}
