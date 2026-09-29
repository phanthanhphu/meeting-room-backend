package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.model.AccountSource;
import org.bsl.meetingroom.model.Role;
import org.bsl.meetingroom.model.User;
import org.bsl.meetingroom.repository.BookingRepository;
import org.bsl.meetingroom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DomainAutoProvisionTest {

    private UserService service(UserRepository users) {
        return new UserService(
                users,
                mock(BookingRepository.class),
                new BCryptPasswordEncoder(4),
                mock(AppSocketPublisher.class),
                mock(NotificationService.class)
        );
    }

    @Test
    void firstDomainLoginCreatesNormalUserWithBlankDepartment() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(users.findByDomainAccountIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) user.setId("domain-1");
            return user;
        });

        ActiveDirectoryService.DirectoryUser ad = new ActiveDirectoryService.DirectoryUser(
                "nguyenvana.st",
                "nguyenvana.st@youngonevn.com",
                "nguyenvana.st@youngonevn.com",
                "Nguyen Van A"
        );

        User created = service(users).resolveOrProvisionDomainUser(ad, "nguyenvana.st@youngonevn.com");

        assertEquals("nguyenvana.st", created.getUsername());
        assertEquals("nguyenvana.st", created.getDomainAccount());
        assertEquals("nguyenvana.st@youngonevn.com", created.getEmail());
        assertEquals("", created.getDepartment());
        assertEquals(Role.USER, created.getRole());
        assertEquals(AccountSource.DOMAIN, created.getAccountSource());
        assertTrue(created.isEnabled());
        assertNull(created.getPasswordHash());
    }

    @Test
    void domainLoginReusesPreCreatedAdminAndKeepsAdminRole() {
        UserRepository users = mock(UserRepository.class);
        User admin = new User();
        admin.setId("admin-1");
        admin.setUsername("nguyenvana.st");
        admin.setEmail("nguyenvana.st@youngonevn.com");
        admin.setFullName("Nguyen Van A");
        admin.setDepartment("IT");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setAccountSource(AccountSource.SYSTEM);
        admin.setPasswordHash("local-system-hash");

        when(users.findByEmailIgnoreCase("nguyenvana.st@youngonevn.com")).thenReturn(Optional.of(admin));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActiveDirectoryService.DirectoryUser ad = new ActiveDirectoryService.DirectoryUser(
                "nguyenvana.st",
                "nguyenvana.st@youngonevn.com",
                "nguyenvana.st@youngonevn.com",
                "Nguyen Van A"
        );

        User resolved = service(users).resolveOrProvisionDomainUser(ad, "nguyenvana.st@youngonevn.com");

        assertEquals("admin-1", resolved.getId());
        assertEquals(Role.ADMIN, resolved.getRole());
        assertEquals(AccountSource.SYSTEM, resolved.getAccountSource());
        assertEquals("nguyenvana.st", resolved.getDomainAccount());
        verify(users, never()).delete(any(User.class));
    }
}
