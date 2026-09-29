package org.bsl.meetingroom.service;

import org.bsl.meetingroom.exception.DomainAuthenticationException;
import org.bsl.meetingroom.security.LdapSslSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.bsl.meetingroom.exception.DomainAuthenticationException.Reason.CONFIGURATION_ERROR;
import static org.bsl.meetingroom.exception.DomainAuthenticationException.Reason.DISABLED;
import static org.bsl.meetingroom.exception.DomainAuthenticationException.Reason.EMAIL_NOT_FOUND;
import static org.bsl.meetingroom.exception.DomainAuthenticationException.Reason.INVALID_CREDENTIALS;
import static org.bsl.meetingroom.exception.DomainAuthenticationException.Reason.SERVICE_UNAVAILABLE;
import static org.bsl.meetingroom.exception.DomainAuthenticationException.Reason.USER_NOT_FOUND;

/**
 * Authenticates users against Microsoft Active Directory.
 *
 * Default mode is KERBEROS_GSSAPI. The username/password posted by the login page
 * is used to obtain a Kerberos ticket from the AD KDC. The application then uses
 * that ticket for an LDAP SASL GSSAPI bind and reads the user's mail attribute.
 *
 * This mirrors the Kerberos path used by Windows LDAP "Negotiate" without requiring
 * the Portal website to use a Domain hostname or browser integrated authentication.
 * The Portal HTTP host/port is unrelated to this AD authentication flow.
 */
@Service
public class ActiveDirectoryService {

    private static final Logger logger = LoggerFactory.getLogger(ActiveDirectoryService.class);
    private static final Object KERBEROS_CONFIGURATION_LOCK = new Object();
    private static final String JAAS_ENTRY_NAME = "PortalActiveDirectoryKerberos";

    private static final String[] USER_ATTRIBUTES = {
            "mail",
            "userPrincipalName",
            "sAMAccountName",
            "displayName",
            "userAccountControl"
    };

    @Value("${app.ldap.enabled:true}")
    private boolean enabled;

    @Value("${app.ldap.urls:ldaps://VN-DC2.youngonevn.com:636}")
    private String ldapUrls;

    @Value("${app.ldap.base-dn:DC=youngonevn,DC=com}")
    private String configuredBaseDn;

    @Value("${app.ldap.domain:youngonevn.com}")
    private String domain;

    @Value("${app.ldap.netbios-domain:YOUNGONEVN}")
    private String netbiosDomain;

    /**
     * KERBEROS_GSSAPI is the default and corresponds to the Kerberos path of
     * Windows LDAP Negotiate. SIMPLE remains available only as an explicit fallback.
     */
    @Value("${app.ldap.authentication-mode:KERBEROS_GSSAPI}")
    private String authenticationMode;

    @Value("${app.ldap.bare-username-bind-format:NETBIOS}")
    private String bareUsernameBindFormat;

    @Value("${app.kerberos.realm:YOUNGONEVN.COM}")
    private String kerberosRealm;

    @Value("${app.kerberos.kdc:VN-DC2.youngonevn.com}")
    private String kerberosKdc;

    @Value("${app.kerberos.ldap-qop:auth}")
    private String kerberosLdapQop;

    @Value("${app.kerberos.debug:false}")
    private boolean kerberosDebug;

    @Value("${app.ldap.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.ldap.read-timeout-ms:7000}")
    private int readTimeoutMs;

    public DirectoryUser authenticate(String rawIdentifier, String password) {
        validateConfigurationAndInput(rawIdentifier, password);

        DomainIdentity identity = normalizeIdentity(rawIdentifier);
        AuthenticationMode mode = AuthenticationMode.from(authenticationMode);

        if (mode == AuthenticationMode.KERBEROS_GSSAPI) {
            return authenticateWithKerberos(identity, password);
        }

        return authenticateWithSimpleBind(identity, password);
    }

