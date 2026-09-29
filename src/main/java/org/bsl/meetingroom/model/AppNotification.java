package org.bsl.meetingroom.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection="notifications")
@CompoundIndex(name="recipient_read_created_idx", def="{'recipientUserId':1,'read':1,'createdAt':-1}")
public class AppNotification {
    @Id private String id;
    @Indexed private String recipientUserId;
    private NotificationType type = NotificationType.DATA_CHANGED;
    private String title;
    private String message;
    private String entityType;
    private String entityId;
    private String actionPath;
    private boolean read = false;
    @CreatedDate private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public String getId(){return id;} public void setId(String v){id=v;}
    public String getRecipientUserId(){return recipientUserId;} public void setRecipientUserId(String v){recipientUserId=v;}
    public NotificationType getType(){return type;} public void setType(NotificationType v){type=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;}
    public String getEntityId(){return entityId;} public void setEntityId(String v){entityId=v;}
    public String getActionPath(){return actionPath;} public void setActionPath(String v){actionPath=v;}
    public boolean isRead(){return read;} public void setRead(boolean v){read=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getReadAt(){return readAt;} public void setReadAt(LocalDateTime v){readAt=v;}
}
