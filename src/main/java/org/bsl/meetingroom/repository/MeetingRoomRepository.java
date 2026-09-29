package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.MeetingRoom;
import org.bsl.meetingroom.model.RoomStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MeetingRoomRepository extends MongoRepository<MeetingRoom,String>{
    boolean existsByCodeIgnoreCase(String code);
    List<MeetingRoom> findAllByOrderByNameAsc();
    List<MeetingRoom> findByStatusOrderByNameAsc(RoomStatus status);
}
