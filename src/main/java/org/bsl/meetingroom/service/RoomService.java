package org.bsl.meetingroom.service;

import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.List;

@Service
public class RoomService {
    private final MeetingRoomRepository rooms;
    private final BookingRepository bookings;
    private final Clock clock;
    private final AppSocketPublisher socket;
    private final NotificationService notifications;
    public RoomService(MeetingRoomRepository rooms,BookingRepository bookings,Clock clock,AppSocketPublisher socket,NotificationService notifications){this.rooms=rooms;this.bookings=bookings;this.clock=clock;this.socket=socket;this.notifications=notifications;}
    public List<RoomResponse> list(){return rooms.findAllByOrderByNameAsc().stream().map(ResponseMapper::room).toList();}
    public RoomResponse get(String id){return ResponseMapper.room(find(id));}
    MeetingRoom find(String id){return rooms.findById(id).orElseThrow(()->AppException.notFound("Không tìm thấy phòng họp"));}

    public RoomResponse create(RoomRequest req,User actor){
        requireRoomManager(actor);
        if(rooms.existsByCodeIgnoreCase(req.code().trim())) throw AppException.conflict("ROOM_CODE_EXISTS","Mã phòng đã tồn tại");
        MeetingRoom r=new MeetingRoom(); apply(r,req); RoomResponse saved=ResponseMapper.room(rooms.save(r));
        notifications.notifyAllEnabledExcept(actor.getId(),NotificationType.ROOM_CREATED,"New meeting room",
                actor.getFullName()+" added "+saved.name()+".","ROOM",saved.id(),"/rooms");
        socket.publish("ROOM_CHANGED"); return saved;
    }
    public RoomResponse update(String id,RoomRequest req,User actor){
        requireRoomManager(actor);
        MeetingRoom r=find(id);
        if(!r.getCode().equalsIgnoreCase(req.code().trim()) && rooms.existsByCodeIgnoreCase(req.code().trim()))
            throw AppException.conflict("ROOM_CODE_EXISTS","Mã phòng đã tồn tại");
        if(req.status()!=RoomStatus.AVAILABLE && r.getStatus()==RoomStatus.AVAILABLE){
            long future=bookings.countByRoomIdAndStatusAndEndAtAfter(id,BookingStatus.APPROVED,LocalDateTime.now(clock));
            if(future>0) throw AppException.conflict("ROOM_HAS_BOOKINGS","Phòng đang có lịch đã duyệt trong tương lai. Hãy xử lý các booking trước khi khóa phòng.");
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
            throw AppException.conflict("ROOM_HAS_HISTORY","Phòng đã có lịch sử đặt phòng nên không thể xóa. Hãy chuyển trạng thái sang DISABLED.");
        String roomName=r.getName();
        rooms.delete(r);
        notifications.notifyAllEnabledExcept(actor.getId(),NotificationType.ROOM_DELETED,"Meeting room removed",
                actor.getFullName()+" removed "+roomName+".","ROOM",id,"/rooms");
        socket.publish("ROOM_CHANGED");
    }
    private void requireRoomManager(User actor){
        if(actor==null || !actor.getRole().canManageRooms())
            throw AppException.forbidden("Bạn không có quyền quản lý phòng họp");
    }
    private void apply(MeetingRoom r,RoomRequest req){
        r.setCode(req.code().trim().toUpperCase());r.setName(req.name().trim());r.setLocation(req.location().trim());r.setCapacity(req.capacity());
        r.setDescription(req.description());r.setAmenities(req.amenities());r.setStatus(req.status());
    }
}
