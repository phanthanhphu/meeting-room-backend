package org.bsl.meetingroom.config;

import org.bsl.meetingroom.model.Role;
import org.bsl.meetingroom.model.User;
import org.bsl.meetingroom.repository.MeetingRoomRepository;
import org.bsl.meetingroom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    @Test
    void startupCreatesDefaultAdminEvenWhenOtherUsersExist() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);

        User normalUser = new User();
        normalUser.setId("u1");
        normalUser.setUsername("user.one");
        normalUser.setEmail("user.one@youngonevn.com");
        normalUser.setDepartment("IT");
        normalUser.setRole(Role.USER);
        normalUser.setEnabled(true);

        List<User> saved = new ArrayList<>();
        when(users.findByEmailIgnoreCase("admin@youngonevn.com")).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(users.findAll()).thenReturn(List.of(normalUser));
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User value = invocation.getArgument(0);
            saved.add(value);
            return value;
        });
        when(rooms.count()).thenReturn(1L);

        CommandLineRunner runner = new DataInitializer().seed(users, rooms, encoder);
        runner.run();

        User admin = saved.stream()
                .filter(u -> "admin@youngonevn.com".equalsIgnoreCase(u.getEmail()))
                .findFirst()
                .orElseThrow();

        assertEquals("admin", admin.getUsername());
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertTrue(encoder.matches("123456", admin.getPasswordHash()));
    }

    @Test
    void startupRepairsExistingAdminPasswordRoleAndEnabledState() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);

        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");
        admin.setEmail("admin@youngonevn.com");
        admin.setFullName("Old Admin");
        admin.setDepartment("IT");
        admin.setRole(Role.USER);
        admin.setEnabled(false);
        admin.setPasswordHash(encoder.encode("WrongPassword@123"));
        admin.setAuthVersion(5L);

        when(users.findByEmailIgnoreCase("admin@youngonevn.com")).thenReturn(Optional.of(admin));
        when(users.findAll()).thenReturn(List.of(admin));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rooms.count()).thenReturn(1L);

        CommandLineRunner runner = new DataInitializer().seed(users, rooms, encoder);
        runner.run();

        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertTrue(encoder.matches("123456", admin.getPasswordHash()));
        assertEquals(6L, admin.getAuthVersion());
    }
}
