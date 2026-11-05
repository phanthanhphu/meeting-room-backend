package org.bsl.meetingroom.mail;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

public final class MailDtos {
    private MailDtos() {}
    // On update a blank smtpPassword means keep the saved password, never clear it.
    public record SenderRequest(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=120) String displayName, boolean enabled,
        @NotBlank @Size(max=254) String smtpUsername,
        @Size(max=1024) String smtpPassword,
        @Size(max=253) String smtpHost, @Min(1) @Max(65535) Integer smtpPort, String smtpSecurity) {}
    public record SenderResponse(String id,String email,String displayName,boolean enabled,boolean defaultSender,
                                 String smtpHost,int smtpPort,String smtpSecurity,String smtpUsername,boolean hasPassword) {}
    public record DefaultSenderRequest(@NotBlank String senderId) {}
    public record RoomMailRequest(String senderId, @NotNull List<@NotBlank String> managerUserIds) {}
    public record RoomMailResponse(String roomId,String senderId,List<String> managerUserIds) {}
    public record OutboxResponse(String id,String bookingId,String eventType,String fromEmail,String recipientEmail,
                                 String subject,String status,int attempts,String lastError,LocalDateTime createdAt,LocalDateTime sentAt) {}
    public record TestMailRequest(@NotBlank String senderId, @Email String recipientEmail) {}
    public record MailRuntimeStatus(boolean enabled, boolean smtpCredentialsConfigured, boolean encryptionKeyConfigured,
                                    String smtpGatewayHost, int smtpGatewayPort, String smtpGatewaySecurity,
                                    long queued, long retrying, long failed, long sending) {}
    public record MailEnabledRequest(boolean enabled) {}
    public record WebsiteLinkRequest(@NotNull @Size(max=2048) String url) {}
    public record WebsiteLinkResponse(String url) {}
    public record BookingRecipientsRequest(@NotNull List<@NotBlank @Email String> emails) {}
    public record BookingRecipientsResponse(List<String> emails) {}
    public record ProcessNowResponse(int attempted, String message) {}
    public record AdminRecipientRequest(@NotBlank @Email @Size(max=254) String email,
        @Size(max=120) String name, boolean enabled) {}
    public record AdminRecipientsRequest(@NotNull @Size(max=30) List<@NotNull @Valid AdminRecipientRequest> recipients) {}
    public record AdminRecipientsResponse(List<AdminRecipientRequest> recipients) {}
    public record NotificationRulesRequest(@NotNull Boolean pending, @NotNull Boolean approved,
                                           @NotNull Boolean rejected, @NotNull Boolean cancelled) {}
    public record NotificationRulesResponse(boolean pending,boolean approved,boolean rejected,boolean cancelled) {}
    public record TemplateEditorRequest(@NotNull java.util.Map<String,String> fields) {}
    public record TemplateEditorResponse(String eventType,String audience,boolean customized,
            java.util.Map<String,String> fields,java.util.Map<String,String> defaults) {}
    public record TemplatePreviewResponse(String eventType,String userSubject,String userHtml,
                                          String adminSubject,String adminHtml) {}
}
