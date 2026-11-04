package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.bsl.meetingroom.mail.MailDtos.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class MailConfigService {
    private final MailSenderIdentityRepository senders;
    private final MailSettingsRepository settings;
    private final MeetingRoomRepository rooms;
    private final UserRepository users;
    private final SmtpCredentialCipher cipher;

    public MailConfigService(MailSenderIdentityRepository senders, MailSettingsRepository settings,
                             MeetingRoomRepository rooms,UserRepository users,SmtpCredentialCipher cipher) {
        this.senders=senders;this.settings=settings;this.rooms=rooms;this.users=users;this.cipher=cipher;
    }
    private MailSettings main(){return settings.findById("main").orElseGet(MailSettings::new);}
    private String defaultId(){return main().getDefaultSenderId();}
    /** Prefer the configured web address; MAIL_APP_URL remains a server-side fallback. */
    public String bookingWebsiteUrl(String fallback){
        String saved=main().getWebsiteUrl();
        return saved!=null && !saved.isBlank() ? saved : (fallback==null ? "" : fallback.trim());
    }
    public String updateBookingWebsiteUrl(String input){
        String value=input==null ? "" : input.trim().replaceAll("/+$", "");
        if(!value.isEmpty()){
            try {
                java.net.URI uri=new java.net.URI(value);
                String scheme=uri.getScheme();
                if(scheme==null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))
                        || uri.getHost()==null || uri.getRawUserInfo()!=null
                        || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                        || (uri.getPort()!=-1 && (uri.getPort()<1 || uri.getPort()>65535)))
                    throw new IllegalArgumentException();
                // The app can be hosted at a subpath; do not append a query or fragment.
            } catch (java.net.URISyntaxException | IllegalArgumentException e) {
                throw AppException.badRequest("INVALID_MAIL_WEBSITE_URL", "Enter a valid http(s) web URL without query or fragment.");
            }
        }
        MailSettings doc=main();doc.setWebsiteUrl(value);settings.save(doc);
        return value;
    }
    public boolean isMailEnabled(){return main().isEnabled();}
    public boolean isEncryptionReady(){return cipher.isReady();}
    public boolean isSmtpConfigured(){
        return cipher.isReady() && senders.findAll().stream().anyMatch(s -> {
            if (!usableSender(s)) return false;
            try { cipher.decrypt(s.getSmtpPasswordEncrypted()); return true; }
            catch (IllegalStateException ex) { return false; }
        });
    }
    private boolean usableSender(MailSenderIdentity s){
        return cipher.isReady() && s.isEnabled() && s.getSmtpUsername()!=null && !s.getSmtpUsername().isBlank()
            && s.getSmtpPasswordEncrypted()!=null && !s.getSmtpPasswordEncrypted().isBlank();
    }
    public void setMailEnabled(boolean enabled){
        if (enabled && !isSmtpConfigured()) throw AppException.badRequest("SMTP_NOT_CONFIGURED",
                "Configure at least one enabled SMTP sender and MAIL_CREDENTIALS_KEY before enabling email.");
        MailSettings s=main();s.setEnabled(enabled);settings.save(s);
    }
    private SenderResponse response(MailSenderIdentity s,String defaultId){
        return new SenderResponse(s.getId(),s.getEmail(),s.getDisplayName(),s.isEnabled(),s.getId().equals(defaultId),
                smtpHost(s),smtpPort(s),smtpSecurity(s),s.getSmtpUsername(),
                s.getSmtpPasswordEncrypted()!=null && !s.getSmtpPasswordEncrypted().isBlank());
    }
    public List<SenderResponse> listSenders(){
        String id=defaultId();return senders.findAllByOrderByEmailAsc().stream().map(s->response(s,id)).toList();
    }
    public SenderResponse createSender(SenderRequest req){
        String email=normalize(req.email());
        if(senders.findByEmailIgnoreCase(email).isPresent()) throw AppException.conflict("MAIL_SENDER_EXISTS","Sender address already exists.");
        MailSenderIdentity s=new MailSenderIdentity();
        apply(s,req,true);
        return response(senders.save(s),defaultId());
    }
    public SenderResponse updateSender(String id,SenderRequest req){
        MailSenderIdentity s=findSender(id);
        String email=normalize(req.email());
        senders.findByEmailIgnoreCase(email).filter(other->!id.equals(other.getId())).ifPresent(other->{
            throw AppException.conflict("MAIL_SENDER_EXISTS","Sender address already exists.");
        });
        apply(s,req,false);
        return response(senders.save(s),defaultId());
    }
    private void apply(MailSenderIdentity s,SenderRequest req,boolean creating){
        if(creating && (req.smtpPassword()==null || req.smtpPassword().isBlank()))
            throw AppException.badRequest("SMTP_PASSWORD_REQUIRED","SMTP password is required for a new sender.");
        if(!creating && (s.getSmtpPasswordEncrypted()==null || s.getSmtpPasswordEncrypted().isBlank())
            && (req.smtpPassword()==null || req.smtpPassword().isBlank()))
            throw AppException.badRequest("SMTP_PASSWORD_REQUIRED","Enter SMTP password for this sender.");
        // Never save credentials in plaintext or return them to the client.
        if(req.smtpPassword()!=null && !req.smtpPassword().isBlank())
            s.setSmtpPasswordEncrypted(cipher.encrypt(req.smtpPassword()));
        s.setEmail(normalize(req.email()));s.setDisplayName(req.displayName().trim());s.setEnabled(req.enabled());
        // Per-sender SMTP host/port/security are managed by ADMIN, never from user bookings.
        String host=req.smtpHost()!=null && !req.smtpHost().isBlank() ? req.smtpHost().trim() :
                (s.getSmtpHost()!=null && !s.getSmtpHost().isBlank()?s.getSmtpHost():MailGateway.HOST);
        if(!host.matches("(?i)^[a-z0-9][a-z0-9.-]{0,251}[a-z0-9]$") && !host.equalsIgnoreCase("localhost"))
            throw AppException.badRequest("INVALID_SMTP_HOST","Enter a valid SMTP hostname or IP address.");
        int port=req.smtpPort()!=null ? req.smtpPort() : (s.getSmtpPort()>0?s.getSmtpPort():MailGateway.PORT);
        String security=req.smtpSecurity()!=null && !req.smtpSecurity().isBlank() ?
                req.smtpSecurity().trim().toUpperCase(Locale.ROOT) : smtpSecurity(s);
        if(!security.equals("STARTTLS") && !security.equals("SSL_TLS"))
            throw AppException.badRequest("INVALID_SMTP_SECURITY","Only STARTTLS or SSL_TLS is permitted.");
        s.setSmtpHost(host);s.setSmtpPort(port);s.setSmtpSecurity(security);
        s.setSmtpUsername(req.smtpUsername().trim());
    }
    public void deleteSender(String id){
        MailSenderIdentity s=findSender(id);
        if(id.equals(defaultId())) throw AppException.conflict("MAIL_DEFAULT_SENDER","Select another default sender before deleting this one.");
        if(rooms.findAll().stream().anyMatch(r->id.equals(r.getMailSenderId())))
            throw AppException.conflict("MAIL_SENDER_ASSIGNED","This sender is assigned to a room. Reassign that room first.");
        senders.delete(s);
    }
    public void setDefaultSender(String id){
        requireEnabledSender(id);MailSettings s=main();s.setDefaultSenderId(id);settings.save(s);
    }
    public RoomMailResponse getRoomMail(String roomId){
        MeetingRoom room=findRoom(roomId);return new RoomMailResponse(room.getId(),room.getMailSenderId(),room.getAssignedMailManagerIds());
    }
    public RoomMailResponse updateRoomMail(String roomId,RoomMailRequest req){
        MeetingRoom room=findRoom(roomId);
        if(req.senderId()!=null && !req.senderId().isBlank()) requireEnabledSender(req.senderId());
        Set<String> ids=new LinkedHashSet<>(req.managerUserIds());
        if(ids.size()!=req.managerUserIds().size()) throw AppException.badRequest("DUPLICATE_MANAGER","Repeated manager user in assignment.");
        List<User> selected=users.findAllById(ids);
        if(selected.size()!=ids.size() || selected.stream().anyMatch(u->!u.isEnabled() || !u.getRole().canManageBookings()))
            throw AppException.badRequest("INVALID_MAIL_MANAGER","Only enabled ADMIN or ROOM_MANAGER users can be assigned.");
        room.setMailSenderId(req.senderId()==null || req.senderId().isBlank()?null:req.senderId());
        room.setAssignedMailManagerIds(new ArrayList<>(ids));room=rooms.save(room);
        return new RoomMailResponse(room.getId(),room.getMailSenderId(),room.getAssignedMailManagerIds());
    }
    public Optional<MailSenderIdentity> senderForRoom(MeetingRoom room){
        String roomSenderId=room.getMailSenderId();
        if(roomSenderId!=null && !roomSenderId.isBlank()) return senders.findById(roomSenderId).filter(this::usableSender);
        String globalDefault=defaultId();
        if(globalDefault!=null && !globalDefault.isBlank()) return senders.findById(globalDefault).filter(this::usableSender);
        List<MailSenderIdentity> available=senders.findAllByOrderByEmailAsc().stream().filter(this::usableSender).toList();
        return available.size()==1?Optional.of(available.get(0)):Optional.empty();
    }
    public MailSenderIdentity requireEnabledSender(String id){
        MailSenderIdentity s=findSender(id);
        if(!usableSender(s))throw AppException.badRequest("SMTP_SENDER_UNAVAILABLE","Sender is disabled or SMTP configuration is incomplete.");
        return s;
    }
    public MailSenderIdentity senderForOutbox(String senderId,String fromEmail){
        MailSenderIdentity sender=senderId!=null ? senders.findById(senderId).orElse(null)
                :senders.findByEmailIgnoreCase(fromEmail).orElse(null); // old queued messages
        if(sender==null || !usableSender(sender) || !sender.getEmail().equalsIgnoreCase(fromEmail))
            throw new IllegalStateException("The configured SMTP sender is unavailable or its FROM address changed.");
        return sender;
    }
    public String smtpHost(MailSenderIdentity s){return s.getSmtpHost()==null || s.getSmtpHost().isBlank()?MailGateway.HOST:s.getSmtpHost();}
    public int smtpPort(MailSenderIdentity s){return s.getSmtpPort()>0?s.getSmtpPort():MailGateway.PORT;}
    public String smtpSecurity(MailSenderIdentity s){return s.getSmtpSecurity()==null || s.getSmtpSecurity().isBlank()?MailGateway.SECURITY:s.getSmtpSecurity();}

    public List<AdminRecipientRequest> adminRecipients(){
        return main().getAdminNotificationRecipients().stream()
                .map(r->new AdminRecipientRequest(r.getEmail(),r.getName(),r.isEnabled())).toList();
    }
    public List<AdminRecipientRequest> updateAdminRecipients(List<AdminRecipientRequest> recipients){
        if(recipients.size()>30) throw AppException.badRequest("TOO_MANY_EMAIL_RECIPIENTS","Maximum 30 admin recipients.");
        Map<String,MailNotificationRecipient> unique=new LinkedHashMap<>();
        for(AdminRecipientRequest r:recipients){
            if(r==null)throw AppException.badRequest("INVALID_RECIPIENT","Recipient cannot be empty.");
            String email=normalize(r.email()==null?"":r.email());
            if(!email.matches("(?i)^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$") || email.length()>254)
                throw AppException.badRequest("INVALID_BOOKING_RECIPIENT","Enter a valid email address.");
            if(unique.containsKey(email))throw AppException.badRequest("DUPLICATE_RECIPIENT","Duplicate admin recipient: "+email);
            String name=r.name()==null?"":r.name().trim();
            if(name.length()>120)throw AppException.badRequest("INVALID_RECIPIENT_NAME","Recipient name is too long.");
            unique.put(email,new MailNotificationRecipient(email,name,r.enabled()));
        }
        MailSettings doc=main();
        doc.setAdminNotificationRecipients(new ArrayList<>(unique.values()));
        // Preserve compatibility with the old /recipients endpoint and earlier settings.
        doc.setBookingNotificationEmails(new ArrayList<>(unique.keySet()));
        settings.save(doc);
        return adminRecipientsFrom(doc);
    }
    private List<AdminRecipientRequest> adminRecipientsFrom(MailSettings doc){
        return doc.getAdminNotificationRecipients().stream().map(r->
                new AdminRecipientRequest(r.getEmail(),r.getName(),r.isEnabled())).toList();
    }
    /** Enabled explicit recipients only. Never infer admin recipients from the SMTP FROM address. */
    public List<String> bookingRecipients(){
        return main().getAdminNotificationRecipients().stream().filter(MailNotificationRecipient::isEnabled)
                .map(MailNotificationRecipient::getEmail).toList();
    }
    public List<String> updateBookingRecipients(List<String> emails){
        List<AdminRecipientRequest> records=emails.stream().map(x->new AdminRecipientRequest(x,"",true)).toList();
        // Preserve legacy behavior: duplicate addresses were silently merged.
        Map<String,AdminRecipientRequest> unique=new LinkedHashMap<>();
        for(AdminRecipientRequest r:records) unique.put(normalize(r.email()==null?"":r.email()),r);
        return updateAdminRecipients(new ArrayList<>(unique.values())).stream().map(AdminRecipientRequest::email).toList();
    }
    public NotificationRulesResponse notificationRules(){
        MailSettings doc=main();
        return new NotificationRulesResponse(doc.adminEventEnabled("CREATED"),doc.adminEventEnabled("APPROVED"),
                doc.adminEventEnabled("REJECTED"),doc.adminEventEnabled("CANCELLED"));
    }
    public NotificationRulesResponse updateNotificationRules(NotificationRulesRequest req){
        MailSettings doc=main();
        Map<String,Boolean> rule=new LinkedHashMap<>();
        rule.put("CREATED",req.pending());rule.put("APPROVED",req.approved());
        rule.put("REJECTED",req.rejected());rule.put("CANCELLED",req.cancelled());
        doc.setAdminNotificationRules(rule);settings.save(doc);
        return notificationRulesFrom(doc);
    }
    private NotificationRulesResponse notificationRulesFrom(MailSettings doc){
        return new NotificationRulesResponse(doc.adminEventEnabled("CREATED"),doc.adminEventEnabled("APPROVED"),
                doc.adminEventEnabled("REJECTED"),doc.adminEventEnabled("CANCELLED"));
    }
    public boolean adminNotificationEnabled(String event){return main().adminEventEnabled(event);}
    private MailSenderIdentity findSender(String id){return senders.findById(id).orElseThrow(()->AppException.notFound("Sender not found."));}
    private MeetingRoom findRoom(String id){return rooms.findById(id).orElseThrow(()->AppException.notFound("Room not found."));}
    private String normalize(String email){return email.trim().toLowerCase(Locale.ROOT);}
}
