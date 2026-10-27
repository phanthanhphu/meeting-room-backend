package org.bsl.meetingroom.service;

import org.bsl.meetingroom.config.BookingRulesProperties;
import org.bsl.meetingroom.common.constants.BookingLockConstants;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final MongoTemplate mongo;
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
                          ResponseMapper mapper,MongoRoomApprovalLockService approvalLocks,AppSocketPublisher socket,NotificationService notifications, MongoTemplate mongo){
        this.bookings=bookings;this.rooms=rooms;this.users=users;this.history=history;this.rules=rules;
        this.clock=clock;this.mapper=mapper;this.mongo=mongo;this.approvalLocks=approvalLocks;this.socket=socket;this.notifications=notifications;
    }

    public List<BookingResponse> mine(User user){
        return mapper.bookings(bookings.findByCreatedByIdOrderByStartAtDesc(user.getId()));
    }
    public List<BookingResponse> pending(){
        return mapper.bookings(bookings.findByStatusOrderByStartAtAsc(BookingStatus.PENDING));
    }
    public List<BookingResponse> all(){
        return mapper.bookings(bookings.findAllByOrderByStartAtDesc());
    }
    public List<BookingResponse> report(LocalDate from,LocalDate to,String roomId,String userId,BookingStatus status){
        if (from!=null && to!=null && to.isBefore(from))
            throw AppException.badRequest("DATE_RANGE", "End date must be on or after start date.");
        List<Criteria> filters=new ArrayList<>();
        if(from!=null || to!=null){
            Criteria dates=Criteria.where("startAt");
            if(from!=null) dates=dates.gte(from.atStartOfDay());
            if(to!=null) dates=dates.lt(to.plusDays(1).atStartOfDay());
            filters.add(dates);
        }
        if(roomId!=null && !roomId.isBlank()) filters.add(Criteria.where("roomId").is(roomId));
        if(userId!=null && !userId.isBlank()) filters.add(Criteria.where("createdById").is(userId));
        if(status!=null) filters.add(Criteria.where("status").is(status));
        Query query=new Query();
        if(!filters.isEmpty()) query.addCriteria(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        query.with(Sort.by(Sort.Direction.DESC,"startAt"));
        return mapper.bookings(mongo.find(query,Booking.class));
    }
    public BookingResponse get(String id,User actor){
        Booking b=find(id);authorizeView(b,actor);return mapper.booking(b);
    }

    public BookingResponse create(BookingRequest req,User actor){
        return withUserBookingLock(actor.getId(), () -> createLocked(req, actor));
    }
    private BookingResponse createLocked(BookingRequest req,User actor){
        if(actor.getRole().isReadOnly()) throw AppException.forbidden("VIEWER accounts cannot create bookings.");
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
        addHistory(b,actor,BookingAction.CREATED,"Booking request created");
        notifications.notifyBookingManagersExcept(actor.getId(),NotificationType.BOOKING_CREATED,"New booking request",
                actor.getFullName()+" requested "+room.getName()+" · "+b.getTitle(),"BOOKING",b.getId(),"/approvals");
        socket.publish("BOOKING_CHANGED");
        return mapper.booking(b);
    }

    public BookingResponse update(String id,BookingRequest req,User actor){
        Booking initial=find(id);
        authorizeOwnerOrAdmin(initial,actor);
        return withUserBookingLock(initial.getCreatedById(), () -> updateLocked(id,req,actor));
    }
    private BookingResponse updateLocked(String id,BookingRequest req,User actor){
        Booking b=find(id);
        authorizeOwnerOrAdmin(b,actor);
        if(b.getStatus()!=BookingStatus.PENDING)
            throw AppException.conflict("BOOKING_LOCKED", "Only PENDING bookings can be edited. Approved bookings cannot be changed; a manager may cancel them.");
        if(!b.getStartAt().isAfter(LocalDateTime.now(clock)))
            throw AppException.badRequest("BOOKING_STARTED","The meeting has already started or ended.");

        MeetingRoom room=findRoom(req.roomId());
        User owner=users.findById(b.getCreatedById()).orElseThrow(()->AppException.notFound("Booking requester not found."));
        validate(room,req,owner,id,true);
        b.setRoomId(room.getId());
        apply(b,req);
        b=bookings.save(b);
        addHistory(b,actor,BookingAction.UPDATED,"Pending booking updated");
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
        return withUserBookingLock(initial.getCreatedById(), () -> approveLocked(id,admin));
    }
    private BookingResponse approveLocked(String id,User admin){
        requireBookingManager(admin);
        Booking initial=find(id);
        if(initial.getStatus()!=BookingStatus.PENDING)
            throw AppException.badRequest("NOT_PENDING","Only PENDING bookings can be approved.");

        String roomId=initial.getRoomId();
        String lockToken=approvalLocks.tryAcquire(roomId,BookingLockConstants.APPROVAL_LOCK_TTL);
        if(lockToken==null)
            throw AppException.conflict("APPROVAL_BUSY","Another approval is in progress for this room. Please try again.");

        try{
            Booking b=find(id); // re-read after obtaining Mongo lock
            if(b.getStatus()!=BookingStatus.PENDING)
                throw AppException.badRequest("NOT_PENDING","Only PENDING bookings can be approved.");
            MeetingRoom room=findRoom(b.getRoomId());
            if(room.getStatus()!=RoomStatus.AVAILABLE)
                throw AppException.conflict("ROOM_UNAVAILABLE","The room is not available.");

            LocalDateTime now=LocalDateTime.now(clock);
            if(!b.getStartAt().isAfter(now))
                throw AppException.badRequest("BOOKING_PAST","A meeting that has started or ended cannot be approved.");
            validateTimeAndCapacity(room,b.getAttendeeCount(),b.getStartAt(),b.getEndAt());
            if(countUserApprovedConflicts(b.getCreatedById(),b.getStartAt(),b.getEndAt(),b.getId())>0)
                throw AppException.conflict("USER_TIME_CONFLICT", "The requester already has an overlapping approved meeting.");
            if(countRoomConflicts(room.getId(),b.getStartAt(),b.getEndAt(),b.getId())>0)
                throw AppException.conflict("ROOM_TIME_CONFLICT","This time slot has already been approved for another booking.");

            b.setStatus(BookingStatus.APPROVED);
            b.setApprovedById(admin.getId());
            b.setApprovedAt(now);
            b.setAdminNote(null);
            b=bookings.save(b);
            addHistory(b,admin,BookingAction.APPROVED,"Booking approved");
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
            throw AppException.badRequest("NOT_PENDING","Only PENDING bookings can be rejected.");
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
        Booking initial=find(id);
        authorizeOwnerOrAdmin(initial,actor);
        return withUserBookingLock(initial.getCreatedById(), () -> cancelLocked(id,actor));
    }
    private BookingResponse cancelLocked(String id,User actor){
        Booking b=find(id);
        authorizeOwnerOrAdmin(b,actor);
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.APPROVED)
            throw AppException.badRequest("CANNOT_CANCEL","Only PENDING or APPROVED bookings can be cancelled.");
        if(b.getStatus()==BookingStatus.APPROVED && !actor.getRole().canManageBookings())
            throw AppException.forbidden("Only ADMIN or ROOM_MANAGER can cancel an approved booking.");
        if(!b.getStartAt().isAfter(LocalDateTime.now(clock)))
            throw AppException.badRequest("BOOKING_STARTED","A meeting that has started or ended cannot be cancelled.");
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancelledAt(LocalDateTime.now(clock));
        b=bookings.save(b);
        // Re-read the persisted MongoDB document so the API response always reflects
        // the actual stored status, not a stale in-memory instance.
        b=bookings.findById(b.getId()).orElseThrow(()->AppException.notFound("Cancelled booking not found."));
        addHistory(b,actor,BookingAction.CANCELLED,"Booking cancelled");
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
            throw AppException.badRequest("CANNOT_DELETE","Only REJECTED or CANCELLED bookings can be deleted.");
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
            throw AppException.badRequest("DATE_RANGE","Calendar range cannot exceed 31 days.");
        LocalDateTime start=from.atStartOfDay();
        LocalDateTime end=to.plusDays(1).atStartOfDay();
        Collection<BookingStatus> statuses=List.of(BookingStatus.PENDING,BookingStatus.APPROVED,BookingStatus.COMPLETED);
        List<Booking> visible=bookings.findByStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(statuses,end,start)
                .stream()
                .filter(b->actor.getRole().canViewAllBookings() || (b.getStatus()==BookingStatus.APPROVED || b.getStatus()==BookingStatus.COMPLETED) || Objects.equals(b.getCreatedById(),actor.getId()))
                .toList();
        return mapper.bookings(visible);
    }

    public List<HistoryResponse> history(String id,User actor){
        Booking b=find(id);authorizeView(b,actor);
        return history.findByBookingIdOrderByCreatedAtAsc(id).stream().map(mapper::history).toList();
    }

    private void validate(MeetingRoom room,BookingRequest req,User user,String excludeId,boolean checkUserOverlap){
        if(room.getStatus()!=RoomStatus.AVAILABLE)
            throw AppException.badRequest("ROOM_UNAVAILABLE","The room is unavailable (maintenance or disabled).");
        validateTimeAndCapacity(room,req.attendeeCount(),req.startAt(),req.endAt());
        if(countRoomConflicts(room.getId(),req.startAt(),req.endAt(),excludeId)>0)
            throw AppException.conflict("ROOM_TIME_CONFLICT","The room already has an overlapping approved meeting.");
        if(checkUserOverlap && countUserOverlaps(user.getId(),req.startAt(),req.endAt(),excludeId)>0)
            throw AppException.conflict("USER_TIME_CONFLICT","You already have an overlapping pending or approved booking.");
    }

    private long countUserApprovedConflicts(String userId,LocalDateTime start,LocalDateTime end,String excludeId){
        return bookings.countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
                userId,List.of(BookingStatus.APPROVED),end,start,excludeId);
    }

    private <T> T withUserBookingLock(String userId, java.util.function.Supplier<T> action) {
        String lockId="USER:"+userId;
        String token=approvalLocks.tryAcquire(lockId,BookingLockConstants.USER_BOOKING_LOCK_TTL);
        if(token==null) throw AppException.conflict("USER_BOOKING_BUSY", "Another booking operation is in progress for this requester. Please try again.");
        try { return action.get(); }
        finally { approvalLocks.release(lockId,token); }
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
            throw AppException.badRequest("INVALID_TIME","Start time must be before end time.");
        if(!start.isAfter(now))
            throw AppException.badRequest("PAST_TIME","Booking start time must be in the future.");
        if(!start.toLocalDate().equals(end.toLocalDate()))
            throw AppException.badRequest("SAME_DAY_ONLY","Booking must start and end on the same day.");
        if(start.toLocalDate().getDayOfWeek()==DayOfWeek.SUNDAY)
            throw AppException.badRequest("SUNDAY_NOT_ALLOWED","Bookings are not allowed on Sundays.");
        if(start.toLocalTime().isBefore(rules.getBusinessStart()) || end.toLocalTime().isAfter(rules.getBusinessEnd()))
            throw AppException.badRequest("OUTSIDE_BUSINESS_HOURS","Bookings are only allowed between "+rules.getBusinessStart()+" - "+rules.getBusinessEnd());
        long minutes=Duration.between(start,end).toMinutes();
        if(minutes<rules.getMinDurationMinutes())
            throw AppException.badRequest("DURATION_TOO_SHORT","Minimum booking duration is "+rules.getMinDurationMinutes()+" minutes");
        if(minutes>rules.getMaxDurationMinutes())
            throw AppException.badRequest("DURATION_TOO_LONG","Maximum booking duration is "+rules.getMaxDurationMinutes()+" minutes");
        if(start.toLocalDate().isAfter(LocalDate.now(clock).plusDays(rules.getMaxAdvanceDays())))
            throw AppException.badRequest("TOO_FAR_AHEAD","Bookings can only be made up to "+rules.getMaxAdvanceDays()+" days in advance");
        if(attendees<1 || attendees>room.getCapacity())
            throw AppException.badRequest("CAPACITY_EXCEEDED","Attendee count must be between 1 and room capacity ("+room.getCapacity()+")");
    }

    private void apply(Booking b,BookingRequest req){
        b.setTitle(req.title().trim());
        b.setPurpose(req.purpose());
        b.setAttendeeCount(req.attendeeCount());
        b.setStartAt(req.startAt());
        b.setEndAt(req.endAt());
    }
    private Booking find(String id){return bookings.findById(id).orElseThrow(()->AppException.notFound("Booking not found."));}
    private MeetingRoom findRoom(String id){return rooms.findById(id).orElseThrow(()->AppException.notFound("Meeting room not found."));}
    private void authorizeOwnerOrAdmin(Booking b,User actor){
        if(actor.getRole().isReadOnly())
            throw AppException.forbidden("VIEWER accounts cannot change bookings.");
        if(!actor.getRole().canManageBookings() && !Objects.equals(b.getCreatedById(),actor.getId()))
            throw AppException.forbidden("You do not have permission to manage this booking.");
    }
    private void authorizeView(Booking b,User actor){
        if(actor.getRole().canViewAllBookings()) return;
        if(!Objects.equals(b.getCreatedById(),actor.getId()))
            throw AppException.forbidden("You do not have permission to view this booking.");
    }
    private void requireBookingManager(User actor){
        if(actor==null || !actor.getRole().canManageBookings())
            throw AppException.forbidden("You do not have permission to manage or approve bookings.");
    }
    private void addHistory(Booking b,User actor,BookingAction action,String note){
        BookingHistory h=new BookingHistory();
        h.setBookingId(b.getId());h.setActorId(actor.getId());h.setAction(action);h.setNote(note);
        history.save(h);
    }
}
