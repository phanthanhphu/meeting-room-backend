package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.config.BookingRulesProperties;
import org.bsl.meetingroom.dto.Dtos.BookingRequest;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.mail.MailOutboxService;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Room/time conflicts only; user identity must not determine slot availability. */
class MultiplePendingRoomBookingTest {
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
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BookingService service=new BookingService(bookings,rooms,users,history,rules,clock,
            mapper,locks,socket,notifications,mongo,mailOutbox);
    private static final LocalDateTime START=LocalDateTime.of(2026,11,9,9,0);
    private static final LocalDateTime END=LocalDateTime.of(2026,11,9,10,0);
    private static final List<BookingStatus> BLOCKING=List.of(BookingStatus.PENDING,BookingStatus.APPROVED);

    private User owner(String id){
        User u=new User();u.setId(id);u.setUsername(id);u.setFullName(id);
        u.setEmail(id+"@example.test");u.setRole(Role.USER);return u;
    }
    private User admin(){User u=owner("admin");u.setRole(Role.ADMIN);return u;}
    private MeetingRoom room(String id){
        MeetingRoom r=new MeetingRoom();r.setId(id);r.setName(id);r.setCapacity(12);return r;
    }
    private BookingRequest request(String roomId){return new BookingRequest(roomId,"Planning","",3,START,END);}
    private Booking pending(String id,String roomId,String ownerId){
        Booking b=new Booking();b.setId(id);b.setRoomId(roomId);b.setCreatedById(ownerId);
        b.setStatus(BookingStatus.PENDING);b.setStartAt(START);b.setEndAt(END);b.setAttendeeCount(3);return b;
    }
    @BeforeEach void setup(){
        when(rules.getBusinessStart()).thenReturn(LocalTime.of(7,0));
        when(rules.getBusinessEnd()).thenReturn(LocalTime.of(18,0));
        when(rules.getMinDurationMinutes()).thenReturn(15);
        when(rules.getMaxDurationMinutes()).thenReturn(240);
        when(rules.getMaxAdvanceDays()).thenReturn(90);
        when(rooms.findById(anyString())).thenAnswer(inv -> Optional.of(room(inv.getArgument(0))));
        when(locks.tryAcquire(anyString(),any())).thenReturn("lock-token");
        when(bookings.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void anotherRoomCanBeRequestedAtSameTimeEvenIfUserHasApprovedMeeting(){
        // Repository is queried using ONLY room-2, never room-1 or owner.
        assertDoesNotThrow(() -> service.create(request("room-2"),owner("a")));
        verify(bookings).countByRoomIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq("room-2"),eq(BLOCKING),eq(END),eq(START));
        verify(bookings,never()).countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                anyString(),anyCollection(),any(),any());
        verify(locks).release("room-2","lock-token");
    }

