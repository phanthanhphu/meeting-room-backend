package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailSenderFallbackTest {
    private final MailSenderIdentityRepository senders = mock(MailSenderIdentityRepository.class);
    private final MailSettingsRepository settings = mock(MailSettingsRepository.class);
    private final MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SmtpCredentialCipher cipher = mock(SmtpCredentialCipher.class);
    private final MailConfigService service = new MailConfigService(senders, settings, rooms, users, cipher);

    private MailSenderIdentity sender(String id, String email) {
        MailSenderIdentity sender = new MailSenderIdentity();
        sender.setId(id); sender.setEmail(email); sender.setEnabled(true);
        sender.setSmtpHost("smtp.example.com"); sender.setSmtpPort(465);
        sender.setSmtpUsername(email); sender.setSmtpPasswordEncrypted("encrypted");
        return sender;
    }

    @Test void onlyEnabledSenderIsUsedWhenNoDefaultWasChosen() {
        when(cipher.isReady()).thenReturn(true);
        var sender = sender("s1", "phupt.st@youngonevn.com");
        when(settings.findById("main")).thenReturn(Optional.empty());
        when(senders.findAllByOrderByEmailAsc()).thenReturn(List.of(sender));
        assertEquals(sender.getEmail(), service.senderForRoom(new MeetingRoom()).orElseThrow().getEmail());
    }

    @Test void severalEnabledSendersRequireExplicitSelection() {
        when(cipher.isReady()).thenReturn(true);
        when(settings.findById("main")).thenReturn(Optional.empty());
        when(senders.findAllByOrderByEmailAsc()).thenReturn(List.of(sender("s1", "one@example.com"), sender("s2", "two@example.com")));
        assertTrue(service.senderForRoom(new MeetingRoom()).isEmpty());
    }
}
