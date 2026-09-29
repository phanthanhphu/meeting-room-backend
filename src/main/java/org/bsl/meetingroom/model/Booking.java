package org.bsl.meetingroom.model;

import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection="bookings")
@CompoundIndexes({
    @CompoundIndex(name="idx_booking_room_time", def="{'roomId':1,'status':1,'startAt':1,'endAt':1}"),
    @CompoundIndex(name="idx_booking_user_time", def="{'createdById':1,'status':1,'startAt':1,'endAt':1}"),
    @CompoundIndex(name="idx_booking_status_start", def="{'status':1,'startAt':1}")
})
public class Booking {
    @Id private String id;
    private String roomId;
    private String createdById;
    private String createdByAccount;
    private String createdByEmail;
    private String title;
    private String purpose;
    private int attendeeCount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private BookingStatus status=BookingStatus.PENDING;
    private String adminNote;
    private String approvedById;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;
    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
    @Version private Long version;

    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getRoomId(){return roomId;} public void setRoomId(String v){roomId=v;}
    public String getCreatedById(){return createdById;} public void setCreatedById(String v){createdById=v;}
    public String getCreatedByAccount(){return createdByAccount;} public void setCreatedByAccount(String v){createdByAccount=v;}
    public String getCreatedByEmail(){return createdByEmail;} public void setCreatedByEmail(String v){createdByEmail=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getPurpose(){return purpose;} public void setPurpose(String v){purpose=v;}
    public int getAttendeeCount(){return attendeeCount;} public void setAttendeeCount(int v){attendeeCount=v;}
    public LocalDateTime getStartAt(){return startAt;} public void setStartAt(LocalDateTime v){startAt=v;}
    public LocalDateTime getEndAt(){return endAt;} public void setEndAt(LocalDateTime v){endAt=v;}
    public BookingStatus getStatus(){return status;} public void setStatus(BookingStatus v){status=v;}
    public String getAdminNote(){return adminNote;} public void setAdminNote(String v){adminNote=v;}
    public String getApprovedById(){return approvedById;} public void setApprovedById(String v){approvedById=v;}
    public LocalDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(LocalDateTime v){approvedAt=v;}
    public LocalDateTime getRejectedAt(){return rejectedAt;} public void setRejectedAt(LocalDateTime v){rejectedAt=v;}
    public LocalDateTime getCancelledAt(){return cancelledAt;} public void setCancelledAt(LocalDateTime v){cancelledAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public Long getVersion(){return version;}
}