    private DirectoryUser authenticateWithKerberos(DomainIdentity identity, String password) {
        configureKerberosRuntime();

        LoginContext loginContext = null;

        try {
            logger.info(
                    "Attempting AD Kerberos authentication for account {} as principal {} through KDC {}",
                    identity.getSamAccountName(),
                    identity.getKerberosPrincipal(),
                    kerberosKdc
            );

            loginContext = createKerberosLoginContext(identity.getKerberosPrincipal(), password);
            loginContext.login();

            Subject subject = loginContext.getSubject();
            if (subject == null) {
                throw new DomainAuthenticationException(
                        CONFIGURATION_ERROR,
                        "Kerberos authentication succeeded without creating a security subject."
                );
            }

            DirectoryUser user = queryDirectoryWithKerberos(subject, identity);

            logger.info(
                    "AD Kerberos/GSSAPI authentication successful for account {}",
                    identity.getSamAccountName()
            );

            return user;
        } catch (FailedLoginException ex) {
            logger.warn(
                    "AD Kerberos rejected credentials for account {}: {}",
                    identity.getSamAccountName(),
                    sanitizeDiagnostic(ex.getMessage())
            );
            throw new DomainAuthenticationException(
                    INVALID_CREDENTIALS,
                    "Domain username or password is incorrect.",
                    ex
            );
        } catch (LoginException ex) {
            throw classifyKerberosLoginFailure(identity, ex);
        } finally {
            if (loginContext != null) {
                try {
                    loginContext.logout();
                } catch (LoginException ex) {
                    logger.debug("Unable to destroy Kerberos login context cleanly: {}", ex.getMessage());
                }
            }
        }
    }

    private DirectoryUser queryDirectoryWithKerberos(Subject subject, DomainIdentity identity) {
        List<String> urls = parseUrls(ldapUrls);
        if (urls.isEmpty()) {
            throw new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "No Domain Controller LDAP/LDAPS URL has been configured."
            );
        }

        DomainAuthenticationException lastUnavailable = null;

        for (String url : urls) {
            try {
                return Subject.doAs(
                        subject,
                        (PrivilegedExceptionAction<DirectoryUser>) () -> {
                            LdapContext context = null;
                            try {
                                logger.info(
                                        "Attempting LDAP SASL GSSAPI bind for account {} through {}",
                                        identity.getSamAccountName(),
                                        url
                                );
                                context = openGssapiContext(url);
                                return findAuthenticatedUser(context, identity);
                            } finally {
                                closeQuietly(context);
                            }
                        }
                );
            } catch (PrivilegedActionException ex) {
                Throwable cause = ex.getException();

                if (cause instanceof AuthenticationException) {
                    throw new DomainAuthenticationException(
                            INVALID_CREDENTIALS,
                            "Active Directory rejected the Kerberos LDAP authentication.",
                            cause
                    );
                }

                if (cause instanceof CommunicationException) {
                    lastUnavailable = classifyConnectionFailure(url, (NamingException) cause);
                    logger.warn("Cannot connect to Domain Controller {}: {}", url, cause.getMessage());
                    continue;
                }

                if (cause instanceof NamingException) {
                    NamingException namingException = (NamingException) cause;
                    lastUnavailable = classifyGssapiDirectoryFailure(url, namingException);
                    logger.warn("LDAP GSSAPI operation failed against {}: {}", url, namingException.getMessage());
                    continue;
                }

                throw new DomainAuthenticationException(
                        SERVICE_UNAVAILABLE,
                        "Unable to query Active Directory after Kerberos authentication.",
                        cause
                );
            } catch (DomainAuthenticationException ex) {
                throw ex;
            }
        }

        if (lastUnavailable != null) {
            throw lastUnavailable;
        }

