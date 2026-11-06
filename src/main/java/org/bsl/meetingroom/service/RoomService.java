package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.constants.BookingLockConstants;

import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Objects;

@Service
public class RoomService {
    private final MeetingRoomRepository rooms;
    private final EquipmentRepository equipment;
    private final BookingRepository bookings;
    private final MongoRoomApprovalLockService approvalLocks;
    private final Clock clock;
    private final AppSocketPublisher socket;
    private final NotificationService notifications;
    public RoomService(MeetingRoomRepository rooms,EquipmentRepository equipment,BookingRepository bookings,Clock clock,AppSocketPublisher socket,NotificationService notifications,MongoRoomApprovalLockService approvalLocks){this.rooms=rooms;this.equipment=equipment;this.bookings=bookings;this.clock=clock;this.socket=socket;this.notifications=notifications;this.approvalLocks=approvalLocks;}
    public List<RoomResponse> list(){return rooms.findAllByOrderByNameAsc().stream().map(ResponseMapper::room).toList();}
    public RoomResponse get(String id){return ResponseMapper.room(find(id));}
    MeetingRoom find(String id){return rooms.findById(id).orElseThrow(()->AppException.notFound("Meeting room not found."));}

    public RoomResponse create(RoomRequest req,User actor){
        requireRoomManager(actor);
        if(rooms.existsByCodeIgnoreCase(req.code().trim())) throw AppException.conflict("ROOM_CODE_EXISTS","This room code already exists.");
        validateRoomColor(req.color(), null);
        MeetingRoom r=new MeetingRoom(); apply(r,req); RoomResponse saved=ResponseMapper.room(rooms.save(r));
        notifications.notifyAllEnabledExcept(actor.getId(),NotificationType.ROOM_CREATED,"New meeting room",
                actor.getFullName()+" added "+saved.name()+".","ROOM",saved.id(),"/rooms");
        socket.publish("ROOM_CHANGED"); return saved;
    }
    public RoomResponse update(String id,RoomRequest req,User actor){
        requireRoomManager(actor);
        String token=approvalLocks.tryAcquire(id,BookingLockConstants.ROOM_UPDATE_LOCK_TTL);
        if(token==null) throw AppException.conflict("ROOM_UPDATE_BUSY", "This room is currently being approved or updated. Please try again.");
        try { return updateLocked(id,req,actor); }
        finally { approvalLocks.release(id,token); }
    }
    private RoomResponse updateLocked(String id,RoomRequest req,User actor){
        MeetingRoom r=find(id);
        validateRoomColor(req.color(), r);
        if(!r.getCode().equalsIgnoreCase(req.code().trim()) && rooms.existsByCodeIgnoreCase(req.code().trim()))
            throw AppException.conflict("ROOM_CODE_EXISTS","This room code already exists.");
        if(req.status()!=RoomStatus.AVAILABLE && r.getStatus()==RoomStatus.AVAILABLE){
            long future=bookings.countByRoomIdAndStatusAndEndAtAfter(id,BookingStatus.APPROVED,LocalDateTime.now(clock));
            if(future>0) throw AppException.conflict("ROOM_HAS_BOOKINGS","This room has future approved bookings. Resolve them before disabling the room.");
        }
        if (req.capacity() < r.getCapacity() && bookings.existsByRoomIdAndStatusAndEndAtAfterAndAttendeeCountGreaterThan(
                id, BookingStatus.APPROVED, LocalDateTime.now(clock), req.capacity())) {
            throw AppException.conflict("ROOM_CAPACITY_CONFLICT", "Some future approved bookings exceed the new room capacity.");
        }
        apply(r,req); RoomResponse saved=ResponseMapper.room(rooms.save(r));
        notifications.notifyAllEnabledExcept(actor.getId(),NotificationType.ROOM_UPDATED,"Meeting room updated",
                actor.getFullName()+" updated "+saved.name()+" ("+saved.status()+").","ROOM",saved.id(),"/rooms");
        socket.publish("ROOM_CHANGED"); return saved;
    }
    public void delete(String id,User actor){
        requireRoomManager(actor);
        MeetingRoom r=find(id);
        if(bookings.existsByRoomId(id))
            throw AppException.conflict("ROOM_HAS_HISTORY","This room has booking history and cannot be deleted. Set its status to DISABLED instead.");
        String roomName=r.getName();
        rooms.delete(r);
        notifications.notifyAllEnabledExcept(actor.getId(),NotificationType.ROOM_DELETED,"Meeting room removed",
                actor.getFullName()+" removed "+roomName+".","ROOM",id,"/rooms");
        socket.publish("ROOM_CHANGED");
    }
    private void requireRoomManager(User actor){
        if(actor==null || !actor.getRole().canManageRooms())
            throw AppException.forbidden("You do not have permission to manage meeting rooms.");
    }
    private void validateRoomColor(String color, MeetingRoom currentRoom) {
        // New choices must come from the current palette. When an existing room is
        // edited for any other reason, its saved legacy hex is preserved verbatim.
        boolean unchangedLegacy = currentRoom != null
                && Objects.equals(color, currentRoom.getColor())
                && RoomColors.storedHex(color);
        if (!RoomColors.valid(color) && !unchangedLegacy)
            throw AppException.badRequest("ROOM_COLOR_INVALID", "Select a color from the meeting room palette.");
        boolean taken = currentRoom == null
                ? rooms.existsByColor(color)
                : rooms.existsByColorAndIdNot(color, currentRoom.getId());
        if (taken)
            throw AppException.conflict("ROOM_COLOR_EXISTS", "This color is already assigned to another room. Please choose a different color.");
        // Unique sparse MongoDB index on MeetingRoom.color also handles concurrent creates.
    }
    private void apply(MeetingRoom r,RoomRequest req){
        r.setColor(req.color());
        r.setCode(req.code().trim().toUpperCase());r.setName(req.name().trim());r.setLocation(req.location().trim());r.setCapacity(req.capacity());
        r.setDescription(req.description());r.setStatus(req.status());
        // Null means a legacy client sent only "amenities"; [] explicitly means no equipment.
        if(req.equipmentIds()!=null){
            Set<String> ids=new LinkedHashSet<>(req.equipmentIds());
            if(ids.size()!=req.equipmentIds().size())throw AppException.badRequest("DUPLICATE_EQUIPMENT","The same equipment cannot be selected more than once.");
            var selected=equipment.findAllById(ids);
            if(selected.size()!=ids.size())throw AppException.badRequest("UNKNOWN_EQUIPMENT","The equipment list contains unknown items.");
            for(var device:selected){
                if(device.getStatus()==EquipmentStatus.INACTIVE)throw AppException.badRequest("INACTIVE_EQUIPMENT","Inactive equipment cannot be assigned: "+device.getName());
            }
            r.setEquipmentIds(List.copyOf(ids));
            r.setAmenities(selected.stream().map(Equipment::getName).sorted().reduce((a,b)->a+", "+b).orElse(""));
        }else{
            r.setAmenities(req.amenities());
        }
    }
}
