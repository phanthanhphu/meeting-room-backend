package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.MailSenderIdentity;
import org.bsl.meetingroom.model.MailSettings;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamicMailSettingsTest {
    @Test void toggleMailEnabledPersistsInMongo() {
        var senders=mock(MailSenderIdentityRepository.class);
        var settings=mock(MailSettingsRepository.class);
        var cipher=mock(SmtpCredentialCipher.class);
        var config=new MailConfigService(senders,settings,mock(MeetingRoomRepository.class),mock(UserRepository.class),cipher);
        var account=new MailSenderIdentity();
        account.setEmail("sender@example.com"); account.setSmtpHost("smtp.example.com");
        account.setSmtpPort(465); account.setSmtpUsername("sender@example.com");
        account.setSmtpPasswordEncrypted("v1:fake"); account.setEnabled(true);
        when(cipher.isReady()).thenReturn(true);
        when(cipher.decrypt("v1:fake")).thenReturn("secret");
        when(senders.findAll()).thenReturn(List.of(account));
        when(settings.findById("main")).thenReturn(Optional.empty());
        config.setMailEnabled(true);
        var saved=org.mockito.ArgumentCaptor.forClass(MailSettings.class);
        verify(settings).save(saved.capture());
        assertTrue(saved.getValue().isEnabled());
    }

    @Test void cannotEnableMailWithoutEncryptedSender() {
        var senders=mock(MailSenderIdentityRepository.class);
        var settings=mock(MailSettingsRepository.class);
        var cipher=mock(SmtpCredentialCipher.class);
        var config=new MailConfigService(senders,settings,mock(MeetingRoomRepository.class),mock(UserRepository.class),cipher);
        when(cipher.isReady()).thenReturn(false);
        assertThrows(AppException.class, () -> config.setMailEnabled(true));
        verify(settings,never()).save(any(MailSettings.class));
    }
}
