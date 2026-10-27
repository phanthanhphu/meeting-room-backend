package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.config.BookingRulesProperties;
import org.bsl.meetingroom.dto.Dtos.BookingRequest;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovedBookingPermissionTest {
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
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BookingService service=new BookingService(bookings,rooms,users,history,rules,clock,mapper,locks,socket,notifications,mongo);

    private User actor(String id,Role role){
        User user=new User();user.setId(id);user.setFullName(id);user.setRole(role);return user;
    }
    private Booking approved(){
        Booking booking=new Booking();booking.setId("b-1");booking.setCreatedById("owner");booking.setRoomId("room-1");
        booking.setTitle("Weekly planning");booking.setStatus(BookingStatus.APPROVED);
        booking.setStartAt(LocalDateTime.of(2026,10,27,10,0));booking.setEndAt(LocalDateTime.of(2026,10,27,11,0));
        return booking;
    }
    private BookingRequest payload(){
        return new BookingRequest("room-1","Changed title","",2,LocalDateTime.of(2026,10,27,10,0),LocalDateTime.of(2026,10,27,11,0));
    }
    private void mockLock(){ when(locks.tryAcquire(eq("USER:owner"),any())).thenReturn("lock-token"); }

    @Test void approvedBookingCannotBeEditedByOwner(){
        Booking booking=approved();when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));mockLock();
        AppException error=assertThrows(AppException.class,()->service.update(booking.getId(),payload(),actor("owner",Role.USER)));
        assertEquals("BOOKING_LOCKED",error.getCode());
        verify(bookings,never()).save(any(Booking.class));
    }
    @Test void approvedBookingCannotBeEditedByAdmin(){
        Booking booking=approved();when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));mockLock();
        AppException error=assertThrows(AppException.class,()->service.update(booking.getId(),payload(),actor("admin",Role.ADMIN)));
        assertEquals("BOOKING_LOCKED",error.getCode());
        verify(bookings,never()).save(any(Booking.class));
    }
    @Test void ownerCannotCancelApprovedBooking(){
        Booking booking=approved();when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));mockLock();
        AppException error=assertThrows(AppException.class,()->service.cancel(booking.getId(),actor("owner",Role.USER)));
        assertEquals("FORBIDDEN",error.getCode());
        verify(bookings,never()).save(any(Booking.class));
    }
    @Test void roomManagerCanCancelApprovedBooking(){
        Booking booking=approved();when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));mockLock();
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MeetingRoom room=new MeetingRoom();room.setId("room-1");room.setName("Excellence");
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        assertDoesNotThrow(()->service.cancel(booking.getId(),actor("manager",Role.ROOM_MANAGER)));
        assertEquals(BookingStatus.CANCELLED,booking.getStatus());
        verify(locks).release("USER:owner","lock-token");
    }
}
