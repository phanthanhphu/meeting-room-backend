package org.bsl.meetingroom.mail;

import javax.net.ssl.SSLException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Locale;

/**
 * Converts TLS handshake failures to actionable, non-secret diagnostics.
 * Does not disable TLS/hostname checks or expose the raw SMTP transcript.
 */
final class SmtpTlsDiagnosis {
    private SmtpTlsDiagnosis() { }

    static String explain(Throwable error) {
        boolean tls = false;
        boolean untrusted = false;
        boolean hostname = false;
        boolean expired = false;
        boolean notYetValid = false;
        boolean protocol = false;

        Throwable current = error;
        for (int i = 0; current != null && i < 16; i++, current = current.getCause()) {
            tls |= current instanceof SSLException;
            untrusted |= current instanceof CertPathBuilderException
                    || current instanceof CertPathValidatorException;
            expired |= current instanceof CertificateExpiredException;
            notYetValid |= current instanceof CertificateNotYetValidException;
            String message = current.getMessage();
            if (message == null) continue;
            String lower = message.toLowerCase(Locale.ROOT);
            untrusted |= lower.contains("pkix") || lower.contains("certpath")
                    || lower.contains("unable to find valid certification path")
                    || lower.contains("trustanchors parameter")
                    || lower.contains("unknown_ca") || lower.contains("unknown ca");
            hostname |= lower.contains("no name matching") || lower.contains("no subject alternative")
                    || lower.contains("hostname verification") || lower.contains("name does not match")
                    || lower.contains("certificate does not match")
                    || lower.contains("no matching dns") || lower.contains("not valid for");
            expired |= lower.contains("certificate expired") || lower.contains("notafter")
                    || lower.contains("certificate has expired");
            notYetValid |= lower.contains("certificate not yet valid") || lower.contains("notbefore");
            protocol |= lower.contains("protocol_version") || lower.contains("no appropriate protocol")
                    || lower.contains("no cipher suites in common") || lower.contains("handshake_failure")
                    || lower.contains("unsupported protocol");
        }
        if (!tls) return null;
        if (hostname) return "SMTP TLS hostname mismatch: the server certificate does not match the configured SMTP host. Ask IT for the correct SMTP hostname/certificate.";
        if (expired) return "SMTP TLS certificate expired. Ask IT to renew the SMTP server certificate.";
        if (notYetValid) return "SMTP TLS certificate not yet valid. Check server/JVM clocks and certificate dates.";
        if (untrusted) return "SMTP TLS certificate chain not trusted by this Java runtime (PKIX). Ensure the SMTP CA is in the active trust sources (Domain/Windows or JVM), or ask IT for its Root/Intermediate CA.";
        if (protocol) return "SMTP TLS negotiation failed (protocol/cipher mismatch). Check Java version and SMTP server TLS support.";
        return "SMTP TLS handshake failed. Run SmtpTlsProbe.java using the same JDK as the backend to inspect the underlying cause.";
    }
}
