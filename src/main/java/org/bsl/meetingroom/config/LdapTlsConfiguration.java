package org.bsl.meetingroom.config;

import jakarta.annotation.PostConstruct;
import org.bsl.meetingroom.security.LdapSslSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configures an SSL socket factory used only by JNDI/LDAPS.
 *
 * Trust sources are combined in this order:
 * 1. The normal JVM trust store.
 * 2. The Windows ROOT certificate store when the backend runs on Windows.
 * 3. Optional certificate files configured by the application.
 *
 * This configuration is completely independent from server.port, the frontend
 * port and Docker port mappings.
 */
@Component
public class LdapTlsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LdapTlsConfiguration.class);

    private final ResourceLoader resourceLoader;

    @Value("${app.ldap.trust.enabled:true}")
    private boolean enabled;

    @Value("${app.ldap.trust.use-jvm-default:true}")
    private boolean useJvmDefault;

    @Value("${app.ldap.trust.use-windows-root:true}")
    private boolean useWindowsRoot;

    @Value("${app.ldap.trust.certificate-locations:}")
    private String certificateLocations;

    public LdapTlsConfiguration(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void configureLdapTrust() {
        if (!enabled) {
            logger.info("Custom LDAPS trust is disabled. The JVM default SSL trust will be used.");
            return;
        }

        try {
            List<X509TrustManager> trustManagers = new ArrayList<>();
            List<String> trustSources = new ArrayList<>();

            if (useJvmDefault) {
                addTrustManagers(null, "JVM default trust store", trustManagers);
                trustSources.add("JVM default trust store");
            }

            if (useWindowsRoot && isWindows()) {
                loadWindowsTrustStores(trustManagers, trustSources);
            }

            int customCertificateCount = loadConfiguredCertificates(trustManagers);
            if (customCertificateCount > 0) {
                trustSources.add(customCertificateCount + " configured certificate(s)");
            }

            if (trustManagers.isEmpty()) {
                throw new IllegalStateException("No X.509 trust manager could be initialized for LDAPS.");
            }

            X509TrustManager compositeTrustManager =
                    new CompositeX509TrustManager(trustManagers);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{compositeTrustManager}, null);

            LdapSslSocketFactory.configure(sslContext.getSocketFactory());

            logger.info(
                    "LDAPS trust initialized successfully from: {}. "
                            + "java.version={}, java.vendor={}, os.name={}, user.name={}. "
                            + "Frontend/backend HTTP ports do not affect LDAPS trust.",
                    String.join(", ", trustSources),
                    System.getProperty("java.version"),
                    System.getProperty("java.vendor"),
                    System.getProperty("os.name"),
                    System.getProperty("user.name")
            );
        } catch (Exception ex) {
            // The portal must still start so System Account login remains available.
            logger.error(
                    "Unable to initialize LDAPS trust. Domain Account login may fail, "
                            + "but System Account login remains available.",
                    ex
            );
        }
    }


    /**
     * Windows tools such as ldp.exe trust certificates from both the current-user
     * and local-machine ROOT stores. Gradle/bootRun may run under a user whose
     * current-user store does not contain the corporate CA, so load both stores.
     *
     * JDK 11+ exposes the explicit CURRENTUSER and LOCALMACHINE names. The legacy
     * Windows-ROOT alias is retained as a fallback for older Windows JDKs.
     */
    private void loadWindowsTrustStores(
            List<X509TrustManager> trustManagers,
            List<String> trustSources
    ) {
        boolean explicitStoreLoaded = false;

        explicitStoreLoaded |= tryAddWindowsStore(
                "Windows-ROOT-CURRENTUSER",
                "Windows ROOT (current user)",
                trustManagers,
                trustSources
        );

        explicitStoreLoaded |= tryAddWindowsStore(
                "Windows-ROOT-LOCALMACHINE",
                "Windows ROOT (local machine)",
                trustManagers,
                trustSources
        );

        if (!explicitStoreLoaded) {
            tryAddWindowsStore(
                    "Windows-ROOT",
                    "Windows ROOT (legacy/current user)",
                    trustManagers,
                    trustSources
            );
        }
    }

    private boolean tryAddWindowsStore(
            String storeType,
            String sourceName,
            List<X509TrustManager> trustManagers,
            List<String> trustSources
    ) {
        try {
            KeyStore windowsStore = KeyStore.getInstance(storeType);
            windowsStore.load(null, null);
            addTrustManagers(windowsStore, sourceName, trustManagers);
            trustSources.add(sourceName);
            return true;
        } catch (Exception ex) {
            logger.warn(
                    "Unable to read {} using keystore type {}: {}",
                    sourceName,
                    storeType,
                    ex.getMessage()
            );
            return false;
        }
    }

    private void addTrustManagers(
            KeyStore keyStore,
            String sourceName,
            List<X509TrustManager> target
    ) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        factory.init(keyStore);

        int before = target.size();
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                target.add((X509TrustManager) trustManager);
            }
        }

        if (target.size() == before) {
            throw new IllegalStateException(sourceName + " did not provide an X.509 trust manager.");
        }

        logger.info("Loaded LDAPS trust source: {}", sourceName);
    }

    private int loadConfiguredCertificates(List<X509TrustManager> target) throws Exception {
        String[] locations = splitLocations(certificateLocations);
        if (locations.length == 0) {
            return 0;
        }

        KeyStore certificateStore = KeyStore.getInstance("JKS");
        certificateStore.load(null, null);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        int certificateCount = 0;

        for (String location : locations) {
            Resource resource = resourceLoader.getResource(location);

            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("LDAPS certificate resource is not readable: {}", location);
                continue;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                Collection<? extends Certificate> certificates =
                        certificateFactory.generateCertificates(inputStream);

                for (Certificate certificate : certificates) {
                    if (!(certificate instanceof X509Certificate)) {
                        continue;
                    }

                    X509Certificate x509Certificate = (X509Certificate) certificate;
                    certificateCount++;
                    String alias = "youngonevn-ldap-ca-" + certificateCount;
                    certificateStore.setCertificateEntry(alias, x509Certificate);

                    logger.info(
                            "Loaded configured LDAPS certificate alias={}, subject={}, issuer={}, sha256={}",
                            alias,
                            x509Certificate.getSubjectX500Principal().getName(),
                            x509Certificate.getIssuerX500Principal().getName(),
                            sha256(x509Certificate)
                    );
                }
            }
        }

        if (certificateCount > 0) {
            addTrustManagers(certificateStore, "configured LDAPS certificate files", target);
        }

        return certificateCount;
    }

    private String[] splitLocations(String rawLocations) {
        if (!StringUtils.hasText(rawLocations)) {
            return new String[0];
        }

        return Arrays.stream(rawLocations.split("[,;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private String sha256(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder value = new StringBuilder();

        for (byte item : digest) {
            if (value.length() > 0) {
                value.append(':');
            }
            value.append(String.format(Locale.ROOT, "%02X", item));
        }

        return value.toString();
    }

    /**
     * Accepts a certificate when any configured trust source accepts it.
     */
    private static final class CompositeX509TrustManager implements X509TrustManager {

        private final List<X509TrustManager> delegates;

        private CompositeX509TrustManager(List<X509TrustManager> delegates) {
            this.delegates = List.copyOf(delegates);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            check(chain, authType, false);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            check(chain, authType, true);
        }

        private void check(X509Certificate[] chain, String authType, boolean server)
                throws CertificateException {
            CertificateException lastFailure = null;

            for (X509TrustManager delegate : delegates) {
                try {
                    if (server) {
                        delegate.checkServerTrusted(chain, authType);
                    } else {
                        delegate.checkClientTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException ex) {
                    lastFailure = ex;
                }
            }

            if (lastFailure != null) {
                throw lastFailure;
            }

            throw new CertificateException("No configured trust source accepted the certificate chain.");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            Map<String, X509Certificate> issuers = new LinkedHashMap<>();

            for (X509TrustManager delegate : delegates) {
                for (X509Certificate certificate : delegate.getAcceptedIssuers()) {
                    String key = certificate.getSubjectX500Principal().getName()
                            + "|" + certificate.getSerialNumber();
                    issuers.putIfAbsent(key, certificate);
                }
            }

            return issuers.values().toArray(new X509Certificate[0]);
        }
    }
}
