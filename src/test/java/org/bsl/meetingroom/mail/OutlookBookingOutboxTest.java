package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutlookBookingOutboxTest {
    private final MailConfigService config=mock(MailConfigService.class);
    private final MailOutboxRepository outbox=mock(MailOutboxRepository.class);
    private final MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    private MailOutboxService service(){
        when(config.isMailEnabled()).thenReturn(true);
        MeetingRoom room=new MeetingRoom();room.setId("room1");room.setName("Room A");
        when(rooms.findById("room1")).thenReturn(Optional.of(room));
        MailSenderIdentity sender=new MailSenderIdentity();sender.setId("sender1");
        sender.setEmail("alert.st@youngonevn.com");sender.setDisplayName("Meeting Room Booking");
        when(config.senderForRoom(room)).thenReturn(Optional.of(sender));
        when(config.bookingRecipients()).thenReturn(List.of("admin@example.com"));
        when(config.adminNotificationEnabled("APPROVED")).thenReturn(true);
        when(config.adminNotificationEnabled("CANCELLED")).thenReturn(true);
        return new MailOutboxService(config,outbox,rooms,users,clock,"https://meeting.example.com");
    }
    private Booking booking(){
        Booking b=new Booking();b.setId("booking1");b.setRoomId("room1");
        b.setCreatedById("user1");b.setCreatedByEmail("user@youngonevn.com");
        b.setTitle("Team Meeting");b.setStartAt(LocalDateTime.of(2026,11,6,9,0));
        b.setEndAt(LocalDateTime.of(2026,11,6,10,0));return b;
    }
    @Test void approvalQueuesRealOutlookRequest(){
        service().bookingApproved(booking(),null);
        var cap=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox,times(2)).save(cap.capture());
        MailOutboxMessage owner=cap.getAllValues().stream().filter(x->x.getRecipientEmail().equals("user@youngonevn.com")).findFirst().orElseThrow();
        MailOutboxMessage admin=cap.getAllValues().stream().filter(x->x.getRecipientEmail().equals("admin@example.com")).findFirst().orElseThrow();
        assertEquals("REQUEST",owner.getCalendarMethod());
        assertTrue(owner.getCalendarBody().contains("METHOD:REQUEST"));
        assertNull(admin.getCalendarBody());
        assertTrue(admin.getHtmlBody().contains("administrative") || admin.getHtmlBody().contains("management"));
    }
    @Test void cancellationOfApprovedBookingQueuesCancelOnlyForOwner(){
        Booking b=booking();b.setApprovedAt(LocalDateTime.of(2026,11,4,8,0));
        service().bookingCancelled(b,null);
        var cap=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox,times(2)).save(cap.capture());
        MailOutboxMessage owner=cap.getAllValues().stream().filter(x->x.getRecipientEmail().equals("user@youngonevn.com")).findFirst().orElseThrow();
        assertEquals("CANCEL",owner.getCalendarMethod());
        MailOutboxMessage manager=cap.getAllValues().stream().filter(x->x.getRecipientEmail().equals("admin@example.com")).findFirst().orElseThrow();
        assertNull(manager.getCalendarBody());
    }
    @Test void pendingCancellationDoesNotCreateFakeOutlookInvite(){
        service().bookingCancelled(booking(),null);
        var cap=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox,times(2)).save(cap.capture());
        assertTrue(cap.getAllValues().stream().allMatch(x->x.getCalendarBody()==null));
    }
}
