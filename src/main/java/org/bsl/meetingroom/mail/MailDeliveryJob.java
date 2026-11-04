package org.bsl.meetingroom.mail;

import jakarta.activation.DataHandler;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.bsl.meetingroom.model.MailOutboxMessage;
import org.bsl.meetingroom.model.MailSenderIdentity;
import org.bsl.meetingroom.model.Booking;
import org.bsl.meetingroom.model.BookingStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.security.LdapSslSocketFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;

/** Uses sender-specific SMTP settings from MongoDB, without restarting the server. */
@Component
@DependsOn("ldapTlsConfiguration")
public class MailDeliveryJob {
    private static final Logger log=LoggerFactory.getLogger(MailDeliveryJob.class);
    private static final int MAX_ATTEMPTS=5;
    private final AtomicBoolean draining=new AtomicBoolean(false);
    private final MongoTemplate mongo;
    private final MailConfigService config;
    private final SmtpCredentialCipher cipher;
    private final Clock clock;
    // Reuse the Domain/LDAPS composite CA trust (JVM + Windows ROOT + configured CA).
    // Set MAIL_TLS_USE_DOMAIN_TRUST=false to use the regular JVM truststore instead.
    @Value("${app.mail.tls.use-domain-trust:true}")
    private boolean useDomainTrust = true;
    public MailDeliveryJob(MongoTemplate mongo,MailConfigService config,SmtpCredentialCipher cipher,Clock clock){
        this.mongo=mongo;this.config=config;this.cipher=cipher;this.clock=clock;
    }
    /** Verify login and TLS without sending an email. Admin-only controller invokes this explicitly. */
    public void testConnection(String senderId){
        MailSenderIdentity account=config.requireEnabledSender(senderId);
        try {
            sender(account).testConnection();
        } catch(Exception ex) {
            throw AppException.badRequest("SMTP_CONNECTION_FAILED",safeError(ex));
        }
    }
    @Scheduled(fixedDelayString="${app.mail.poll-interval-ms:10000}",initialDelay=5000)
    public void sendPending(){
        try { processBatch(20,false); }
        catch (Exception ex) { log.error("Mail queue poll failed: {}", ex.toString(), ex); }
    }
    /** Admin diagnostic action: attempt due messages immediately, without relying on scheduled polling. */
    public int processPendingNow(){
        if(!config.isMailEnabled()) throw AppException.badRequest("MAIL_DISABLED", "Enable email service first.");
        return processBatch(3,true);
    }
    private int processBatch(int limit,boolean forceQueued){
        if(!draining.compareAndSet(false,true))return 0;
        int attempted=0;
        try {
            if(!config.isMailEnabled())return 0;
            for(int i=0;i<limit;i++){
                MailOutboxMessage m=claim(forceQueued);
                if(m==null) break;
                attempted++;
                deliver(m);
            }
            if(attempted>0)log.info("Mail worker processed {} queued message(s)",attempted);
        }finally {draining.set(false);}
        return attempted;
    }
    private MailOutboxMessage claim(boolean forceQueued){
        LocalDateTime now=LocalDateTime.now(clock);
        // Include records created by earlier versions, where nextAttemptAt could be absent/null.
        Criteria due=new Criteria().orOperator(
            Criteria.where("nextAttemptAt").lte(now), Criteria.where("nextAttemptAt").is(null));
        Query q=new Query(new Criteria().orOperator(
            forceQueued ? Criteria.where("status").in(List.of("QUEUED","RETRY")) :
                new Criteria().andOperator(Criteria.where("status").in(List.of("QUEUED","RETRY")),due),
            new Criteria().andOperator(Criteria.where("status").is("SENDING"),
                new Criteria().orOperator(Criteria.where("lockedUntil").lt(now),Criteria.where("lockedUntil").is(null)))));
        q.with(org.springframework.data.domain.Sort.by("nextAttemptAt").ascending());
        Update u=new Update().set("status","SENDING").set("lockedUntil",now.plusMinutes(2)).inc("attempts",1);
        return mongo.findAndModify(q,u,FindAndModifyOptions.options().returnNew(true),MailOutboxMessage.class);
    }
    private JavaMailSenderImpl sender(MailSenderIdentity account){
        JavaMailSenderImpl s=new JavaMailSenderImpl();
        s.setHost(config.smtpHost(account));s.setPort(config.smtpPort(account));
        s.setUsername(account.getSmtpUsername());s.setPassword(cipher.decrypt(account.getSmtpPasswordEncrypted()));
        s.setDefaultEncoding("UTF-8");
        Properties p=s.getJavaMailProperties();
        p.put("mail.smtp.auth","true");
        p.put("mail.smtp.connectiontimeout","8000");p.put("mail.smtp.timeout","8000");p.put("mail.smtp.writetimeout","8000");
        boolean ssl="SSL_TLS".equals(config.smtpSecurity(account));
        p.put("mail.smtp.ssl.enable",Boolean.toString(ssl));
        p.put("mail.smtp.ssl.checkserveridentity","true");
        p.put("mail.smtp.starttls.enable",Boolean.toString(!ssl));
        p.put("mail.smtp.starttls.required",Boolean.toString(!ssl));
        // Angus/Jakarta Mail uses the SSLSocketFactory instance for both direct
        // SSL/TLS and STARTTLS (the latter upgrades the existing SMTP socket).
        // LdapSslSocketFactory delegates to the already-initialized LDAPS trust
        // context; it never accepts unknown certificates or disables hostname checks.
        // Use Properties.put(), not setProperty(), because this is an object.
        if (useDomainTrust) {
            p.put("mail.smtp.ssl.socketFactory", new LdapSslSocketFactory());
        }
        return s;
    }
    private void deliver(MailOutboxMessage m){
        try{
            MailSenderIdentity account=config.senderForOutbox(m.getSenderId(),m.getFromEmail());
            // Never deliver an old APPROVED/REQUEST message after the booking was
            // cancelled; a retry otherwise could re-create an already cancelled event.
            if("REQUEST".equals(m.getCalendarMethod()) && m.getBookingId()!=null){
                Booking latest=mongo.findById(m.getBookingId(),Booking.class);
                if(latest==null || latest.getStatus()!=BookingStatus.APPROVED){
                    log.info("Skipping superseded Outlook invitation for booking {}",m.getBookingId());
                    updateIfClaimed(m,"SUPERSEDED",null,null);
                    return;
                }
            }
            JavaMailSenderImpl mail=sender(account);
            MimeMessage mime=mail.createMimeMessage();
            MimeMessageHelper helper=new MimeMessageHelper(mime,false,"UTF-8");
            helper.setFrom(new InternetAddress(m.getFromEmail(),m.getFromName(),"UTF-8"));
            helper.setTo(m.getRecipientEmail());helper.setSubject(m.getSubject());
            if(m.getCalendarBody()!=null && !m.getCalendarBody().isBlank()
                    && ("REQUEST".equals(m.getCalendarMethod()) || "CANCEL".equals(m.getCalendarMethod()))){
                // An HTML button alone does not create Outlook Accept/Tentative/Decline.
                // RFC 5545 text/calendar in multipart/alternative does.
                MimeMultipart alternative = new MimeMultipart("alternative");
                MimeBodyPart plain=new MimeBodyPart();
                plain.setText(m.getSubject()+"\nOpen Meeting Room Booking for details.","UTF-8");
                alternative.addBodyPart(plain);
                // The HTML and its CID icon must be one related MIME part.
                // This also preserves the Outlook calendar alternative below.
                MimeBodyPart brandedHtml=new MimeBodyPart();
                brandedHtml.setContent(htmlWithEmbeddedLogo(m.getHtmlBody()));
                alternative.addBodyPart(brandedHtml);
                MimeBodyPart calendar=new MimeBodyPart();
                String calendarType="text/calendar; charset=UTF-8; method="+m.getCalendarMethod()+"; name=invite.ics";
                calendar.setDataHandler(new DataHandler(new ByteArrayDataSource(
                        m.getCalendarBody().getBytes(java.nio.charset.StandardCharsets.UTF_8),calendarType)));
                calendar.setDisposition(Part.INLINE);
                calendar.setHeader("Content-Class","urn:content-classes:calendarmessage");
                alternative.addBodyPart(calendar);
                mime.setContent(alternative);
                mime.saveChanges();
            } else {
                // Inline logo works in plain notification emails as well as
                // APPROVED/CANCELLED Outlook calendar invitations.
                mime.setContent(htmlWithEmbeddedLogo(m.getHtmlBody()));
                mime.saveChanges();
            }
            mail.send(mime);
            updateIfClaimed(m,"SENT",null,null);
        }catch(Exception e){
            boolean maxed=m.getAttempts()>=MAX_ATTEMPTS;
            long delay=(long)Math.pow(2,Math.max(0,m.getAttempts()-1));
            // Do not expose SMTP credentials or server debug protocol in UI/history.
            String error=e.getClass().getSimpleName()+": "+safeError(e);
            log.warn("Email delivery failed for event {}, attempt {}: {}",m.getEventKey(),m.getAttempts(),safeError(e));
            updateIfClaimed(m,maxed?"FAILED":"RETRY",error.substring(0,Math.min(300,error.length())),
                maxed?null:LocalDateTime.now(clock).plusMinutes(delay));
        }
    }
    /** multipart/related: HTML first, then the local PNG with matching Content-ID. */
    private static MimeMultipart htmlWithEmbeddedLogo(String htmlBody) throws Exception {
        MimeMultipart related = new MimeMultipart("related");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
        related.addBodyPart(htmlPart);
        if (htmlBody != null && htmlBody.contains("cid:" + BookingMailTemplates.BRAND_ICON_CID)) {
            MimeBodyPart logoPart = new MimeBodyPart();
            logoPart.setDataHandler(new DataHandler(new ByteArrayDataSource(
                    BookingMailTemplates.brandIconBytes(), "image/png")));
            logoPart.setHeader("Content-ID", "<" + BookingMailTemplates.BRAND_ICON_CID + ">");
            logoPart.setDisposition(Part.INLINE);
            related.addBodyPart(logoPart);
        }
        return related;
    }

