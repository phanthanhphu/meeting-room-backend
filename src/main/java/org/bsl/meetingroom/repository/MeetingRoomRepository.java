package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.MeetingRoom;
import org.bsl.meetingroom.model.RoomStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MeetingRoomRepository extends MongoRepository<MeetingRoom,String>{
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByColor(String color);
    boolean existsByColorAndIdNot(String color, String id);
    boolean existsByEquipmentIdsContaining(String equipmentId);
    List<MeetingRoom> findAllByOrderByNameAsc();
    List<MeetingRoom> findByStatusOrderByNameAsc(RoomStatus status);
}
