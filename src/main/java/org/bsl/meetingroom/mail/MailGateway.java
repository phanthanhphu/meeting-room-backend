package org.bsl.meetingroom.mail;

/** SMTP settings from the verified Youngone test. Credentials stay encrypted in MongoDB.
 * Override these environment variables before starting the service if IT changes the gateway.
 * Unencrypted SMTP AUTH is deliberately not supported. */
public final class MailGateway {
    private MailGateway() {}
    public static final String HOST = env("MAIL_SMTP_HOST", "pop3.youngonevn.com");
    public static final int PORT = Integer.parseInt(env("MAIL_SMTP_PORT", "30475"));
    public static final String SECURITY = env("MAIL_SMTP_SECURITY", "STARTTLS").toUpperCase(java.util.Locale.ROOT);
    static {
        if (!SECURITY.equals("STARTTLS") && !SECURITY.equals("SSL_TLS"))
            throw new IllegalStateException("MAIL_SMTP_SECURITY must be STARTTLS or SSL_TLS; plaintext authentication is blocked.");
    }
    private static String env(String name,String fallback){
        String value=System.getenv(name);
        return value==null || value.isBlank()?fallback:value.trim();
    }
}
