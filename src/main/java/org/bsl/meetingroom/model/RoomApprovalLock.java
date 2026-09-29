package org.bsl.meetingroom.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection="room_approval_locks")
public class RoomApprovalLock {
    @Id private String roomId;
    private String token;
    private Instant lockedUntil;

    public String getRoomId(){return roomId;} public void setRoomId(String v){roomId=v;}
    public String getToken(){return token;} public void setToken(String v){token=v;}
    public Instant getLockedUntil(){return lockedUntil;} public void setLockedUntil(Instant v){lockedUntil=v;}
}
