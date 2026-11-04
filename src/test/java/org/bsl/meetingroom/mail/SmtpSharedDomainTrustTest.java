package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.MailSenderIdentity;
import org.bsl.meetingroom.security.LdapSslSocketFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpSharedDomainTrustTest {
    private JavaMailSenderImpl makeSender(boolean shareTrust, String encryption) throws Exception {
        MailConfigService config = mock(MailConfigService.class);
        SmtpCredentialCipher cipher = mock(SmtpCredentialCipher.class);
        MailSenderIdentity identity = new MailSenderIdentity();
        identity.setSmtpUsername("alert@example.com");
        identity.setSmtpPasswordEncrypted("encrypted");
        when(cipher.decrypt("encrypted")).thenReturn("secret");
        when(config.smtpHost(identity)).thenReturn("smtp.example.com");
        when(config.smtpPort(identity)).thenReturn(30475);
        when(config.smtpSecurity(identity)).thenReturn(encryption);
        MailDeliveryJob job = new MailDeliveryJob(mock(MongoTemplate.class), config, cipher, Clock.systemUTC());
        Field toggle = MailDeliveryJob.class.getDeclaredField("useDomainTrust");
        toggle.setAccessible(true);
        toggle.setBoolean(job, shareTrust);
        Method sender = MailDeliveryJob.class.getDeclaredMethod("sender", MailSenderIdentity.class);
        sender.setAccessible(true);
        return (JavaMailSenderImpl) sender.invoke(job, identity);
    }

    @Test void starttlsReusesDomainCertificateTrustWithoutDisablingSecurity() throws Exception {
        Properties p = makeSender(true, "STARTTLS").getJavaMailProperties();
        assertTrue(p.get("mail.smtp.ssl.socketFactory") instanceof LdapSslSocketFactory);
        assertEquals("true", p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertNull(p.getProperty("mail.smtp.ssl.trust"));
    }

    @Test void directSslAlsoReusesTheSameVerifiedCertificateTrust() throws Exception {
        Properties p = makeSender(true, "SSL_TLS").getJavaMailProperties();
        assertTrue(p.get("mail.smtp.ssl.socketFactory") instanceof LdapSslSocketFactory);
        assertEquals("true", p.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("false", p.getProperty("mail.smtp.starttls.enable"));
    }

    @Test void explicitOptOutRestoresJvmDefaultTrust() throws Exception {
        Properties p = makeSender(false, "STARTTLS").getJavaMailProperties();
        assertNull(p.get("mail.smtp.ssl.socketFactory"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.checkserveridentity"));
    }
}
