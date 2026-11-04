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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailBookingNotificationTest {
    private final MailConfigService config = mock(MailConfigService.class);
    private final MailOutboxRepository outbox = mock(MailOutboxRepository.class);
    private final MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private MailOutboxService service(boolean enabled) {
        when(config.isMailEnabled()).thenReturn(enabled);
        when(config.adminNotificationEnabled("CREATED")).thenReturn(true);
        return new MailOutboxService(config, outbox, rooms, users, clock, "https://meeting.example.com");
    }

    private MailSenderIdentity sender() {
        MailSenderIdentity from = new MailSenderIdentity();
        from.setId("sender1");
        from.setEmail("phupt.st@youngonevn.com");
        from.setDisplayName("Meeting Room Booking");
        from.setEnabled(true);
        return from;
    }

    private Booking booking() {
        Booking b = new Booking();
        b.setId("booking1");
        b.setRoomId("room1");
        b.setCreatedById("requester");
        b.setCreatedByAccount("user1");
        b.setCreatedByEmail("requester@example.com");
        b.setTitle("Project meeting");
        b.setStartAt(LocalDateTime.of(2026, 10, 29, 9, 0));
        b.setEndAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        return b;
    }

    private MeetingRoom room(String... managers) {
        MeetingRoom room = new MeetingRoom();
        room.setId("room1");
        room.setName("Meeting Room A");
        room.setAssignedMailManagerIds(List.of(managers));
        when(rooms.findById("room1")).thenReturn(Optional.of(room));
        when(config.senderForRoom(room)).thenReturn(Optional.of(sender()));
        return room;
    }

    @Test void pendingSendsUserConfirmationEvenWithNoAdminsConfigured() {
        room();
        service(true).bookingCreated(booking());
        var saved=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox).save(saved.capture());
        assertEquals("requester@example.com",saved.getValue().getRecipientEmail());
        assertEquals("PENDING",saved.getValue().getEventType());
        assertFalse(saved.getValue().getHtmlBody().contains("Review Booking"));
    }

    @Test void explicitAdminAndOwnerGetDifferentContent() {
        room();
        when(config.bookingRecipients()).thenReturn(List.of("manager@example.com"));
        service(true).bookingCreated(booking());
        var saved=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox,times(2)).save(saved.capture());
        var mails=saved.getAllValues();
        assertEquals("PENDING",mails.get(0).getEventType());
        assertEquals("CREATED",mails.get(1).getEventType());
        assertEquals("manager@example.com",mails.get(1).getRecipientEmail());
        assertTrue(mails.get(1).getHtmlBody().contains("Review Booking"));
        assertFalse(mails.get(0).getHtmlBody().contains("Review Booking"));
    }

    @Test void configuredAdminMatchingAssignedManagerDoesNotDuplicateAdminEmail() {
        room("admin1");
        when(config.bookingRecipients()).thenReturn(List.of("manager@example.com"));
        User user=new User();user.setId("admin1");user.setEmail("MANAGER@example.com");
        user.setRole(Role.ADMIN);
        when(users.findById("admin1")).thenReturn(Optional.of(user));
        service(true).bookingCreated(booking());
        var saved=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox,times(2)).save(saved.capture());
        assertEquals(2,saved.getAllValues().size());
    }

    @Test void disabledAdminRuleStillDeliversOwnerEmail() {
        room();
        when(config.bookingRecipients()).thenReturn(List.of("manager@example.com"));
        when(config.adminNotificationEnabled("CREATED")).thenReturn(false);
        service(true).bookingCreated(booking());
        verify(outbox,times(1)).save(any(MailOutboxMessage.class));
    }

    @Test void overlappingOwnerAndAdminEmailReceiveBothRoleSpecificMessages() {
        room();
        when(config.bookingRecipients()).thenReturn(List.of("requester@example.com"));
        service(true).bookingCreated(booking());
        var saved=org.mockito.ArgumentCaptor.forClass(MailOutboxMessage.class);
        verify(outbox,times(2)).save(saved.capture());
        assertEquals("PENDING",saved.getAllValues().get(0).getEventType());
        assertEquals("CREATED",saved.getAllValues().get(1).getEventType());
    }

    @Test void disabledMailDoesNotQueueNotifications() {
        room();
        service(false).bookingCreated(booking());
        verifyNoInteractions(outbox);
    }
}
