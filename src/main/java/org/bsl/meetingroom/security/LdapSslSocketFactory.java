package org.bsl.meetingroom.security;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;

/**
 * Socket factory used only by JNDI/LDAPS.
 *
 * The delegate can be configured from certificates bundled with the application,
 * so the Domain CA does not need to be imported into the global JVM cacerts file.
 * Web application ports (3001/3003/8081/8083/etc.) do not affect this factory.
 */
public final class LdapSslSocketFactory extends SSLSocketFactory {

    private static volatile SSLSocketFactory delegate =
            (SSLSocketFactory) SSLSocketFactory.getDefault();

    private static volatile boolean customConfigured;

    public LdapSslSocketFactory() {
        // Public no-argument constructor required by the JNDI LDAP provider.
    }

    public static void configure(SSLSocketFactory sslSocketFactory) {
        delegate = Objects.requireNonNull(sslSocketFactory, "sslSocketFactory");
        customConfigured = true;
    }

    public static boolean isCustomConfigured() {
        return customConfigured;
    }

    /**
     * JNDI calls getDefault() on the configured socket-factory class.
     */
    public static SocketFactory getDefault() {
        return new LdapSslSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return delegate.createSocket();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
            throws IOException {
        return delegate.createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return delegate.createSocket(host, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(
            InetAddress address,
            int port,
            InetAddress localAddress,
            int localPort
    ) throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}
