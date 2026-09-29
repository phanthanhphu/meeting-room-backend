package org.bsl.meetingroom.service;

import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.exception.DomainAuthenticationException;
import org.bsl.meetingroom.model.LoginType;
import org.bsl.meetingroom.model.User;
import org.bsl.meetingroom.repository.UserRepository;
import org.bsl.meetingroom.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final ActiveDirectoryService activeDirectory;
    private final UserService userService;
    private final String corporateDomain;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, ActiveDirectoryService activeDirectory,
                       UserService userService, @Value("${app.ldap.domain:youngonevn.com}") String corporateDomain) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.activeDirectory = activeDirectory;
        this.userService = userService;
        this.corporateDomain = corporateDomain == null ? "youngonevn.com" : corporateDomain.trim().toLowerCase(Locale.ROOT);
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "LOGIN_REQUEST_REQUIRED", "Login information is required.");
        }

        String identifier = req.resolveIdentifier();
        String password = req.password();
        if (!StringUtils.hasText(identifier)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "LOGIN_IDENTIFIER_REQUIRED", "Username or email cannot be empty.");
        }
        if (!StringUtils.hasText(password)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "LOGIN_PASSWORD_REQUIRED", "Password cannot be empty.");
        }

        final LoginType loginType;
        try {
            loginType = req.resolveLoginType();
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "LOGIN_TYPE_INVALID", "Login type must be DOMAIN or SYSTEM.");
        }

        return loginType == LoginType.DOMAIN
                ? loginDomain(identifier, password)
                : loginSystem(identifier, password);
    }

    private LoginResponse loginSystem(String identifier, String password) {
        User user = users.findByUsernameIgnoreCase(identifier)
                .or(() -> users.findByEmailIgnoreCase(identifier))
                .orElseThrow(this::invalidSystemCredentials);

        if (!user.isEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "USER_DISABLED", "Your system account has been disabled. Please contact the administrator.");
        }
        if (!StringUtils.hasText(user.getPasswordHash()) || !encoder.matches(password, user.getPasswordHash())) {
            throw invalidSystemCredentials();
        }

        return new LoginResponse(tokens.generate(user), ResponseMapper.user(user), LoginType.SYSTEM.name(), null);
    }

    private LoginResponse loginDomain(String identifier, String password) {
        final ActiveDirectoryService.DirectoryUser directoryUser;
        try {
            directoryUser = activeDirectory.authenticate(identifier, password);
        } catch (DomainAuthenticationException ex) {
            throw mapDomainFailure(ex);
        }

        // A successful Domain authentication is enough to enter the application.
        // First-time users are auto-provisioned as USER; pre-created SYSTEM/ADMIN
        // records are linked by email/username and keep their existing role.
        String canonicalEmail = canonicalCorporateEmail(directoryUser);
        User user = userService.resolveOrProvisionDomainUser(directoryUser, canonicalEmail);

        if (!user.isEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "USER_DISABLED", "Your Meeting Room account has been disabled. Please contact the administrator.");
        }

        DomainUserResponse domain = new DomainUserResponse(
                directoryUser.getUsername(),
                directoryUser.getUserPrincipalName(),
                canonicalEmail,
                directoryUser.getDisplayName()
        );
        return new LoginResponse(tokens.generate(user), ResponseMapper.user(user), LoginType.DOMAIN.name(), domain);
    }

    private String canonicalCorporateEmail(ActiveDirectoryService.DirectoryUser directoryUser) {
        String sam = normalize(directoryUser.getUsername());
        if (StringUtils.hasText(sam)) {
            return sam + "@" + corporateDomain;
        }
        String email = normalize(directoryUser.getEmail());
        if (StringUtils.hasText(email)) return email;
        throw new AppException(HttpStatus.FORBIDDEN, "DOMAIN_EMAIL_NOT_FOUND", "Unable to determine the Domain email address.");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private AppException mapDomainFailure(DomainAuthenticationException ex) {
        return switch (ex.getReason()) {
            case INVALID_CREDENTIALS -> new AppException(HttpStatus.UNAUTHORIZED, "DOMAIN_INVALID_CREDENTIALS", "Domain username or password is incorrect.");
            case EMAIL_NOT_FOUND -> new AppException(HttpStatus.FORBIDDEN, "DOMAIN_EMAIL_NOT_FOUND", ex.getMessage());
            case USER_NOT_FOUND -> new AppException(HttpStatus.FORBIDDEN, "DOMAIN_PROFILE_NOT_FOUND", ex.getMessage());
            case DISABLED -> new AppException(HttpStatus.FORBIDDEN, "DOMAIN_ACCOUNT_DISABLED", ex.getMessage());
            case CONFIGURATION_ERROR -> new AppException(HttpStatus.SERVICE_UNAVAILABLE, "DOMAIN_CONFIGURATION_ERROR", ex.getMessage());
            case SERVICE_UNAVAILABLE -> new AppException(HttpStatus.SERVICE_UNAVAILABLE, "DOMAIN_SERVICE_UNAVAILABLE", "The Domain authentication service is currently unavailable. Please try again or use System Account.");
        };
    }

    private AppException invalidSystemCredentials() {
        return new AppException(HttpStatus.UNAUTHORIZED, "SYSTEM_INVALID_CREDENTIALS", "System username/email or password is incorrect.");
    }

    public User current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa đăng nhập");
        }
        User user = users.findByUsernameIgnoreCase(auth.getName())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Tài khoản không tồn tại"));
        if (!user.isEnabled()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa");
        }
        return user;
    }
}
