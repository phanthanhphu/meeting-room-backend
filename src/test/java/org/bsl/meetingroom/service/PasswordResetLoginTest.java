package org.bsl.meetingroom.service;

import org.bsl.meetingroom.dto.Dtos.LoginRequest;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.AccountSource;
import org.bsl.meetingroom.model.Role;
import org.bsl.meetingroom.model.User;
import org.bsl.meetingroom.repository.BookingRepository;
import org.bsl.meetingroom.repository.UserRepository;
import org.bsl.meetingroom.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetLoginTest {

    @Test
    void resetPasswordCanLoginImmediatelyAndOldPasswordStopsWorking() {
        UserRepository users = mock(UserRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        TokenService tokens = mock(TokenService.class);
        AppSocketPublisher socket = mock(AppSocketPublisher.class);
        NotificationService notifications = mock(NotificationService.class);
        ActiveDirectoryService activeDirectory = mock(ActiveDirectoryService.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);

        User target = new User();
        target.setId("user-1");
        target.setUsername("user.one");
        target.setEmail("user.one@youngonevn.com");
        target.setFullName("User One");
        target.setDepartment("IT");
        target.setAccountSource(AccountSource.SYSTEM);
        target.setRole(Role.USER);
        target.setEnabled(true);
        target.setPasswordHash(encoder.encode("OldPass@1234"));

        User admin = new User();
        admin.setId("admin-1");
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        when(users.findById("user-1")).thenReturn(Optional.of(target));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByUsernameIgnoreCase("user.one")).thenReturn(Optional.of(target));
        when(users.findByEmailIgnoreCase("user.one")).thenReturn(Optional.empty());
        when(tokens.generate(target)).thenReturn("token");

        UserService userService = new UserService(users, bookings, encoder, socket, notifications);
        AuthService authService = new AuthService(users, encoder, tokens, activeDirectory, userService, "youngonevn.com");

        String temporaryPassword = userService.resetPassword("user-1", admin).temporaryPassword();

        assertNotNull(temporaryPassword);
        assertTrue(temporaryPassword.length() >= 12);
        assertTrue(encoder.matches(temporaryPassword, target.getPasswordHash()));
        assertEquals("token", authService.login(new LoginRequest("SYSTEM", "user.one", "user.one", temporaryPassword)).token());
        assertThrows(AppException.class, () -> authService.login(new LoginRequest("SYSTEM", "user.one", "user.one", "OldPass@1234")));
    }
}
