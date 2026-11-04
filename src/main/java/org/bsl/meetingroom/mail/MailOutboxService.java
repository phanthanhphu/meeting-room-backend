package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.mail.MailDtos.OutboxResponse;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MailOutboxService {
    private static final Logger log=LoggerFactory.getLogger(MailOutboxService.class);
    private final MailConfigService config;
    private final MailOutboxRepository outbox;
    private final MeetingRoomRepository rooms;
    private final UserRepository users;
    private final Clock clock;
    private final String appUrl;

    public MailOutboxService(MailConfigService config,MailOutboxRepository outbox,MeetingRoomRepository rooms,
                             UserRepository users,Clock clock,
                             @Value("${app.mail.base-url:}") String appUrl){
        this.config=config;this.outbox=outbox;this.rooms=rooms;this.users=users;this.clock=clock;
        // Only explicitly configured HTTP(S) destinations are included in emails.
        this.appUrl=appUrl!=null && appUrl.matches("(?i)^https?://[^\\s<>]+$")?appUrl.replaceAll("/+$",""):"";
    }
    public void bookingCreated(Booking b){
        safely(()->{
            MeetingRoom room=getRoom(b);
            Optional<MailSenderIdentity> from=config.senderForRoom(room);
            if(from.isEmpty()) {log.warn("No enabled sender configured for room {}",room.getId());return;}
            User requester=users.findById(b.getCreatedById()).orElse(null);
            // Send two independent messages: User gets a safe confirmation, Admin gets approval actions.
            // Equal addresses intentionally get both role-specific versions, not a shared CC.
            String owner=b.getCreatedByEmail()!=null && !b.getCreatedByEmail().isBlank() ?
                    b.getCreatedByEmail() : (requester!=null?requester.getEmail():null);
            enqueue("booking:"+b.getId()+":pending:user",b.getId(),"PENDING",from.get(),owner,
                    BookingMailTemplates.pendingConfirmation(b,room,requester,link("/my-bookings",b.getId())));
            notifyAdmins(b,room,from.get(),"CREATED",
                    BookingMailTemplates.created(b,room,requester,link("/approvals",b.getId())));

        });
    }
    private static void addRecipient(Map<String,String> recipients,String email){
        if(email==null)return;
        String normalized=email.trim().toLowerCase(Locale.ROOT);
        if(normalized.matches("(?i)^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$"))
            recipients.putIfAbsent(normalized,email.trim());
    }
    private void notifyAdmins(Booking b,MeetingRoom room,MailSenderIdentity sender,String event,
                              BookingMailTemplates.Content template){
        if(!config.adminNotificationEnabled(event))return;
        Map<String,String> recipients=new LinkedHashMap<>();
        for(String email:config.bookingRecipients())addRecipient(recipients,email);
        // Room-specific assignees are intentionally included, because they're explicitly assigned as managers.
        for(String userId:new LinkedHashSet<>(room.getAssignedMailManagerIds())){
            users.findById(userId).filter(u->u.isEnabled() && u.getRole()!=null && u.getRole().canManageBookings())
                    .ifPresent(u->addRecipient(recipients,u.getEmail()));
        }
        for(String email:recipients.values()){
            enqueue("booking:"+b.getId()+":"+event.toLowerCase(Locale.ROOT)+":admin:"+email.toLowerCase(Locale.ROOT),
                    b.getId(),event,sender,email,template);
        }
    }
    public void bookingApproved(Booking b,User admin){
        queueUserOutcome(b,admin,"APPROVED");
    }
    public void bookingRejected(Booking b,User admin){
        queueUserOutcome(b,admin,"REJECTED");
    }
    private void queueUserOutcome(Booking b,User admin,String type){
        safely(()->{
            MeetingRoom room=getRoom(b);
            Optional<MailSenderIdentity> from=config.senderForRoom(room);
            if(from.isEmpty()){log.warn("No enabled sender configured for room {}",room.getId());return;}
            User requester=users.findById(b.getCreatedById()).orElse(null);
            // Keep the same original recipient for REQUEST and CANCEL even if
            // the user's directory email changes later.
            String email=b.getCreatedByEmail()!=null && !b.getCreatedByEmail().isBlank() ?
                    b.getCreatedByEmail() : (requester!=null?requester.getEmail():null);
            BookingMailTemplates.Content template="APPROVED".equals(type)?
                    BookingMailTemplates.approved(b,room,requester,admin,link("/my-bookings",b.getId())):
                    BookingMailTemplates.rejected(b,room,requester,admin,link("/my-bookings",b.getId()));
            String calendar = "APPROVED".equals(type) && email != null ?
                    OutlookCalendarInvite.build(b,room,from.get().getEmail(),email,false,clock,link("/my-bookings",b.getId())) : null;
            enqueue("booking:"+b.getId()+":"+type.toLowerCase(Locale.ROOT)+":user",b.getId(),type,from.get(),email,template,
                    calendar==null?null:"REQUEST",calendar);
            BookingMailTemplates.Content adminTemplate="APPROVED".equals(type)?
                    BookingMailTemplates.adminApproved(b,room,requester,admin,link("/approvals",b.getId())):
                    BookingMailTemplates.adminRejected(b,room,requester,admin,link("/approvals",b.getId()));
            notifyAdmins(b,room,from.get(),type,adminTemplate);
        });
    }
    public void bookingCancelled(Booking b,User actor){
        safely(()->{
            MeetingRoom room=getRoom(b);
            Optional<MailSenderIdentity> sender=config.senderForRoom(room);
            if(sender.isEmpty()){log.warn("No enabled sender for cancelled booking {}",b.getId());return;}
            User requester=users.findById(b.getCreatedById()).orElse(null);
            String ownerEmail=b.getCreatedByEmail()!=null && !b.getCreatedByEmail().isBlank() ?
                    b.getCreatedByEmail() : (requester!=null?requester.getEmail():null);
            BookingMailTemplates.Content ownerTemplate=BookingMailTemplates.cancelled(b,room,requester,actor,
                    link("/my-bookings",b.getId()));
            boolean hadInvitation=b.getApprovedAt()!=null;
            String calendar=hadInvitation && ownerEmail!=null ? OutlookCalendarInvite.build(b,room,
                    sender.get().getEmail(),ownerEmail,true,clock,link("/my-bookings",b.getId())) : null;
            enqueue("booking:"+b.getId()+":cancelled:user",b.getId(),"CANCELLED",sender.get(),ownerEmail,
                    ownerTemplate,calendar==null?null:"CANCEL",calendar);
            // Admin notification contains a management-oriented message, never Outlook REQUEST/CANCEL.
            notifyAdmins(b,room,sender.get(),"CANCELLED",
                    BookingMailTemplates.adminCancelled(b,room,requester,actor,link("/approvals",b.getId())));
        });
    }
    private void safely(Runnable action){
        if(!config.isMailEnabled())return;
        try{action.run();}catch(RuntimeException e){
            // An outage of the mail subsystem must not undo successful booking changes.
            log.error("Unable to queue booking mail: {}",e.toString(),e);
        }
    }
    public void queueTest(String senderId,User admin){queueTest(senderId,admin,null);}
    public void queueTest(String senderId,User admin,String recipientOverride){
        if(!config.isMailEnabled())throw AppException.badRequest("MAIL_DISABLED","Enable mail in Email Sender Management first.");
        String target=recipientOverride==null || recipientOverride.isBlank()?admin.getEmail():recipientOverride.trim();
        if(target==null || !target.matches("(?i)^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$") || target.length()>254)
            throw AppException.badRequest("INVALID_TEST_RECIPIENT","Please supply a valid test recipient email.");
        MailSenderIdentity from=config.requireEnabledSender(senderId);
        enqueue("test:"+UUID.randomUUID(),null,"TEST",from,target,BookingMailTemplates.test(admin.getFullName()));
    }
    private void enqueue(String key,String bookingId,String type,MailSenderIdentity from,String email,BookingMailTemplates.Content template){
        enqueue(key,bookingId,type,from,email,template,null,null);
    }
    private void enqueue(String key,String bookingId,String type,MailSenderIdentity from,String email,
                         BookingMailTemplates.Content template,String calendarMethod,String calendarBody){
        if(email==null || !email.trim().matches("(?i)^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$")){
            log.warn("Skipping {} mail; invalid recipient for booking {}",type,bookingId);return;
        }
        MailOutboxMessage m=new MailOutboxMessage();
        m.setEventKey(key);m.setBookingId(bookingId);m.setEventType(type);
        m.setSenderId(from.getId());m.setFromEmail(from.getEmail());m.setFromName(from.getDisplayName());m.setRecipientEmail(email.trim());
        m.setSubject(template.subject());m.setHtmlBody(template.html());
        m.setCalendarMethod(calendarMethod);m.setCalendarBody(calendarBody);
        m.setNextAttemptAt(LocalDateTime.now(clock));
        try{outbox.save(m);}catch(DuplicateKeyException ignored){/* idempotent event */}
    }
    private MeetingRoom getRoom(Booking b){return rooms.findById(b.getRoomId()).orElseThrow(()->AppException.notFound("Room not found."));}
    private String link(String path,String id){
        String base=config.bookingWebsiteUrl(appUrl);
        if(base==null || base.isBlank()) return null;
        base=base.replaceAll("/+$", "");
        return base+path+"?booking="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);
    }
    public List<OutboxResponse> history(){return outbox.findTop100ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();}
    private OutboxResponse toResponse(MailOutboxMessage m){return new OutboxResponse(m.getId(),m.getBookingId(),m.getEventType(),m.getFromEmail(),m.getRecipientEmail(),m.getSubject(),m.getStatus(),m.getAttempts(),m.getLastError(),m.getCreatedAt(),m.getSentAt());}
    public void retry(String id){
        MailOutboxMessage mail=outbox.findById(id).orElseThrow(()->AppException.notFound("Email delivery log not found."));
        if(!"FAILED".equals(mail.getStatus()))throw AppException.badRequest("MAIL_NOT_FAILED","Only permanently failed messages may be retried.");
        mail.setStatus("QUEUED");mail.setAttempts(0);mail.setLastError(null);
        mail.setLockedUntil(null);mail.setNextAttemptAt(LocalDateTime.now(clock));
        outbox.save(mail);
    }
}
