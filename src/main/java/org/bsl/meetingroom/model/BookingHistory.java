package org.bsl.meetingroom.model;

import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection="booking_history")
public class BookingHistory {
    @Id private String id;
    @Indexed private String bookingId;
    private String actorId;
    private BookingAction action;
    private String note;
    @CreatedDate private LocalDateTime createdAt;

    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getBookingId(){return bookingId;} public void setBookingId(String v){bookingId=v;}
    public String getActorId(){return actorId;} public void setActorId(String v){actorId=v;}
    public BookingAction getAction(){return action;} public void setAction(BookingAction v){action=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
