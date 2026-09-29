package org.bsl.meetingroom.service;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.repository.BookingRepository;
import org.bsl.meetingroom.repository.MeetingRoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class BookingCompletionJob {
    private final BookingRepository bookings;
    private final MeetingRoomRepository rooms;
    private final Clock clock;
    private final AppSocketPublisher socket;
    private final NotificationService notifications;

    public BookingCompletionJob(BookingRepository bookings,MeetingRoomRepository rooms,Clock clock,AppSocketPublisher socket,NotificationService notifications){
        this.bookings=bookings;this.rooms=rooms;this.clock=clock;this.socket=socket;this.notifications=notifications;
    }

    @Scheduled(fixedDelay=300000)
    public void closeCompleted(){
        boolean changed=false;
        for(Booking b:bookings.findByStatusAndEndAtBefore(BookingStatus.APPROVED,LocalDateTime.now(clock))){
            b.setStatus(BookingStatus.COMPLETED);bookings.save(b);changed=true;
            String roomName=rooms.findById(b.getRoomId()).map(MeetingRoom::getName).orElse("meeting room");
            notifications.notifyUser(b.getCreatedById(),NotificationType.DATA_CHANGED,"Meeting completed",
                    "Your meeting "+b.getTitle()+" at "+roomName+" has been marked completed.","BOOKING",b.getId(),"/my-bookings");
        }
        if(changed) socket.publish("BOOKING_CHANGED");
    }
}
