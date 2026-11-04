package org.bsl.meetingroom.mail;

import org.junit.jupiter.api.Test;
import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateExpiredException;

import static org.junit.jupiter.api.Assertions.*;

class SmtpTlsDiagnosisTest {
    @Test void detectsUntrustedJavaCa() {
        SSLHandshakeException e = new SSLHandshakeException("PKIX path building failed");
        e.initCause(new CertPathBuilderException("unable to find valid certification path"));
        assertTrue(SmtpTlsDiagnosis.explain(e).contains("PKIX"));
    }
    @Test void detectsHostnameMismatch() {
        SSLHandshakeException e = new SSLHandshakeException("No subject alternative DNS name matching pop3.example.com");
        assertTrue(SmtpTlsDiagnosis.explain(e).contains("hostname mismatch"));
    }
    @Test void detectsExpiredCertificate() {
        SSLHandshakeException e = new SSLHandshakeException("certificate invalid");
        e.initCause(new CertificateExpiredException("NotAfter 2025-01-01"));
        assertTrue(SmtpTlsDiagnosis.explain(e).contains("expired"));
    }
    @Test void detectsTlsProtocolMismatch() {
        assertTrue(SmtpTlsDiagnosis.explain(new SSLHandshakeException("Received fatal alert: handshake_failure"))
            .contains("protocol/cipher"));
    }
    @Test void unrelatedErrorsRemainUnchanged() {
        assertNull(SmtpTlsDiagnosis.explain(new IllegalStateException("invalid credentials")));
    }
}
