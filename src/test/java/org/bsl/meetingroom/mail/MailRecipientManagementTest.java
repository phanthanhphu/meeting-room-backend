package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.bsl.meetingroom.mail.MailDtos.*;
import org.bsl.meetingroom.exception.AppException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailRecipientManagementTest {
    private final MailSettingsRepository settings=mock(MailSettingsRepository.class);
    private final MailSettings state=new MailSettings();
    private final MailConfigService svc=new MailConfigService(mock(MailSenderIdentityRepository.class),settings,
        mock(MeetingRoomRepository.class),mock(UserRepository.class),mock(SmtpCredentialCipher.class));
    private void configure(){when(settings.findById("main")).thenReturn(Optional.of(state));}

    @Test void onlyEnabledAdminAddressesAreUsedForNotification(){
        configure();
        svc.updateAdminRecipients(List.of(new AdminRecipientRequest("A@Example.com","IT",true),
            new AdminRecipientRequest("b@example.com","Room Manager",false)));
        assertEquals(List.of("a@example.com"),svc.bookingRecipients());
        assertEquals(2,svc.adminRecipients().size());
        verify(settings).save(state);
    }
    @Test void oldEmailListMigratesWithoutLosingRecipients(){
        configure();
        state.setBookingNotificationEmails(List.of("legacy@example.com"));
        assertEquals(List.of("legacy@example.com"),svc.bookingRecipients());
        assertTrue(svc.notificationRules().pending());
    }
    @Test void disabledRuleIsSavedAndRead(){
        configure();
        svc.updateNotificationRules(new NotificationRulesRequest(true,false,true,false));
        assertTrue(svc.adminNotificationEnabled("CREATED"));
        assertFalse(svc.adminNotificationEnabled("APPROVED"));
        assertFalse(svc.adminNotificationEnabled("CANCELLED"));
    }
    @Test void duplicatesNotAllowedInNewRecipientApi(){
        configure();
        assertThrows(AppException.class,()->svc.updateAdminRecipients(List.of(
            new AdminRecipientRequest("a@example.com","",true),
            new AdminRecipientRequest("A@example.com","",false))));
    }
    @Test void mailPreviewHasDifferentOwnerAndAdminMessages(){
        var preview=BookingMailTemplates.preview("APPROVED");
        assertTrue(preview.userHtml().contains("Accept in the Outlook invitation"));
        assertTrue(preview.adminHtml().contains("administrative") || preview.adminHtml().contains("management"));
        assertNotEquals(preview.userSubject(),preview.adminSubject());
    }
}
