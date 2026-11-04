package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.MailOutboxMessage;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailWorkerDiagnosticTest {
    @Test void manualProcessingRecordsAnAttemptWhenSenderIsUnavailable() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MailConfigService settings = mock(MailConfigService.class);
        SmtpCredentialCipher cipher = mock(SmtpCredentialCipher.class);
        when(settings.isMailEnabled()).thenReturn(true);
        MailOutboxMessage msg = new MailOutboxMessage();
        msg.setId("mail1"); msg.setEventKey("test1"); msg.setStatus("SENDING");
        msg.setAttempts(1); msg.setSenderId("sender1"); msg.setFromEmail("sender@example.com");
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(MailOutboxMessage.class)))
            .thenReturn(msg).thenReturn(null);
        when(settings.senderForOutbox("sender1","sender@example.com"))
            .thenThrow(new IllegalStateException("Missing sender configuration"));
        Clock clock=Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
        MailDeliveryJob job=new MailDeliveryJob(mongo,settings,cipher,clock);
        assertEquals(1,job.processPendingNow());
        verify(mongo).updateFirst(any(Query.class),any(Update.class),eq(MailOutboxMessage.class));
    }
    @Test void disabledWorkerDoesNotClaimMessages() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MailConfigService settings = mock(MailConfigService.class);
        MailDeliveryJob job=new MailDeliveryJob(mongo,settings,mock(SmtpCredentialCipher.class),Clock.systemUTC());
        job.sendPending();
        verifyNoInteractions(mongo);
    }
}
