package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.dto.Dtos.RoomRequest;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomColorsTest {
    private final MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
    private final EquipmentRepository equipment=mock(EquipmentRepository.class);
    private final BookingRepository bookings=mock(BookingRepository.class);
    private final MongoRoomApprovalLockService locks=mock(MongoRoomApprovalLockService.class);
    private final AppSocketPublisher socket=mock(AppSocketPublisher.class);
    private final NotificationService notifications=mock(NotificationService.class);
    private final RoomService service=new RoomService(rooms,equipment,bookings,Clock.systemDefaultZone(),socket,notifications,locks);

    private User manager() {
        User user=new User();
        user.setId("manager-1");user.setFullName("Manager");user.setRole(Role.ROOM_MANAGER);
        return user;
    }
    private RoomRequest request(String color) {
        return new RoomRequest("MR-NEW","Boardroom",color,"Floor 2",12,"","",RoomStatus.AVAILABLE,null);
    }
    @Test void paletteContainsOnlyUniqueMutedHexColors() {
        assertEquals(RoomColors.PALETTE.size(),new HashSet<>(RoomColors.PALETTE).size());
        assertTrue(RoomColors.PALETTE.size()>=24);
        for(String color: RoomColors.PALETTE) assertTrue(color.matches("#[0-9A-F]{6}"));
    }
    @Test void cannotSaveUnselectedOrArbitraryColor() {
        for(String color:new String[]{null,"", "#FF00FF", "#FFFFFF"}) {
            var error=assertThrows(AppException.class,()->service.create(request(color), manager()));
            assertEquals("ROOM_COLOR_INVALID",error.getCode());
        }
        verify(rooms,never()).save(any(MeetingRoom.class));
    }
    @Test void createRejectsColorAlreadyUsedByAnotherRoom() {
        String color=RoomColors.PALETTE.get(0);
        when(rooms.existsByColor(color)).thenReturn(true);
        var error=assertThrows(AppException.class,()->service.create(request(color), manager()));
        assertEquals("ROOM_COLOR_EXISTS",error.getCode());
        verify(rooms,never()).save(any(MeetingRoom.class));
    }
    @Test void createPersistsSelectedColor() {
        String color=RoomColors.PALETTE.get(1);
        when(rooms.save(any(MeetingRoom.class))).thenAnswer(inv->inv.getArgument(0));
        var saved=service.create(request(color),manager());
        assertEquals(color,saved.color());
        verify(socket).publish("ROOM_CHANGED");
    }
    @Test void editCanKeepItsOwnColor() {
        String color=RoomColors.PALETTE.get(0);
        MeetingRoom existing=new MeetingRoom();existing.setId("room-1");existing.setCode("MR-NEW");existing.setColor(color);existing.setCapacity(12);existing.setStatus(RoomStatus.AVAILABLE);
        when(locks.tryAcquire(eq("room-1"),any())).thenReturn("lock");
        when(rooms.findById("room-1")).thenReturn(Optional.of(existing));
        when(rooms.save(any(MeetingRoom.class))).thenAnswer(inv->inv.getArgument(0));
        assertEquals(color,service.update("room-1",request(color),manager()).color());
        verify(rooms).existsByColorAndIdNot(color,"room-1");
    }
    @Test void editCannotReuseOtherRoomColor() {
        String color=RoomColors.PALETTE.get(1);
        MeetingRoom existing=new MeetingRoom();existing.setId("room-1");existing.setCode("MR-NEW");existing.setCapacity(12);existing.setStatus(RoomStatus.AVAILABLE);
        when(locks.tryAcquire(eq("room-1"),any())).thenReturn("lock");
        when(rooms.findById("room-1")).thenReturn(Optional.of(existing));
        when(rooms.existsByColorAndIdNot(color,"room-1")).thenReturn(true);
        var error=assertThrows(AppException.class,()->service.update("room-1",request(color),manager()));
        assertEquals("ROOM_COLOR_EXISTS",error.getCode());
        verify(rooms,never()).save(any(MeetingRoom.class));
    }
}
