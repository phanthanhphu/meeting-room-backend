package org.bsl.meetingroom.service;

import org.bsl.meetingroom.config.BookingRulesProperties;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final MeetingRoomRepository rooms;
    private final UserRepository users;
    private final BookingHistoryRepository history;
    private final BookingRulesProperties rules;
    private final Clock clock;
    private final ResponseMapper mapper;
    private final MongoRoomApprovalLockService approvalLocks;
    private final AppSocketPublisher socket;
    private final NotificationService notifications;

    public BookingService(BookingRepository bookings,MeetingRoomRepository rooms,UserRepository users,
                          BookingHistoryRepository history,BookingRulesProperties rules,Clock clock,
                          ResponseMapper mapper,MongoRoomApprovalLockService approvalLocks,AppSocketPublisher socket,NotificationService notifications){
        this.bookings=bookings;this.rooms=rooms;this.users=users;this.history=history;this.rules=rules;
        this.clock=clock;this.mapper=mapper;this.approvalLocks=approvalLocks;this.socket=socket;this.notifications=notifications;
    }

    public List<BookingResponse> mine(User user){
        return bookings.findByCreatedByIdOrderByStartAtDesc(user.getId()).stream().map(mapper::booking).toList();
    }
    public List<BookingResponse> pending(){
        return bookings.findByStatusOrderByStartAtAsc(BookingStatus.PENDING).stream().map(mapper::booking).toList();
    }
    public List<BookingResponse> all(){
        return bookings.findAllByOrderByStartAtDesc().stream().map(mapper::booking).toList();
    }
    public List<BookingResponse> report(LocalDate from,LocalDate to,String roomId,String userId,BookingStatus status){
        return bookings.findAllByOrderByStartAtDesc().stream()
                .filter(b->from==null || !b.getStartAt().toLocalDate().isBefore(from))
                .filter(b->to==null || !b.getStartAt().toLocalDate().isAfter(to))
                .filter(b->roomId==null || roomId.isBlank() || Objects.equals(b.getRoomId(),roomId))
                .filter(b->userId==null || userId.isBlank() || Objects.equals(b.getCreatedById(),userId))
                .filter(b->status==null || b.getStatus()==status)
                .map(mapper::booking).toList();
    }
    public BookingResponse get(String id,User actor){
        Booking b=find(id);authorizeView(b,actor);return mapper.booking(b);
    }

    public BookingResponse create(BookingRequest req,User actor){
        if(actor.getRole().isReadOnly()) throw AppException.forbidden("Tài khoản VIEWER chỉ được xem, không được tạo booking");
        MeetingRoom room=findRoom(req.roomId());
        validate(room,req,actor,null,true);
        Booking b=new Booking();
        b.setRoomId(room.getId());
        b.setCreatedById(actor.getId());
        b.setCreatedByAccount(actor.getDomainAccount()!=null && !actor.getDomainAccount().isBlank()?actor.getDomainAccount():actor.getUsername());
        b.setCreatedByEmail(actor.getEmail());
        apply(b,req);
        b.setStatus(BookingStatus.PENDING);
        b=bookings.save(b);
        addHistory(b,actor,BookingAction.CREATED,"Tạo yêu cầu đặt phòng");
        notifications.notifyBookingManagersExcept(actor.getId(),NotificationType.BOOKING_CREATED,"New booking request",
                actor.getFullName()+" requested "+room.getName()+" · "+b.getTitle(),"BOOKING",b.getId(),"/approvals");
        socket.publish("BOOKING_CHANGED");
        return mapper.booking(b);
    }

    public BookingResponse update(String id,BookingRequest req,User actor){
        Booking b=find(id);
        authorizeOwnerOrAdmin(b,actor);
        if(EnumSet.of(BookingStatus.REJECTED,BookingStatus.CANCELLED,BookingStatus.COMPLETED).contains(b.getStatus()))
            throw AppException.badRequest("BOOKING_LOCKED","Booking ở trạng thái này không thể chỉnh sửa");
        if(!b.getStartAt().isAfter(LocalDateTime.now(clock)))
            throw AppException.badRequest("BOOKING_STARTED","Cuộc họp đã bắt đầu hoặc đã qua");

        MeetingRoom room=findRoom(req.roomId());
        User owner=users.findById(b.getCreatedById()).orElseThrow(()->AppException.notFound("Không tìm thấy người tạo booking"));
        validate(room,req,owner,id,true);
        boolean wasApproved=b.getStatus()==BookingStatus.APPROVED;
        b.setRoomId(room.getId());
        apply(b,req);
        if(wasApproved){
            b.setStatus(BookingStatus.PENDING);
            b.setApprovedById(null);
            b.setApprovedAt(null);
            b.setAdminNote(null);
        }
        b=bookings.save(b);
        addHistory(b,actor,BookingAction.UPDATED,wasApproved?"Đã sửa booking đã duyệt; chuyển lại PENDING":"Cập nhật yêu cầu đặt phòng");
        if(actor.getRole().canManageBookings() && !Objects.equals(actor.getId(),owner.getId())){
            notifications.notifyUser(owner.getId(),NotificationType.BOOKING_UPDATED,"Booking updated by Manager",
                    "Your booking "+b.getTitle()+" was updated by "+actor.getFullName()+".","BOOKING",b.getId(),"/my-bookings");
        }
        notifications.notifyBookingManagersExcept(actor.getId(),NotificationType.BOOKING_UPDATED,"Booking updated",
                actor.getFullName()+" updated "+room.getName()+" · "+b.getTitle(),"BOOKING",b.getId(),"/approvals");
        socket.publish("BOOKING_CHANGED");
        return mapper.booking(b);
    }

    public BookingResponse approve(String id,User admin){
        requireBookingManager(admin);
        Booking initial=find(id);
        if(initial.getStatus()!=BookingStatus.PENDING)
            throw AppException.badRequest("NOT_PENDING","Chỉ booking PENDING mới được duyệt");

        String roomId=initial.getRoomId();
        String lockToken=approvalLocks.tryAcquire(roomId,Duration.ofSeconds(15));
        if(lockToken==null)
            throw AppException.conflict("APPROVAL_BUSY","Phòng đang được xử lý phê duyệt bởi một yêu cầu khác. Vui lòng thử lại.");

        try{
            Booking b=find(id); // re-read after obtaining Mongo lock
            if(b.getStatus()!=BookingStatus.PENDING)
                throw AppException.badRequest("NOT_PENDING","Chỉ booking PENDING mới được duyệt");
            MeetingRoom room=findRoom(b.getRoomId());
            if(room.getStatus()!=RoomStatus.AVAILABLE)
                throw AppException.conflict("ROOM_UNAVAILABLE","Phòng hiện không ở trạng thái AVAILABLE");

            LocalDateTime now=LocalDateTime.now(clock);
            if(!b.getStartAt().isAfter(now))
                throw AppException.badRequest("BOOKING_PAST","Không thể duyệt cuộc họp đã bắt đầu hoặc đã qua");
            validateTimeAndCapacity(room,b.getAttendeeCount(),b.getStartAt(),b.getEndAt());
            if(countRoomConflicts(room.getId(),b.getStartAt(),b.getEndAt(),b.getId())>0)
                throw AppException.conflict("ROOM_TIME_CONFLICT","Khung giờ này vừa được booking khác duyệt. Không thể duyệt trùng phòng.");

            b.setStatus(BookingStatus.APPROVED);
            b.setApprovedById(admin.getId());
            b.setApprovedAt(now);
            b.setAdminNote(null);
            b=bookings.save(b);
            addHistory(b,admin,BookingAction.APPROVED,"Phê duyệt booking");
            notifications.notifyUser(b.getCreatedById(),NotificationType.BOOKING_APPROVED,"Booking approved",
                    "Your booking "+b.getTitle()+" at "+room.getName()+" was approved.","BOOKING",b.getId(),"/my-bookings");
            notifications.notifyBookingManagersExcept(admin.getId(),NotificationType.BOOKING_APPROVED,"Booking approved",
                    admin.getFullName()+" approved "+room.getName()+" · "+b.getTitle(),"BOOKING",b.getId(),"/approvals");
            socket.publish("BOOKING_CHANGED");
            return mapper.booking(b);
        }finally{
            approvalLocks.release(roomId,lockToken);
        }
    }

    public BookingResponse reject(String id,String reason,User admin){
        requireBookingManager(admin);
        Booking b=find(id);
        if(b.getStatus()!=BookingStatus.PENDING)
            throw AppException.badRequest("NOT_PENDING","Chỉ booking PENDING mới được từ chối");
        b.setStatus(BookingStatus.REJECTED);
        b.setAdminNote(reason.trim());
        b.setRejectedAt(LocalDateTime.now(clock));
        b=bookings.save(b);
        addHistory(b,admin,BookingAction.REJECTED,reason.trim());
        MeetingRoom rejectedRoom=findRoom(b.getRoomId());
        notifications.notifyUser(b.getCreatedById(),NotificationType.BOOKING_REJECTED,"Booking rejected",
                "Your booking "+b.getTitle()+" at "+rejectedRoom.getName()+" was rejected. Reason: "+reason.trim(),"BOOKING",b.getId(),"/my-bookings");
        notifications.notifyBookingManagersExcept(admin.getId(),NotificationType.BOOKING_REJECTED,"Booking rejected",
                admin.getFullName()+" rejected "+rejectedRoom.getName()+" · "+b.getTitle(),"BOOKING",b.getId(),"/approvals");
        socket.publish("BOOKING_CHANGED");
        return mapper.booking(b);
    }

    public BookingResponse cancel(String id,User actor){
        Booking b=find(id);
        authorizeOwnerOrAdmin(b,actor);
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.APPROVED)
            throw AppException.badRequest("CANNOT_CANCEL","Chỉ booking PENDING hoặc APPROVED mới được hủy");
        if(!b.getStartAt().isAfter(LocalDateTime.now(clock)))
            throw AppException.badRequest("BOOKING_STARTED","Không thể hủy cuộc họp đã bắt đầu hoặc đã qua");
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancelledAt(LocalDateTime.now(clock));
        b=bookings.save(b);
        // Re-read the persisted MongoDB document so the API response always reflects
        // the actual stored status, not a stale in-memory instance.
        b=bookings.findById(b.getId()).orElseThrow(()->AppException.notFound("Không tìm thấy booking sau khi hủy"));
        addHistory(b,actor,BookingAction.CANCELLED,"Hủy booking");
        MeetingRoom cancelledRoom=findRoom(b.getRoomId());
        if(actor.getRole().canManageBookings() && !Objects.equals(actor.getId(),b.getCreatedById())){
            notifications.notifyUser(b.getCreatedById(),NotificationType.BOOKING_CANCELLED,"Booking cancelled by Manager",
                    "Your booking "+b.getTitle()+" at "+cancelledRoom.getName()+" was cancelled by "+actor.getFullName()+".","BOOKING",b.getId(),"/my-bookings");
        } else {
            notifications.notifyBookingManagersExcept(actor.getId(),NotificationType.BOOKING_CANCELLED,"Booking cancelled",
                    actor.getFullName()+" cancelled "+cancelledRoom.getName()+" · "+b.getTitle(),"BOOKING",b.getId(),"/approvals");
        }
        socket.publish("BOOKING_CHANGED");
        return mapper.booking(b);
    }

    public void delete(String id,User actor){
        Booking b=find(id);
        authorizeOwnerOrAdmin(b,actor);
        if(b.getStatus()!=BookingStatus.REJECTED && b.getStatus()!=BookingStatus.CANCELLED)
            throw AppException.badRequest("CANNOT_DELETE","Chỉ booking REJECTED hoặc CANCELLED mới được xóa");
        MeetingRoom deletedRoom=findRoom(b.getRoomId());
        String ownerId=b.getCreatedById();
        String title=b.getTitle();
        history.deleteByBookingId(id);
        bookings.delete(b);
        if(actor.getRole().canManageBookings() && !Objects.equals(actor.getId(),ownerId)){
            notifications.notifyUser(ownerId,NotificationType.BOOKING_DELETED,"Booking removed by Manager",
                    "Your booking "+title+" at "+deletedRoom.getName()+" was removed.","BOOKING",id,"/my-bookings");
        } else {
            notifications.notifyBookingManagersExcept(actor.getId(),NotificationType.BOOKING_DELETED,"Booking removed",
                    actor.getFullName()+" removed "+deletedRoom.getName()+" · "+title,"BOOKING",id,"/approvals");
        }
        socket.publish("BOOKING_CHANGED");
    }

    public List<BookingResponse> calendar(LocalDate from,LocalDate to,User actor){
        if(from==null) from=LocalDate.now(clock);
        if(to==null) to=from.plusDays(6);
        if(to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>31)
            throw AppException.badRequest("DATE_RANGE","Khoảng xem lịch tối đa 31 ngày");
        LocalDateTime start=from.atStartOfDay();
        LocalDateTime end=to.plusDays(1).atStartOfDay();
        Collection<BookingStatus> statuses=List.of(BookingStatus.PENDING,BookingStatus.APPROVED);
        return bookings.findByStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(statuses,end,start)
                .stream()
                .filter(b->actor.getRole().canViewAllBookings() || b.getStatus()==BookingStatus.APPROVED || Objects.equals(b.getCreatedById(),actor.getId()))
                .map(mapper::booking).toList();
    }

    public List<HistoryResponse> history(String id,User actor){
        Booking b=find(id);authorizeView(b,actor);
        return history.findByBookingIdOrderByCreatedAtAsc(id).stream().map(mapper::history).toList();
    }

    private void validate(MeetingRoom room,BookingRequest req,User user,String excludeId,boolean checkUserOverlap){
        if(room.getStatus()!=RoomStatus.AVAILABLE)
            throw AppException.badRequest("ROOM_UNAVAILABLE","Phòng đang Maintenance/Disabled");
        validateTimeAndCapacity(room,req.attendeeCount(),req.startAt(),req.endAt());
        if(countRoomConflicts(room.getId(),req.startAt(),req.endAt(),excludeId)>0)
            throw AppException.conflict("ROOM_TIME_CONFLICT","Phòng đã có lịch APPROVED giao với khung giờ này");
        if(checkUserOverlap && countUserOverlaps(user.getId(),req.startAt(),req.endAt(),excludeId)>0)
            throw AppException.conflict("USER_TIME_CONFLICT","Bạn đã có một booking PENDING/APPROVED giao với khung giờ này");
    }

    private long countRoomConflicts(String roomId,LocalDateTime start,LocalDateTime end,String excludeId){
        if(excludeId==null)
            return bookings.countByRoomIdAndStatusAndStartAtLessThanAndEndAtGreaterThan(roomId,BookingStatus.APPROVED,end,start);
        return bookings.countByRoomIdAndStatusAndStartAtLessThanAndEndAtGreaterThanAndIdNot(roomId,BookingStatus.APPROVED,end,start,excludeId);
    }

    private long countUserOverlaps(String userId,LocalDateTime start,LocalDateTime end,String excludeId){
        List<BookingStatus> statuses=List.of(BookingStatus.PENDING,BookingStatus.APPROVED);
        if(excludeId==null)
            return bookings.countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(userId,statuses,end,start);
        return bookings.countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(userId,statuses,end,start,excludeId);
    }

    private void validateTimeAndCapacity(MeetingRoom room,int attendees,LocalDateTime start,LocalDateTime end){
        LocalDateTime now=LocalDateTime.now(clock);
        if(start==null || end==null || !start.isBefore(end))
            throw AppException.badRequest("INVALID_TIME","Giờ bắt đầu phải nhỏ hơn giờ kết thúc");
        if(!start.isAfter(now))
            throw AppException.badRequest("PAST_TIME","Không thể đặt thời gian đã qua");
        if(!start.toLocalDate().equals(end.toLocalDate()))
            throw AppException.badRequest("SAME_DAY_ONLY","Booking phải bắt đầu và kết thúc trong cùng một ngày");
        if(start.toLocalDate().getDayOfWeek()==DayOfWeek.SUNDAY)
            throw AppException.badRequest("SUNDAY_NOT_ALLOWED","Không cho phép đặt phòng vào Chủ nhật");
        if(start.toLocalTime().isBefore(rules.getBusinessStart()) || end.toLocalTime().isAfter(rules.getBusinessEnd()))
            throw AppException.badRequest("OUTSIDE_BUSINESS_HOURS","Chỉ được đặt phòng trong "+rules.getBusinessStart()+" - "+rules.getBusinessEnd());
        long minutes=Duration.between(start,end).toMinutes();
        if(minutes<rules.getMinDurationMinutes())
            throw AppException.badRequest("DURATION_TOO_SHORT","Thời lượng tối thiểu "+rules.getMinDurationMinutes()+" phút");
        if(minutes>rules.getMaxDurationMinutes())
            throw AppException.badRequest("DURATION_TOO_LONG","Thời lượng tối đa "+rules.getMaxDurationMinutes()+" phút");
        if(start.toLocalDate().isAfter(LocalDate.now(clock).plusDays(rules.getMaxAdvanceDays())))
            throw AppException.badRequest("TOO_FAR_AHEAD","Chỉ được đặt trước tối đa "+rules.getMaxAdvanceDays()+" ngày");
        if(attendees<1 || attendees>room.getCapacity())
            throw AppException.badRequest("CAPACITY_EXCEEDED","Số người phải từ 1 đến sức chứa phòng ("+room.getCapacity()+")");
    }

    private void apply(Booking b,BookingRequest req){
        b.setTitle(req.title().trim());
        b.setPurpose(req.purpose());
        b.setAttendeeCount(req.attendeeCount());
        b.setStartAt(req.startAt());
        b.setEndAt(req.endAt());
    }
    private Booking find(String id){return bookings.findById(id).orElseThrow(()->AppException.notFound("Không tìm thấy booking"));}
    private MeetingRoom findRoom(String id){return rooms.findById(id).orElseThrow(()->AppException.notFound("Không tìm thấy phòng họp"));}
    private void authorizeOwnerOrAdmin(Booking b,User actor){
        if(actor.getRole().isReadOnly())
            throw AppException.forbidden("Tài khoản VIEWER chỉ được xem, không được thay đổi booking");
        if(!actor.getRole().canManageBookings() && !Objects.equals(b.getCreatedById(),actor.getId()))
            throw AppException.forbidden("Bạn không có quyền thao tác booking này");
    }
    private void authorizeView(Booking b,User actor){
        if(actor.getRole().canViewAllBookings()) return;
        if(!Objects.equals(b.getCreatedById(),actor.getId()))
            throw AppException.forbidden("Bạn không có quyền xem booking này");
    }
    private void requireBookingManager(User actor){
        if(actor==null || !actor.getRole().canManageBookings())
            throw AppException.forbidden("Bạn không có quyền quản lý hoặc phê duyệt booking");
    }
    private void addHistory(Booking b,User actor,BookingAction action,String note){
        BookingHistory h=new BookingHistory();
        h.setBookingId(b.getId());h.setActorId(actor.getId());h.setAction(action);h.setNote(note);
        history.save(h);
    }
}
