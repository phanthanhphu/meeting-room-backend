package org.bsl.meetingroom.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "mail_settings")
public class MailSettings {
    @Id private String id = "main";
    private String defaultSenderId;
    // Public frontend origin or deployment base path used for email deep links.
    private String websiteUrl;
    public String getWebsiteUrl(){return websiteUrl;}
    public void setWebsiteUrl(String url){websiteUrl=url;}
    // Off until an administrator enables sending after entering valid SMTP accounts.
    private boolean enabled = false;
    private java.util.List<String> bookingNotificationEmails = new java.util.ArrayList<>();
    // Null means a pre-migration document: use the old notification emails as enabled recipients.
    private java.util.List<MailNotificationRecipient> adminNotificationRecipients;
    // Null/missing event keys default to true to preserve previously configured notifications.
    private java.util.Map<String,Boolean> adminNotificationRules;
    public java.util.List<MailNotificationRecipient> getAdminNotificationRecipients(){
        if(adminNotificationRecipients!=null) return adminNotificationRecipients;
        java.util.List<MailNotificationRecipient> migrated=new java.util.ArrayList<>();
        for(String email:getBookingNotificationEmails()) migrated.add(new MailNotificationRecipient(email,"",true));
        return migrated;
    }
    public void setAdminNotificationRecipients(java.util.List<MailNotificationRecipient> value){
        adminNotificationRecipients=new java.util.ArrayList<>(value);
    }
    public boolean adminEventEnabled(String event){
        return adminNotificationRules==null || adminNotificationRules.getOrDefault(event,Boolean.TRUE);
    }
    public void setAdminNotificationRules(java.util.Map<String,Boolean> rules){
        adminNotificationRules=new java.util.LinkedHashMap<>(rules);
    }
    public String getId(){return id;}
    public String getDefaultSenderId(){return defaultSenderId;}
    public void setDefaultSenderId(String v){defaultSenderId=v;}
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean v){enabled=v;}
    public java.util.List<String> getBookingNotificationEmails(){
        return bookingNotificationEmails==null?java.util.List.of():bookingNotificationEmails;
    }
    public void setBookingNotificationEmails(java.util.List<String> emails){bookingNotificationEmails=new java.util.ArrayList<>(emails);}

}
