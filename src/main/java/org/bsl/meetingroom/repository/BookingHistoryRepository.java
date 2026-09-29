package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.BookingHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BookingHistoryRepository extends MongoRepository<BookingHistory,String>{
    List<BookingHistory> findByBookingIdOrderByCreatedAtAsc(String bookingId);
    void deleteByBookingId(String bookingId);
}
