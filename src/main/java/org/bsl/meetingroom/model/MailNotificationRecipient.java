package org.bsl.meetingroom.model;

/** Explicit opt-in administrative recipient. Stored inside the one mail_settings document. */
public class MailNotificationRecipient {
    private String email;
    private String name;
    private boolean enabled = true;
    public MailNotificationRecipient() {}
    public MailNotificationRecipient(String email,String name,boolean enabled){
        this.email=email;this.name=name;this.enabled=enabled;
    }
    public String getEmail(){return email;}
    public void setEmail(String v){email=v;}
    public String getName(){return name;}
    public void setName(String v){name=v;}
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean v){enabled=v;}
}