    private String safeError(Exception e){
        String tlsExplanation=SmtpTlsDiagnosis.explain(e);
        if(tlsExplanation!=null)return tlsExplanation;
        Throwable cause=e;
        for (int i=0; cause!=null && i<8; i++,cause=cause.getCause()) {
            if (cause instanceof jakarta.mail.AuthenticationFailedException)
                return "SMTP authentication rejected; check account and password.";
            if (cause instanceof java.net.UnknownHostException)
                return "SMTP DNS lookup failed; check the configured company SMTP host.";
            if (cause instanceof java.net.ConnectException)
                return "SMTP connection refused; check firewall and company SMTP port.";
            if (cause instanceof java.net.SocketTimeoutException)
                return "SMTP connection timed out; check server network/firewall.";
        }
        if(e instanceof IllegalStateException) return "Sender configuration unavailable or cannot decrypt credentials.";
        return "SMTP connection/delivery failed; verify host, port, TLS and server access.";
    }
    private void updateIfClaimed(MailOutboxMessage m,String status,String error,LocalDateTime next){
        Query q=new Query(Criteria.where("_id").is(m.getId()).and("status").is("SENDING").and("attempts").is(m.getAttempts()));
        Update u=new Update().set("status",status).set("lastError",error).set("lockedUntil",null).set("nextAttemptAt",next);
        if("SENT".equals(status))u.set("sentAt",LocalDateTime.now(clock));
        mongo.updateFirst(q,u,MailOutboxMessage.class);
    }
}
