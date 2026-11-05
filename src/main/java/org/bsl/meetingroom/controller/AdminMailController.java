package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.mail.MailConfigService;
import org.bsl.meetingroom.mail.MailGateway;
import org.bsl.meetingroom.mail.MailDeliveryJob;
import org.bsl.meetingroom.repository.MailOutboxRepository;
import org.bsl.meetingroom.mail.MailOutboxService;
import org.bsl.meetingroom.mail.MailDtos.*;
import org.bsl.meetingroom.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/mail")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailController {
    @org.springframework.beans.factory.annotation.Value("${app.mail.base-url:}")
    private String appUrlFallback;
    private final MailConfigService config;
    private final org.bsl.meetingroom.mail.MailTemplateEditorService templateEditor;
    private final MailOutboxService outbox;
    private final AuthService auth;
    private final MailDeliveryJob deliveryJob;
    private final MailOutboxRepository mailRepository;
    public AdminMailController(MailConfigService config,MailOutboxService outbox,AuthService auth,
                               MailDeliveryJob deliveryJob,MailOutboxRepository mailRepository,
                               org.bsl.meetingroom.mail.MailTemplateEditorService templateEditor){
        this.config=config;this.outbox=outbox;this.auth=auth;
        this.deliveryJob=deliveryJob;this.mailRepository=mailRepository;this.templateEditor=templateEditor;
    }
    @GetMapping("/status") public MailRuntimeStatus status(){
        return new MailRuntimeStatus(config.isMailEnabled(),config.isSmtpConfigured(),config.isEncryptionReady(),
            MailGateway.HOST,MailGateway.PORT,MailGateway.SECURITY,
            mailRepository.countByStatus("QUEUED"),mailRepository.countByStatus("RETRY"),
            mailRepository.countByStatus("FAILED"),mailRepository.countByStatus("SENDING"));
    }
    @PutMapping("/settings") public MailRuntimeStatus updateSettings(@Valid @RequestBody MailEnabledRequest req){
        config.setMailEnabled(req.enabled());return status();
    }
    @GetMapping("/website-link") public WebsiteLinkResponse websiteLink(){
        return new WebsiteLinkResponse(config.bookingWebsiteUrl(appUrlFallback));
    }
    @PutMapping("/website-link") public WebsiteLinkResponse updateWebsiteLink(@Valid @RequestBody WebsiteLinkRequest req){
        config.updateBookingWebsiteUrl(req.url());
        return websiteLink();
    }
    @GetMapping("/senders") public List<SenderResponse> senders(){return config.listSenders();}
    @PostMapping("/senders") public SenderResponse create(@Valid @RequestBody SenderRequest req){return config.createSender(req);}
    @PutMapping("/senders/{id}") public SenderResponse update(@PathVariable String id,@Valid @RequestBody SenderRequest req){return config.updateSender(id,req);}
    @DeleteMapping("/senders/{id}") public void delete(@PathVariable String id){config.deleteSender(id);}
    @PutMapping("/default-sender") public void setDefault(@Valid @RequestBody DefaultSenderRequest req){config.setDefaultSender(req.senderId());}
    @GetMapping("/rooms/{roomId}") public RoomMailResponse room(@PathVariable String roomId){return config.getRoomMail(roomId);}
    @PutMapping("/rooms/{roomId}") public RoomMailResponse updateRoom(@PathVariable String roomId,@Valid @RequestBody RoomMailRequest req){return config.updateRoomMail(roomId,req);}
    @GetMapping("/outbox") public List<OutboxResponse> history(){return outbox.history();}
    @PostMapping("/outbox/{id}/retry") public void retry(@PathVariable String id){outbox.retry(id);}
    @PostMapping("/test") public void test(@Valid @RequestBody TestMailRequest req){outbox.queueTest(req.senderId(),auth.current(),req.recipientEmail());}
    @PostMapping("/connection-test") public void testConnection(@Valid @RequestBody DefaultSenderRequest req){
        deliveryJob.testConnection(req.senderId());
    }
    @GetMapping("/recipients") public BookingRecipientsResponse recipients(){
        return new BookingRecipientsResponse(config.bookingRecipients());
    }
    @PutMapping("/recipients") public BookingRecipientsResponse updateRecipients(@Valid @RequestBody BookingRecipientsRequest req){
        return new BookingRecipientsResponse(config.updateBookingRecipients(req.emails()));
    }
    @GetMapping("/admin-recipients") public AdminRecipientsResponse adminRecipients(){
        return new AdminRecipientsResponse(config.adminRecipients());
    }
    @PutMapping("/admin-recipients") public AdminRecipientsResponse updateAdminRecipients(
            @Valid @RequestBody AdminRecipientsRequest req){
        return new AdminRecipientsResponse(config.updateAdminRecipients(req.recipients()));
    }
    @GetMapping("/notification-rules") public NotificationRulesResponse notificationRules(){return config.notificationRules();}
    @PutMapping("/notification-rules") public NotificationRulesResponse updateNotificationRules(
            @Valid @RequestBody NotificationRulesRequest req){return config.updateNotificationRules(req);}
    @GetMapping("/templates/{event}") public TemplatePreviewResponse template(@PathVariable String event){
        return templateEditor.preview(event,config.bookingWebsiteUrl(appUrlFallback));
    }
    @GetMapping("/templates/{event}/editor/{audience}")
    public TemplateEditorResponse getTemplateEditor(@PathVariable String event,@PathVariable String audience){
        return templateEditor.getEditor(event,audience,config.bookingWebsiteUrl(appUrlFallback));
    }
    @PutMapping("/templates/{event}/editor/{audience}")
    public TemplateEditorResponse saveTemplateEditor(@PathVariable String event,@PathVariable String audience,
            @Valid @RequestBody TemplateEditorRequest req){
        return templateEditor.save(event,audience,req.fields(),config.bookingWebsiteUrl(appUrlFallback));
    }
    @PostMapping("/templates/{event}/preview/{audience}")
    public TemplatePreviewResponse previewDraft(@PathVariable String event,@PathVariable String audience,
            @Valid @RequestBody TemplateEditorRequest req){
        return templateEditor.previewDraft(event,audience,req.fields(),config.bookingWebsiteUrl(appUrlFallback));
    }
    @DeleteMapping("/templates/{event}/editor/{audience}")
    public void resetTemplateEditor(@PathVariable String event,@PathVariable String audience){
        templateEditor.reset(event,audience);
    }
    @PostMapping("/outbox/process") public ProcessNowResponse processNow(){
        int count=deliveryJob.processPendingNow();
        return new ProcessNowResponse(count,count==0?"No due emails, or worker is already processing them.":"Delivery attempted. Check SENT/RETRY/FAILED in history.");
    }
}
