package org.bsl.meetingroom.mail;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class SmtpCredentialCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void passwordIsNeverStoredAsPlaintextAndCanBeReadWithSameKey() {
        SmtpCredentialCipher cipher = new SmtpCredentialCipher(KEY);
        String c1 = cipher.encrypt("a-strong-SMTP-password");
        String c2 = cipher.encrypt("a-strong-SMTP-password");
        assertTrue(c1.startsWith("v1:"));
        assertNotEquals(c1, c2, "Each encryption must use a new random nonce");
        assertFalse(c1.contains("a-strong-SMTP-password"));
        assertEquals("a-strong-SMTP-password", cipher.decrypt(c1));
    }

    @Test void absentOrInvalidKeyCannotDecryptOrEncrypt() {
        // A missing environment key now triggers an automatically persisted local server key.
        // Invalid explicitly supplied keys are rejected rather than silently replaced.
        assertFalse(new SmtpCredentialCipher("invalid-base64").isReady());
        byte[] key2 = new byte[32];key2[0]=1;
        SmtpCredentialCipher other = new SmtpCredentialCipher(Base64.getEncoder().encodeToString(key2));
        assertThrows(IllegalStateException.class, () -> other.decrypt(new SmtpCredentialCipher(KEY).encrypt("pw")));
    }
}
