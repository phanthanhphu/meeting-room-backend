package org.bsl.meetingroom.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/** Durable at-least-once mail delivery queue; never contains SMTP credentials. */
@Document(collection = "mail_outbox")
public class MailOutboxMessage {
    @Id private String id;
    @Indexed(unique = true) private String eventKey;
    private String bookingId;
    private String eventType;
    private String senderId;
    private String fromEmail;
    private String fromName;
    private String recipientEmail;
    private String subject;
    private String htmlBody;
    // Optional Outlook invitation MIME part (REQUEST or CANCEL).
    private String calendarMethod;
    private String calendarBody;
    private String status = "QUEUED";
    private int attempts;
    private String lastError;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lockedUntil;
    private LocalDateTime sentAt;
    @CreatedDate private LocalDateTime createdAt;

    public String getId(){return id;} public void setId(String v){id=v;}
    public String getEventKey(){return eventKey;} public void setEventKey(String v){eventKey=v;}
    public String getBookingId(){return bookingId;} public void setBookingId(String v){bookingId=v;}
    public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
    public String getSenderId(){return senderId;} public void setSenderId(String v){senderId=v;}
    public String getFromEmail(){return fromEmail;} public void setFromEmail(String v){fromEmail=v;}
    public String getFromName(){return fromName;} public void setFromName(String v){fromName=v;}
    public String getRecipientEmail(){return recipientEmail;} public void setRecipientEmail(String v){recipientEmail=v;}
    public String getSubject(){return subject;} public void setSubject(String v){subject=v;}
    public String getHtmlBody(){return htmlBody;} public void setHtmlBody(String v){htmlBody=v;}
    public String getCalendarMethod(){return calendarMethod;} public void setCalendarMethod(String v){calendarMethod=v;}
    public String getCalendarBody(){return calendarBody;} public void setCalendarBody(String v){calendarBody=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public int getAttempts(){return attempts;} public void setAttempts(int v){attempts=v;}
    public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;}
    public LocalDateTime getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(LocalDateTime v){nextAttemptAt=v;}
    public LocalDateTime getLockedUntil(){return lockedUntil;} public void setLockedUntil(LocalDateTime v){lockedUntil=v;}
    public LocalDateTime getSentAt(){return sentAt;} public void setSentAt(LocalDateTime v){sentAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
