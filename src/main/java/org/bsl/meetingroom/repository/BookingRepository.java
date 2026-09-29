package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.Booking;
import org.bsl.meetingroom.model.BookingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends MongoRepository<Booking,String>{
    long countByRoomIdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            String roomId, BookingStatus status, LocalDateTime endAt, LocalDateTime startAt);
    long countByRoomIdAndStatusAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
            String roomId, BookingStatus status, LocalDateTime endAt, LocalDateTime startAt, String id);

    long countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
            String userId, Collection<BookingStatus> statuses, LocalDateTime endAt, LocalDateTime startAt);
    long countByCreatedByIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanAndIdNot(
            String userId, Collection<BookingStatus> statuses, LocalDateTime endAt, LocalDateTime startAt, String id);

    List<Booking> findByCreatedByIdOrderByStartAtDesc(String userId);
    List<Booking> findAllByOrderByStartAtDesc();
    List<Booking> findByStatusOrderByStartAtAsc(BookingStatus status);
    List<Booking> findByStatusAndEndAtBefore(BookingStatus status, LocalDateTime time);
    List<Booking> findByStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            Collection<BookingStatus> statuses, LocalDateTime endAt, LocalDateTime startAt);

    long countByRoomIdAndStatusAndEndAtAfter(String roomId,BookingStatus status,LocalDateTime now);
    long countByStatus(BookingStatus status);
    long countByCreatedByIdAndStatus(String userId,BookingStatus status);
    long countByCreatedByIdAndStartAtBetweenAndStatus(String userId,LocalDateTime start,LocalDateTime end,BookingStatus status);
    long countByStartAtBetweenAndStatus(LocalDateTime start,LocalDateTime end,BookingStatus status);
    long countByCreatedByIdAndStatusAndEndAtAfter(String userId,BookingStatus status,LocalDateTime now);
    boolean existsByRoomId(String roomId);
    boolean existsByCreatedById(String createdById);
}
