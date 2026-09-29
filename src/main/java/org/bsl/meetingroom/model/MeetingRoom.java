package org.bsl.meetingroom.model;

import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection="meeting_rooms")
public class MeetingRoom {
    @Id private String id;
    @Indexed(unique=true) private String code;
    private String name;
    private String location;
    private int capacity;
    private String description;
    private String amenities;
    private RoomStatus status=RoomStatus.AVAILABLE;
    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
    @Version private Long version;

    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public int getCapacity(){return capacity;} public void setCapacity(int v){capacity=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getAmenities(){return amenities;} public void setAmenities(String v){amenities=v;}
    public RoomStatus getStatus(){return status;} public void setStatus(RoomStatus v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public Long getVersion(){return version;}
}
