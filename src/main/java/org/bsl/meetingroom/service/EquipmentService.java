package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EquipmentService {
    private static final Set<String> CATEGORIES=Set.of("DISPLAY","VIDEO","AUDIO","PRESENTATION","CONNECTIVITY","OTHER");
    private final EquipmentRepository equipment;
    private final MeetingRoomRepository rooms;
    private final AppSocketPublisher socket;

    public EquipmentService(EquipmentRepository equipment,MeetingRoomRepository rooms,AppSocketPublisher socket){
        this.equipment=equipment;this.rooms=rooms;this.socket=socket;
    }
    public static EquipmentResponse response(Equipment e){
        return new EquipmentResponse(e.getId(),e.getCode(),e.getName(),e.getCategory(),e.getDescription(),e.getStatus());
    }
    public List<EquipmentResponse> list(){return equipment.findAllByOrderByNameAsc().stream().map(EquipmentService::response).toList();}
    public EquipmentResponse get(String id){return response(find(id));}
    private Equipment find(String id){return equipment.findById(id).orElseThrow(()->AppException.notFound("Không tìm thấy thiết bị"));}

    public EquipmentResponse create(EquipmentRequest req){
        String code=req.code().trim().toUpperCase(Locale.ROOT);
        if(equipment.existsByCodeIgnoreCase(code))throw AppException.conflict("EQUIPMENT_CODE_EXISTS","Mã thiết bị đã tồn tại");
        Equipment item=new Equipment();apply(item,req);EquipmentResponse saved=response(equipment.save(item));
        socket.publish("EQUIPMENT_CHANGED");return saved;
    }
    public EquipmentResponse update(String id,EquipmentRequest req){
        Equipment item=find(id);
        String code=req.code().trim();
        if(!item.getCode().equalsIgnoreCase(code)&&equipment.existsByCodeIgnoreCase(code))
            throw AppException.conflict("EQUIPMENT_CODE_EXISTS","Mã thiết bị đã tồn tại");
        if(req.status()==EquipmentStatus.INACTIVE && item.getStatus()!=EquipmentStatus.INACTIVE && isAssigned(id))
            throw AppException.conflict("EQUIPMENT_IN_USE","Thiết bị đang thuộc phòng họp. Hãy gỡ khỏi các phòng trước khi ngừng sử dụng.");
        apply(item,req);EquipmentResponse saved=response(equipment.save(item));
        socket.publish("EQUIPMENT_CHANGED");socket.publish("ROOM_CHANGED");return saved;
    }
    public void delete(String id){
        Equipment item=find(id);
        if(isAssigned(id))throw AppException.conflict("EQUIPMENT_IN_USE","Thiết bị đang được gán cho phòng họp. Hãy gỡ khỏi các phòng trước khi xóa.");
        equipment.delete(item);socket.publish("EQUIPMENT_CHANGED");
    }
    private boolean isAssigned(String id){return rooms.existsByEquipmentIdsContaining(id);}
    private void apply(Equipment item,EquipmentRequest req){
        String category=req.category().trim().toUpperCase(Locale.ROOT);
        if(!CATEGORIES.contains(category))throw AppException.badRequest("INVALID_EQUIPMENT_CATEGORY","Loại thiết bị không hợp lệ");
        item.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        item.setName(req.name().trim());item.setCategory(category);
        item.setDescription(req.description()==null?"":req.description().trim());item.setStatus(req.status());
    }
}
