package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.MailSettings;
import org.bsl.meetingroom.repository.*;
import org.bsl.meetingroom.exception.AppException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingRecipientsConfigTest {
    @Test void extraRecipientsAreNormalizedAndDeduplicated() {
        MailSettingsRepository settings=mock(MailSettingsRepository.class);
        MailSettings state=new MailSettings();
        when(settings.findById("main")).thenReturn(Optional.of(state));
        MailConfigService svc=new MailConfigService(mock(MailSenderIdentityRepository.class),settings,
            mock(MeetingRoomRepository.class),mock(UserRepository.class),mock(SmtpCredentialCipher.class));
        assertEquals(List.of("manager@example.com"),svc.updateBookingRecipients(
            List.of("Manager@Example.com","manager@example.com")));
        verify(settings).save(state);
    }
    @Test void rejectsInvalidRecipient() {
        MailConfigService svc=new MailConfigService(mock(MailSenderIdentityRepository.class),mock(MailSettingsRepository.class),
            mock(MeetingRoomRepository.class),mock(UserRepository.class),mock(SmtpCredentialCipher.class));
        assertThrows(AppException.class, () -> svc.updateBookingRecipients(List.of("not-an-email")));
    }
}
