package org.bsl.meetingroom.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Each FROM address owns its own encrypted SMTP configuration. */
@Document(collection = "mail_sender_identities")
public class MailSenderIdentity {
    @Id private String id;
    @Indexed(unique = true) private String email;
    private String displayName;
    private boolean enabled = true;
    private String smtpHost;
    private int smtpPort;
    private String smtpSecurity = "SSL_TLS";
    private String smtpUsername;
    // Encrypted with AES-256-GCM and an application key held OUTSIDE MongoDB.
    private String smtpPasswordEncrypted;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getSmtpHost(){return smtpHost;} public void setSmtpHost(String v){smtpHost=v;}
    public int getSmtpPort(){return smtpPort;} public void setSmtpPort(int v){smtpPort=v;}
    public String getSmtpSecurity(){return smtpSecurity;} public void setSmtpSecurity(String v){smtpSecurity=v;}
    public String getSmtpUsername(){return smtpUsername;} public void setSmtpUsername(String v){smtpUsername=v;}
    public String getSmtpPasswordEncrypted(){return smtpPasswordEncrypted;}
    public void setSmtpPasswordEncrypted(String v){smtpPasswordEncrypted=v;}
}