        throw new DomainAuthenticationException(
                SERVICE_UNAVAILABLE,
                "Unable to connect to the Domain authentication service."
        );
    }

    private LoginContext createKerberosLoginContext(String principal, String password) throws LoginException {
        PasswordCallbackHandler callbackHandler = new PasswordCallbackHandler(principal, password);

        Map<String, Object> options = new HashMap<>();
        options.put("principal", principal);
        options.put("useTicketCache", "false");
        options.put("useKeyTab", "false");
        options.put("doNotPrompt", "false");
        options.put("storeKey", "true");
        options.put("isInitiator", "true");
        options.put("refreshKrb5Config", "true");
        options.put("debug", String.valueOf(kerberosDebug));

        Configuration configuration = new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                if (!JAAS_ENTRY_NAME.equals(name)) {
                    return null;
                }

                return new AppConfigurationEntry[]{
                        new AppConfigurationEntry(
                                "com.sun.security.auth.module.Krb5LoginModule",
                                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                                options
                        )
                };
            }
        };

        return new LoginContext(
                JAAS_ENTRY_NAME,
                null,
                callbackHandler,
                configuration
        );
    }

    private void configureKerberosRuntime() {
        if (!StringUtils.hasText(kerberosRealm)) {
            throw new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "Kerberos realm has not been configured."
            );
        }

        if (!StringUtils.hasText(kerberosKdc)) {
            throw new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "Kerberos KDC has not been configured."
            );
        }

        synchronized (KERBEROS_CONFIGURATION_LOCK) {
            System.setProperty("java.security.krb5.realm", kerberosRealm.trim().toUpperCase(Locale.ROOT));
            System.setProperty("java.security.krb5.kdc", kerberosKdc.trim());
            System.setProperty("sun.security.krb5.debug", String.valueOf(kerberosDebug));
        }
    }

    private LdapContext openGssapiContext(String url) throws NamingException {
        Hashtable<String, Object> environment = baseLdapEnvironment(url);
        environment.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");
        environment.put("java.naming.security.sasl.realm", kerberosRealm.trim().toUpperCase(Locale.ROOT));
        environment.put("javax.security.sasl.qop", normalizeQop(kerberosLdapQop));
        environment.put("javax.security.sasl.server.authentication", "false");

        return new InitialLdapContext(environment, null);
    }

    private DirectoryUser authenticateWithSimpleBind(DomainIdentity identity, String password) {
        List<String> urls = parseUrls(ldapUrls);
        if (urls.isEmpty()) {
            throw new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "No Domain Controller LDAPS URL has been configured."
            );
        }

        DomainAuthenticationException lastUnavailable = null;

        for (String url : urls) {
            LdapContext context = null;

            try {
                logger.info(
                        "Attempting AD simple bind for account {} using {} format through {}",
                        identity.getSamAccountName(),
                        identity.getBindPrincipalFormat(),
                        url
                );

                context = openSimpleContext(url, identity.getBindPrincipal(), password);
                return findAuthenticatedUser(context, identity);
            } catch (AuthenticationException ex) {
                logger.warn(
                        "Active Directory rejected the simple bind for account {}. Diagnostic: {}",
                        identity.getSamAccountName(),
                        authenticationDiagnostic(ex)
                );
                throw new DomainAuthenticationException(
                        INVALID_CREDENTIALS,
                        "Domain username or password is incorrect.",
                        ex
                );
            } catch (CommunicationException ex) {
                lastUnavailable = classifyConnectionFailure(url, ex);
            } catch (NamingException ex) {
                if (isInvalidCredentials(ex)) {
                    throw new DomainAuthenticationException(
                            INVALID_CREDENTIALS,
                            "Domain username or password is incorrect.",
                            ex
                    );
                }
                lastUnavailable = classifyConnectionFailure(url, ex);
            } finally {
                closeQuietly(context);
            }
        }

        if (lastUnavailable != null) {
            throw lastUnavailable;
        }

        throw new DomainAuthenticationException(
                SERVICE_UNAVAILABLE,
                "Unable to connect to the Domain authentication service."
        );
    }

    private LdapContext openSimpleContext(String url, String principal, String password) throws NamingException {
        Hashtable<String, Object> environment = baseLdapEnvironment(url);
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, principal);
        environment.put(Context.SECURITY_CREDENTIALS, password);
        return new InitialLdapContext(environment, null);
    }

    private Hashtable<String, Object> baseLdapEnvironment(String url) {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, url);
        environment.put(Context.REFERRAL, "ignore");
        environment.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connectTimeoutMs));
        environment.put("com.sun.jndi.ldap.read.timeout", String.valueOf(readTimeoutMs));
        environment.put("com.sun.jndi.ldap.connect.pool", "false");

        if (url.toLowerCase(Locale.ROOT).startsWith("ldaps://")) {
            environment.put(
                    "java.naming.ldap.factory.socket",
                    LdapSslSocketFactory.class.getName()
            );
        }

        return environment;
    }

    private DirectoryUser findAuthenticatedUser(LdapContext context, DomainIdentity identity) throws NamingException {
        String baseDn = resolveBaseDn(context);
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(USER_ATTRIBUTES);
        controls.setCountLimit(2);
        controls.setTimeLimit(readTimeoutMs);

        String filter = "(&(objectCategory=person)(objectClass=user)(|"
                + "(sAMAccountName=" + escapeLdapFilter(identity.getSamAccountName()) + ")"
                + "(userPrincipalName=" + escapeLdapFilter(identity.getUserPrincipalName()) + ")"
                + "(mail=" + escapeLdapFilter(identity.getOriginalIdentifier()) + ")"
                + "))";

        NamingEnumeration<SearchResult> results = null;

        try {
            results = context.search(baseDn, filter, controls);

            if (!results.hasMore()) {
                throw new DomainAuthenticationException(
                        USER_NOT_FOUND,
                        "The Domain account was authenticated, but its directory profile could not be found."
                );
            }

            SearchResult result = results.next();
            Attributes attributes = result.getAttributes();

            String mail = attributeValue(attributes, "mail");
            String userPrincipalName = attributeValue(attributes, "userPrincipalName");
            String email = firstNonBlank(mail, userPrincipalName, identity.getUserPrincipalName());

            if (!StringUtils.hasText(email)) {
                throw new DomainAuthenticationException(
                        EMAIL_NOT_FOUND,
                        "The Domain account does not have an email address. Please contact the administrator."
                );
            }

            String userAccountControl = attributeValue(attributes, "userAccountControl");
            if (isDisabled(userAccountControl)) {
                throw new DomainAuthenticationException(
                        DISABLED,
                        "The Domain account has been disabled. Please contact the administrator."
                );
            }

            return new DirectoryUser(
                    attributeValue(attributes, "sAMAccountName"),
                    userPrincipalName,
                    email.trim().toLowerCase(Locale.ROOT),
                    attributeValue(attributes, "displayName")
            );
        } finally {
            closeQuietly(results);
        }
    }

    private String resolveBaseDn(LdapContext context) throws NamingException {
        if (StringUtils.hasText(configuredBaseDn)) {
            return configuredBaseDn.trim();
        }

        Attributes rootAttributes = context.getAttributes("", new String[]{"defaultNamingContext"});
        String discoveredBaseDn = attributeValue(rootAttributes, "defaultNamingContext");

        if (!StringUtils.hasText(discoveredBaseDn)) {
            throw new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "Active Directory did not provide a default naming context."
            );
        }

        return discoveredBaseDn;
    }

    private DomainIdentity normalizeIdentity(String rawIdentifier) {
        String original = rawIdentifier.trim();
        String account = original;
        String samAccountName;
        String userPrincipalName;
        String bindPrincipal;
        String bindPrincipalFormat;

        int slashIndex = account.indexOf('\\');
        if (slashIndex >= 0) {
            String prefix = account.substring(0, slashIndex).trim();
            String suffix = account.substring(slashIndex + 1).trim();

            if (!StringUtils.hasText(suffix)) {
                throw invalidIdentity();
            }

            if (StringUtils.hasText(prefix)
                    && StringUtils.hasText(netbiosDomain)
                    && !prefix.equalsIgnoreCase(netbiosDomain.trim())) {
                throw new DomainAuthenticationException(
                        INVALID_CREDENTIALS,
                        "The account does not belong to the configured Domain."
                );
            }

            samAccountName = suffix;
            userPrincipalName = samAccountName + "@" + domain.trim();
            bindPrincipal = netbiosDomain.trim() + "\\" + samAccountName;
            bindPrincipalFormat = "NETBIOS";
        } else {
            int atIndex = account.indexOf('@');

            if (atIndex >= 0) {
                samAccountName = account.substring(0, atIndex).trim();
                userPrincipalName = account;
                bindPrincipal = account;
                bindPrincipalFormat = "UPN";
            } else {
                samAccountName = account;
                userPrincipalName = samAccountName + "@" + domain.trim();

                if ("UPN".equalsIgnoreCase(bareUsernameBindFormat)) {
                    bindPrincipal = userPrincipalName;
                    bindPrincipalFormat = "UPN";
                } else {
                    bindPrincipal = netbiosDomain.trim() + "\\" + samAccountName;
                    bindPrincipalFormat = "NETBIOS";
                }
            }
        }

        if (!StringUtils.hasText(samAccountName)
                || !StringUtils.hasText(userPrincipalName)
                || !StringUtils.hasText(bindPrincipal)) {
            throw invalidIdentity();
        }

        String kerberosPrincipal = samAccountName
                + "@"
                + kerberosRealm.trim().toUpperCase(Locale.ROOT);

        return new DomainIdentity(
                original,
                samAccountName,
                userPrincipalName,
                bindPrincipal,
                bindPrincipalFormat,
                kerberosPrincipal
        );
    }

    private DomainAuthenticationException invalidIdentity() {
        return new DomainAuthenticationException(
                INVALID_CREDENTIALS,
                "Domain username is invalid."
        );
    }

    private void validateConfigurationAndInput(String rawIdentifier, String password) {
        if (!enabled) {
            throw new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "Domain login is currently disabled."
            );
        }

        if (!StringUtils.hasText(rawIdentifier) || !StringUtils.hasText(password)) {
            throw new DomainAuthenticationException(
                    INVALID_CREDENTIALS,
                    "Domain username and password are required."
            );
        }
    }

    private DomainAuthenticationException classifyKerberosLoginFailure(
            DomainIdentity identity,
            LoginException ex
    ) {
        String diagnostic = sanitizeDiagnostic(ex.getMessage());
        String normalized = diagnostic.toLowerCase(Locale.ROOT);

        logger.warn(
                "AD Kerberos authentication failed for account {}: {}",
                identity.getSamAccountName(),
                diagnostic
        );

        if (normalized.contains("pre-authentication information was invalid")
                || normalized.contains("preauthentication failed")
                || normalized.contains("password")
                || normalized.contains("client not found")
                || normalized.contains("credentials")) {
            return new DomainAuthenticationException(
                    INVALID_CREDENTIALS,
                    "Domain username or password is incorrect.",
                    ex
            );
        }

        if (normalized.contains("cannot locate kdc")
                || normalized.contains("cannot contact")
                || normalized.contains("connection")
                || normalized.contains("timeout")
                || normalized.contains("network is unreachable")) {
            return new DomainAuthenticationException(
                    SERVICE_UNAVAILABLE,
                    "Unable to connect to the Active Directory Kerberos service at " + kerberosKdc + ".",
                    ex
            );
        }

        return new DomainAuthenticationException(
                SERVICE_UNAVAILABLE,
                "Active Directory Kerberos authentication failed: " + diagnostic,
                ex
        );
    }

    private DomainAuthenticationException classifyGssapiDirectoryFailure(String url, NamingException ex) {
        if (containsCause(ex, SSLHandshakeException.class)) {
            return classifyConnectionFailure(url, ex);
        }

        String diagnostic = sanitizeDiagnostic(rootCauseMessage(ex));
        String normalized = diagnostic.toLowerCase(Locale.ROOT);

        if (normalized.contains("server not found in kerberos database")
                || normalized.contains("no valid credentials provided")
                || normalized.contains("failed to find any kerberos tgt")
                || normalized.contains("gss initiate failed")) {
            return new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "Kerberos authenticated the user, but LDAP GSSAPI could not authenticate to " + url
                            + ". The LDAP URL must use the Domain Controller FQDN. Detail: " + diagnostic,
                    ex
            );
        }

        return new DomainAuthenticationException(
                SERVICE_UNAVAILABLE,
                "Unable to query Active Directory through LDAP GSSAPI at " + url + ". Detail: " + diagnostic,
                ex
        );
    }

    private DomainAuthenticationException classifyConnectionFailure(String url, NamingException ex) {
        if (containsCause(ex, SSLHandshakeException.class)) {
            String rootMessage = rootCauseMessage(ex).toLowerCase(Locale.ROOT);

            if (rootMessage.contains("no subject alternative")
                    || rootMessage.contains("no name matching")
                    || rootMessage.contains("hostname")) {
                return new DomainAuthenticationException(
                        CONFIGURATION_ERROR,
                        "The LDAPS certificate hostname does not match " + url
                                + ". Use the Domain Controller hostname contained in the certificate.",
                        ex
                );
            }

            return new DomainAuthenticationException(
                    CONFIGURATION_ERROR,
                    "LDAPS certificate validation failed for " + url
                            + ". Root cause: " + rootCauseMessage(ex),
                    ex
            );
        }

        return new DomainAuthenticationException(
                SERVICE_UNAVAILABLE,
                "Unable to connect to Domain Controller " + url + ".",
                ex
        );
    }

    private String normalizeQop(String value) {
        if (!StringUtils.hasText(value)) {
            return "auth";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("auth")
                || normalized.equals("auth-int")
                || normalized.equals("auth-conf")) {
            return normalized;
        }

        throw new DomainAuthenticationException(
                CONFIGURATION_ERROR,
                "Invalid Kerberos LDAP QOP. Use auth, auth-int, or auth-conf."
        );
    }

    private List<String> parseUrls(String value) {
        List<String> urls = new ArrayList<>();

        if (!StringUtils.hasText(value)) {
            return urls;
        }

        for (String item : value.split("[,;]")) {
            String url = item.trim();
            if (StringUtils.hasText(url)) {
                urls.add(url);
            }
        }

        return urls;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }

    private String authenticationDiagnostic(AuthenticationException ex) {
        return sanitizeDiagnostic(ex.getMessage());
    }

    private String sanitizeDiagnostic(String message) {
        if (!StringUtils.hasText(message)) {
            return "No diagnostic message";
        }

        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 700 ? normalized : normalized.substring(0, 700);
    }

    private boolean isInvalidCredentials(NamingException ex) {
        if (ex instanceof AuthenticationException) {
            return true;
        }

        String message = ex.getMessage();
        if (message == null) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("error code 49")
                || normalized.contains("data 52e")
                || normalized.contains("invalid credentials");
    }

    private boolean isDisabled(String userAccountControl) {
        if (!StringUtils.hasText(userAccountControl)) {
            return false;
        }

        try {
            int value = Integer.parseInt(userAccountControl.trim());
            return (value & 0x0002) != 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String attributeValue(Attributes attributes, String attributeName) throws NamingException {
        if (attributes == null) {
            return "";
        }

        Attribute attribute = attributes.get(attributeName);
        if (attribute == null || attribute.get() == null) {
            return "";
        }

        return String.valueOf(attribute.get()).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String escapeLdapFilter(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '\\':
                    escaped.append("\\5c");
                    break;
                case '*':
                    escaped.append("\\2a");
                    break;
                case '(':
                    escaped.append("\\28");
                    break;
                case ')':
                    escaped.append("\\29");
                    break;
                case '\u0000':
                    escaped.append("\\00");
                    break;
                default:
                    escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> causeClass) {
        Throwable current = throwable;
        while (current != null) {
            if (causeClass.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void closeQuietly(DirContext context) {
        if (context == null) {
            return;
        }

        try {
            context.close();
        } catch (NamingException ignored) {
            // Nothing to do.
        }
    }

    private void closeQuietly(NamingEnumeration<?> enumeration) {
        if (enumeration == null) {
            return;
        }

        try {
            enumeration.close();
        } catch (NamingException ignored) {
            // Nothing to do.
        }
    }

    private enum AuthenticationMode {
        KERBEROS_GSSAPI,
        SIMPLE;

        private static AuthenticationMode from(String value) {
            if (!StringUtils.hasText(value)) {
                return KERBEROS_GSSAPI;
            }

            String normalized = value.trim()
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);

            if (normalized.equals("KERBEROS")
                    || normalized.equals("GSSAPI")
                    || normalized.equals("NEGOTIATE")) {
                return KERBEROS_GSSAPI;
            }

            try {
                return AuthenticationMode.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new DomainAuthenticationException(
                        CONFIGURATION_ERROR,
                        "Invalid LDAP authentication mode. Use KERBEROS_GSSAPI or SIMPLE.",
                        ex
                );
            }
        }
    }

    private static final class PasswordCallbackHandler implements CallbackHandler {
        private final String username;
        private final char[] password;

        private PasswordCallbackHandler(String username, String password) {
            this.username = username;
            this.password = password.toCharArray();
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            try {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(username);
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(password);
                    } else if (callback instanceof RealmCallback) {
                        RealmCallback realmCallback = (RealmCallback) callback;
                        realmCallback.setText(realmCallback.getDefaultText());
                    } else if (callback instanceof RealmChoiceCallback) {
                        ((RealmChoiceCallback) callback).setSelectedIndex(0);
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static final class DomainIdentity {
        private final String originalIdentifier;
        private final String samAccountName;
        private final String userPrincipalName;
        private final String bindPrincipal;
        private final String bindPrincipalFormat;
        private final String kerberosPrincipal;

        private DomainIdentity(
                String originalIdentifier,
                String samAccountName,
                String userPrincipalName,
                String bindPrincipal,
                String bindPrincipalFormat,
                String kerberosPrincipal
        ) {
            this.originalIdentifier = originalIdentifier;
            this.samAccountName = samAccountName;
            this.userPrincipalName = userPrincipalName;
            this.bindPrincipal = bindPrincipal;
            this.bindPrincipalFormat = bindPrincipalFormat;
            this.kerberosPrincipal = kerberosPrincipal;
        }

        private String getOriginalIdentifier() {
            return originalIdentifier;
        }

        private String getSamAccountName() {
            return samAccountName;
        }

        private String getUserPrincipalName() {
            return userPrincipalName;
        }

        private String getBindPrincipal() {
            return bindPrincipal;
        }

        private String getBindPrincipalFormat() {
            return bindPrincipalFormat;
        }

        private String getKerberosPrincipal() {
            return kerberosPrincipal;
        }
    }

    public static final class DirectoryUser {
        private final String username;
        private final String userPrincipalName;
        private final String email;
        private final String displayName;

        public DirectoryUser(String username, String userPrincipalName, String email, String displayName) {
            this.username = username;
            this.userPrincipalName = userPrincipalName;
            this.email = email;
            this.displayName = displayName;
        }

        public String getUsername() {
            return username;
        }

        public String getUserPrincipalName() {
            return userPrincipalName;
        }

        public String getEmail() {
            return email;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
