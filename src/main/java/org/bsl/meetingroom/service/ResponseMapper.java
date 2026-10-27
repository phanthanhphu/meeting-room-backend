package org.bsl.meetingroom.service;

import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ResponseMapper {
    private final MeetingRoomRepository rooms;
    private final UserRepository users;

    public ResponseMapper(MeetingRoomRepository rooms,UserRepository users){this.rooms=rooms;this.users=users;}

    public static UserResponse user(User u){
        return u==null?null:new UserResponse(u.getId(),u.getUsername(),u.getEmail(),u.getFullName(),u.getDepartment(),u.getDomainAccount(),
                u.getAccountSource()==null?AccountSource.SYSTEM:u.getAccountSource(),u.getRole(),u.isEnabled());
    }
    public static RoomResponse room(MeetingRoom r){
        return new RoomResponse(r.getId(),r.getCode(),r.getName(),r.getLocation(),r.getCapacity(),r.getDescription(),r.getAmenities(),r.getStatus(),r.getEquipmentIds());
    }
    /** Resolve unique room/user IDs once for each booking list, preserving response fields. */
    public List<BookingResponse> bookings(List<Booking> rows) {
        if (rows.isEmpty()) return List.of();
        Set<String> roomIds = rows.stream().map(Booking::getRoomId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> userIds = new HashSet<>();
        rows.forEach(b -> { if (b.getCreatedById()!=null) userIds.add(b.getCreatedById());
                            if (b.getApprovedById()!=null) userIds.add(b.getApprovedById()); });
        Map<String,MeetingRoom> roomMap = new HashMap<>();
        rooms.findAllById(roomIds).forEach(item -> roomMap.put(item.getId(),item));
        Map<String,User> userMap = new HashMap<>();
        users.findAllById(userIds).forEach(item -> userMap.put(item.getId(),item));
        return rows.stream().map(b -> booking(b,roomMap.get(b.getRoomId()),userMap.get(b.getCreatedById()),
                userMap.get(b.getApprovedById()))).toList();
    }

    public BookingResponse booking(Booking b){
        MeetingRoom room=rooms.findById(b.getRoomId()).orElse(null);
        User creator=users.findById(b.getCreatedById()).orElse(null);
        User approver=b.getApprovedById()==null?null:users.findById(b.getApprovedById()).orElse(null);
        return booking(b,room,creator,approver);
    }
    private BookingResponse booking(Booking b,MeetingRoom room,User creator,User approver){
        String account=(b.getCreatedByAccount()==null || b.getCreatedByAccount().isBlank())?(creator==null?null:(creator.getDomainAccount()==null || creator.getDomainAccount().isBlank()?creator.getUsername():creator.getDomainAccount())):b.getCreatedByAccount();
        String email=(b.getCreatedByEmail()==null || b.getCreatedByEmail().isBlank())?(creator==null?null:creator.getEmail()):b.getCreatedByEmail();
        return new BookingResponse(b.getId(),room==null?null:room(room),user(creator),account,email,b.getTitle(),b.getPurpose(),b.getAttendeeCount(),
                b.getStartAt(),b.getEndAt(),b.getStatus(),b.getAdminNote(),user(approver),b.getApprovedAt(),b.getRejectedAt(),
                b.getCancelledAt(),b.getCreatedAt(),b.getUpdatedAt());
    }
    public HistoryResponse history(BookingHistory h){
        User actor=users.findById(h.getActorId()).orElse(null);
        return new HistoryResponse(h.getId(),h.getAction(),user(actor),h.getNote(),h.getCreatedAt());
    }
}
