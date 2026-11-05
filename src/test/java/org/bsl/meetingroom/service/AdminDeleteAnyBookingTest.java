package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.config.BookingRulesProperties;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.mail.MailOutboxService;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression checks: ADMIN-only permanent deletion without sending email. */
class AdminDeleteAnyBookingTest {
    private final BookingRepository bookings=mock(BookingRepository.class);
    private final MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final BookingHistoryRepository history=mock(BookingHistoryRepository.class);
    private final BookingRulesProperties rules=mock(BookingRulesProperties.class);
    private final ResponseMapper mapper=mock(ResponseMapper.class);
    private final MongoRoomApprovalLockService locks=mock(MongoRoomApprovalLockService.class);
    private final AppSocketPublisher socket=mock(AppSocketPublisher.class);
    private final NotificationService notifications=mock(NotificationService.class);
    private final MongoTemplate mongo=mock(MongoTemplate.class);
    private final MailOutboxService mailOutbox=mock(MailOutboxService.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-05T04:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BookingService service=new BookingService(bookings,rooms,users,history,rules,clock,mapper,locks,socket,notifications,mongo,mailOutbox);

    private User actor(String id,Role role){
        User user=new User();user.setId(id);user.setFullName(id);user.setRole(role);return user;
    }
    private Booking booking(BookingStatus status){
        Booking b=new Booking();b.setId("booking-1");b.setCreatedById("owner");b.setRoomId("room-1");
        b.setTitle("Team meeting");b.setStatus(status);
        b.setStartAt(status==BookingStatus.COMPLETED ? LocalDateTime.of(2026,11,4,10,0) : LocalDateTime.of(2026,11,6,10,0));
        b.setEndAt(status==BookingStatus.COMPLETED ? LocalDateTime.of(2026,11,4,11,0) : LocalDateTime.of(2026,11,6,11,0));
        if(status==BookingStatus.APPROVED || status==BookingStatus.COMPLETED)
            b.setApprovedAt(LocalDateTime.of(2026,11,5,9,0));
        return b;
    }
    private void setup(Booking b){
        when(bookings.findById(b.getId())).thenReturn(Optional.of(b));
        when(locks.tryAcquire(eq("USER:owner"),any())).thenReturn("lock-token");
        MeetingRoom room=new MeetingRoom();room.setId("room-1");room.setName("Conference Room");
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
    }
    @Test void adminCanDeleteEveryStatusRegardlessOfOwner(){
        for(BookingStatus status:BookingStatus.values()){
            reset(bookings,rooms,history,locks,socket,notifications,mailOutbox);
            Booking b=booking(status);setup(b);
            assertDoesNotThrow(()->service.delete(b.getId(),actor("admin",Role.ADMIN)),"Failed on "+status);
            verify(history).deleteByBookingId(b.getId());
            verify(bookings).delete(b);
            verify(locks).release("USER:owner","lock-token");
            verify(socket).publish("BOOKING_CHANGED");
            verifyNoInteractions(mailOutbox);
        }
    }
    @Test void everyNonAdminRoleIsForbiddenForEveryStatusEvenWhenOwner(){
        for(BookingStatus status:BookingStatus.values()){
            for(Role role:new Role[]{Role.USER,Role.ROOM_MANAGER,Role.VIEWER}){
                reset(bookings,rooms,history,locks,socket,notifications,mailOutbox);
                Booking b=booking(status);setup(b);
                User actor=actor("owner",role);
                assertThrows(AppException.class,()->service.delete(b.getId(),actor),role + " / " + status);
                verify(bookings,never()).delete(any(Booking.class));
                verify(history,never()).deleteByBookingId(anyString());
                verifyNoInteractions(mailOutbox);
            }
        }
    }
    @Test void nullActorIsForbidden(){
        assertThrows(AppException.class,()->service.delete("booking-1",null));
        verifyNoInteractions(bookings,mailOutbox);
    }
    @Test void deletingAlreadyStartedApprovedBookingDoesNotSendEmail(){
        Booking b=booking(BookingStatus.APPROVED);
        b.setStartAt(LocalDateTime.of(2026,11,4,10,0));setup(b);
        service.delete(b.getId(),actor("admin",Role.ADMIN));
        verify(bookings).delete(b);
        verifyNoInteractions(mailOutbox);
    }
    @Test void lockContentionDoesNotDeleteAnything(){
        Booking b=booking(BookingStatus.COMPLETED);setup(b);
        when(locks.tryAcquire(eq("USER:owner"),any())).thenReturn(null);
        assertThrows(AppException.class,()->service.delete(b.getId(),actor("admin",Role.ADMIN)));
        verify(bookings,never()).delete(any(Booking.class));
        verify(history,never()).deleteByBookingId(anyString());
    }
}
