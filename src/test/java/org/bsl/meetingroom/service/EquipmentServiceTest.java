package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EquipmentServiceTest {
    @Test
    void createCatalogItemNormalizesCodeAndCategory() {
        EquipmentRepository items=mock(EquipmentRepository.class);
        MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
        AppSocketPublisher socket=mock(AppSocketPublisher.class);
        when(items.save(any(Equipment.class))).thenAnswer(inv -> inv.getArgument(0));
        EquipmentService service=new EquipmentService(items,rooms,socket);
        EquipmentResponse created=service.create(new EquipmentRequest(" eq_tv ","Screen","display","",EquipmentStatus.ACTIVE));
        assertEquals("EQ_TV",created.code());
        assertEquals("DISPLAY",created.category());
        verify(socket).publish("EQUIPMENT_CHANGED");
    }

    @Test
    void equipmentAssignedToRoomCannotBeDeleted() {
        EquipmentRepository items=mock(EquipmentRepository.class);
        MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
        AppSocketPublisher socket=mock(AppSocketPublisher.class);
        Equipment item=new Equipment();item.setId("device-1");item.setCode("EQ_TV");item.setName("TV");item.setCategory("DISPLAY");
        when(items.findById("device-1")).thenReturn(Optional.of(item));
        when(rooms.existsByEquipmentIdsContaining("device-1")).thenReturn(true);
        EquipmentService service=new EquipmentService(items,rooms,socket);
        AppException error=assertThrows(AppException.class,()->service.delete("device-1"));
        assertEquals("EQUIPMENT_IN_USE",error.getCode());
        verify(items,never()).delete(any(Equipment.class));
    }

    @Test
    void assignedEquipmentCannotBeMarkedInactive() {
        EquipmentRepository items=mock(EquipmentRepository.class);
        MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
        AppSocketPublisher socket=mock(AppSocketPublisher.class);
        Equipment item=new Equipment();item.setId("device-1");item.setCode("EQ_TV");item.setName("TV");item.setCategory("DISPLAY");item.setStatus(EquipmentStatus.ACTIVE);
        when(items.findById("device-1")).thenReturn(Optional.of(item));
        when(rooms.existsByEquipmentIdsContaining("device-1")).thenReturn(true);
        EquipmentService service=new EquipmentService(items,rooms,socket);
        assertEquals("EQUIPMENT_IN_USE",assertThrows(AppException.class,()->service.update("device-1",new EquipmentRequest("EQ_TV","TV","DISPLAY","",EquipmentStatus.INACTIVE))).getCode());
        verify(items,never()).save(any(Equipment.class));
    }
}
