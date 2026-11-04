package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** AES-256-GCM protects saved SMTP passwords. Key must remain outside the database. */
@Component
public class SmtpCredentialCipher {
    private static final Logger log = LoggerFactory.getLogger(SmtpCredentialCipher.class);
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public SmtpCredentialCipher(@Value("${app.mail.credentials-key:}") String base64Key) {
        byte[] value = null;
        try {
            if (base64Key != null && !base64Key.isBlank()) {
                byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
                if (decoded.length == 32) value = decoded;
            }
        } catch (IllegalArgumentException ignored) { /* invalid key reported in status */ }
        // Server-local persistent key: credentials never need to be put into application.yml.
        // An explicitly provided but invalid environment key must NOT silently generate another key.
        if (base64Key == null || base64Key.isBlank()) value = loadOrCreateServerKey();
        this.key = value;
    }
    private static byte[] loadOrCreateServerKey(){
        Path target = Path.of(System.getProperty("user.home"), ".meeting-room-booking", "smtp-credentials.key");
        try {
            if(Files.notExists(target)) {
                Files.createDirectories(target.getParent());
                try {
                    Files.setPosixFilePermissions(target.getParent(), PosixFilePermissions.fromString("rwx------"));
                } catch (UnsupportedOperationException ignored) { /* Windows user profile has ACLs */ }
                byte[] generated = new byte[32]; new SecureRandom().nextBytes(generated);
                Path temp = Files.createTempFile(target.getParent(), "smtp-key-", ".tmp");
                try {
                    try { Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------")); }
                    catch (UnsupportedOperationException ignored) { /* Windows ACL */ }
                    Files.write(temp, generated, StandardOpenOption.TRUNCATE_EXISTING);
                    try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target); }
                } catch (FileAlreadyExistsException ignored) { /* another instance created the key */ }
                finally { Files.deleteIfExists(temp); }
            }
            if(Files.isSymbolicLink(target)) throw new IllegalStateException("SMTP key cannot be a symlink");
            byte[] key=Files.readAllBytes(target);
            if(key.length!=32) throw new IllegalStateException("Stored SMTP key must be exactly 32 bytes");
            return key;
        } catch(Exception e) {
            log.error("Cannot load or persist SMTP encryption key at {}: {}",target,e.getClass().getSimpleName());
            return null;
        }
    }
    public boolean isReady(){return key != null;}
    public String encrypt(String password) {
        requireKey();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] combined = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
            return "v1:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt SMTP credentials", ex); }
    }
    public String decrypt(String encrypted) {
        requireKey();
        try {
            if (encrypted == null || !encrypted.startsWith("v1:")) throw new IllegalArgumentException("Invalid ciphertext version");
            byte[] data = Base64.getDecoder().decode(encrypted.substring(3));
            if (data.length < 29) throw new IllegalArgumentException("Invalid ciphertext length");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, data, 0, 12));
            return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("SMTP password cannot be decrypted. Check MAIL_CREDENTIALS_KEY.", ex); }
    }
    private void requireKey() {
        if (!isReady()) throw AppException.badRequest("MAIL_ENCRYPTION_KEY_MISSING",
            "Set MAIL_CREDENTIALS_KEY to a persistent Base64-encoded 32-byte key on the backend first.");
    }
}
