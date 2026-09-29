package org.bsl.meetingroom.service;

import org.bsl.meetingroom.dto.Dtos.DashboardResponse;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class DashboardService {
    private final MeetingRoomRepository rooms; private final BookingRepository bookings; private final UserRepository users; private final Clock clock;
    public DashboardService(MeetingRoomRepository rooms,BookingRepository bookings,UserRepository users,Clock clock){this.rooms=rooms;this.bookings=bookings;this.users=users;this.clock=clock;}
    public DashboardResponse summary(User user){
        LocalDate today=LocalDate.now(clock); LocalDateTime start=today.atStartOfDay(),end=today.plusDays(1).atStartOfDay(); LocalDateTime now=LocalDateTime.now(clock);
        long pending=user.getRole().canViewAllBookings()
                ?bookings.countByStatus(BookingStatus.PENDING)
                :bookings.countByCreatedByIdAndStatus(user.getId(),BookingStatus.PENDING);
        long approvedToday=user.getRole().canViewAllBookings()
                ?bookings.countByStartAtBetweenAndStatus(start,end,BookingStatus.APPROVED)
                :bookings.countByCreatedByIdAndStartAtBetweenAndStatus(user.getId(),start,end,BookingStatus.APPROVED);
        return new DashboardResponse(rooms.count(),pending,approvedToday,
                bookings.countByCreatedByIdAndStatusAndEndAtAfter(user.getId(),BookingStatus.APPROVED,now),
                user.getRole()==Role.ADMIN?users.count():0);
    }
}