    @Test void sameRoomPendingRequestOfSameUserBlocksCreation(){
        when(bookings.countByRoomIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq("room-1"),eq(BLOCKING),eq(END),eq(START))).thenReturn(1L);
        AppException error=assertThrows(AppException.class, () -> service.create(request("room-1"),owner("a")));
        assertEquals("ROOM_TIME_CONFLICT",error.getCode());
        verify(bookings,never()).save(any(Booking.class));
    }

    @Test void sameRoomPendingRequestOfDifferentUserAlsoBlocksCreation(){
        when(bookings.countByRoomIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq("room-1"),eq(BLOCKING),eq(END),eq(START))).thenReturn(1L);
        AppException error=assertThrows(AppException.class, () -> service.create(request("room-1"),owner("b")));
        assertEquals("ROOM_TIME_CONFLICT",error.getCode());
        verify(bookings,never()).save(any(Booking.class));
    }

    @Test void sameRoomApprovedBookingBlocksCreationForAnyone(){
        when(bookings.countByRoomIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq("room-1"),eq(BLOCKING),eq(END),eq(START))).thenReturn(1L);
        AppException error=assertThrows(AppException.class, () -> service.create(request("room-1"),owner("b")));
        assertEquals("ROOM_TIME_CONFLICT",error.getCode());
    }

    @Test void updatingPendingCanSwitchToAnotherRoomAtSameTime(){
        Booking b=pending("b-1","room-1","a");
        when(bookings.findById("b-1")).thenReturn(Optional.of(b));
        when(users.findById("a")).thenReturn(Optional.of(owner("a")));
        assertDoesNotThrow(() -> service.update("b-1",request("room-2"),owner("a")));
        verify(bookings).countByRoomIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                eq("room-2"),eq(BLOCKING),eq(END),eq(START),eq("b-1"));
        assertEquals("room-2",b.getRoomId());
        verify(locks).release("room-2","lock-token");
    }

    @Test void updatingPendingToOccupiedRoomIsBlocked(){
        Booking b=pending("b-1","room-1","a");
        when(bookings.findById("b-1")).thenReturn(Optional.of(b));
        when(users.findById("a")).thenReturn(Optional.of(owner("a")));
        when(bookings.countByRoomIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                eq("room-2"),eq(BLOCKING),eq(END),eq(START),eq("b-1"))).thenReturn(1L);
        AppException error=assertThrows(AppException.class, () -> service.update("b-1",request("room-2"),owner("a")));
        assertEquals("ROOM_TIME_CONFLICT",error.getCode());
        assertEquals("room-1",b.getRoomId());
        verify(bookings,never()).save(any(Booking.class));
    }

    @Test void approveCanProceedEvenIfUserHasAnotherRoomApproved(){
        Booking b=pending("b-2","room-2","a");
        when(bookings.findById("b-2")).thenReturn(Optional.of(b));
        assertDoesNotThrow(() -> service.approve("b-2",admin()));
        assertEquals(BookingStatus.APPROVED,b.getStatus());
        verify(bookings).countByRoomIdAndStatusAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                eq("room-2"),eq(BookingStatus.APPROVED),eq(END),eq(START),eq("b-2"));
        verify(bookings,never()).countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                anyString(),anyCollection(),any(),any(),anyString());
    }

    @Test void approveCannotCreateTwoApprovedMeetingsInSameRoom(){
        Booking b=pending("b-2","room-2","a");
        when(bookings.findById("b-2")).thenReturn(Optional.of(b));
        when(bookings.countByRoomIdAndStatusAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                eq("room-2"),eq(BookingStatus.APPROVED),eq(END),eq(START),eq("b-2"))).thenReturn(1L);
        AppException error=assertThrows(AppException.class, () -> service.approve("b-2",admin()));
        assertEquals("ROOM_TIME_CONFLICT",error.getCode());
        assertEquals(BookingStatus.PENDING,b.getStatus());
    }

    @Test void occupiedSlotsHideBookingIdentityAndIncludePendingAndApproved(){
        Booking own=pending("b-1","room-1","a");
        Booking other=pending("b-2","room-2","b");
        other.setStatus(BookingStatus.APPROVED);
        when(bookings.findByStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                eq(BLOCKING),any(),any())).thenReturn(List.of(own,other));
        var slots=service.occupied(START.toLocalDate(),null,owner("a"));
        assertEquals(2,slots.size());
        assertEquals("room-1",slots.get(0).roomId());
        assertEquals(BookingStatus.PENDING,slots.get(0).status());
        assertEquals("room-2",slots.get(1).roomId());
    }

    @Test void occupiedSlotsExcludeOnlyOwnedBookingWhileEditing(){
        Booking own=pending("b-1","room-1","a");
        Booking other=pending("b-2","room-1","b");
        when(bookings.findById("b-1")).thenReturn(Optional.of(own));
        when(bookings.findByStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                eq(BLOCKING),any(),any())).thenReturn(List.of(own,other));
        var slots=service.occupied(START.toLocalDate(),"b-1",owner("a"));
        assertEquals(1,slots.size());
        assertEquals("room-1",slots.get(0).roomId());
        assertThrows(AppException.class, () -> service.occupied(START.toLocalDate(),"b-1",owner("b")));
    }
}
