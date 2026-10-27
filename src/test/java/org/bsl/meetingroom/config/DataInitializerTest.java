package org.bsl.meetingroom.config;

import org.bsl.meetingroom.model.Role;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.bsl.meetingroom.model.User;
import org.bsl.meetingroom.repository.MeetingRoomRepository;
import org.bsl.meetingroom.repository.EquipmentRepository;
import org.bsl.meetingroom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import com.mongodb.client.result.UpdateResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    @Test
    void startupCreatesAdminOnlyWithStrongBootstrapSecret() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
        EquipmentRepository equipment = mock(EquipmentRepository.class);
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

        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findDistinct(any(Query.class), eq("role"), eq("users"), eq(String.class)))
                .thenReturn(List.of());
        CommandLineRunner runner = new DataInitializer().seed(users, rooms, equipment, encoder, mongoTemplate, "BootstrapSecret@2026");
        runner.run();

        User admin = saved.stream()
                .filter(u -> "admin@youngonevn.com".equalsIgnoreCase(u.getEmail()))
                .findFirst()
                .orElseThrow();

        assertEquals("admin", admin.getUsername());
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertTrue(encoder.matches("BootstrapSecret@2026", admin.getPasswordHash()));
    }

    @Test
    void startupNeverResetsExistingAdminPasswordRoleOrEnabledState() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
        EquipmentRepository equipment = mock(EquipmentRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);

        User admin = new User();
        admin.setId("admin-id");
        admin.setAccountSource(org.bsl.meetingroom.model.AccountSource.SYSTEM);
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

        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findDistinct(any(Query.class), eq("role"), eq("users"), eq(String.class)))
                .thenReturn(List.of());
        CommandLineRunner runner = new DataInitializer().seed(users, rooms, equipment, encoder, mongoTemplate, "BootstrapSecret@2026");
        runner.run();

        assertEquals(Role.USER, admin.getRole());
        assertFalse(admin.isEnabled());
        assertTrue(encoder.matches("WrongPassword@123", admin.getPasswordHash()));
        assertEquals(5L, admin.getAuthVersion());
        verify(users, never()).save(admin);
    }

    @Test
    void bootstrapRefusesInsecureOrMissingSecret() {
        UserRepository users=mock(UserRepository.class);
        MeetingRoomRepository rooms=mock(MeetingRoomRepository.class);
        EquipmentRepository equipment=mock(EquipmentRepository.class);
        PasswordEncoder encoder=new BCryptPasswordEncoder(4);
        MongoTemplate mongoTemplate=mock(MongoTemplate.class);
        when(mongoTemplate.findDistinct(any(Query.class),eq("role"),eq("users"),eq(String.class))).thenReturn(List.of());
        when(users.findByEmailIgnoreCase("admin@youngonevn.com")).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        var run = new DataInitializer().seed(users, rooms, equipment, encoder, mongoTemplate, "123456");
        assertThrows(IllegalStateException.class, () -> run.run());
        verify(users,never()).save(any(User.class));
    }

    @Test
    void startupNormalizesLegacyRoleStringsBeforeReadingUsers() throws Exception {
        UserRepository users = mock(UserRepository.class);
        MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
        EquipmentRepository equipment = mock(EquipmentRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        when(mongoTemplate.findDistinct(any(Query.class), eq("role"), eq("users"), eq(String.class)))
                .thenReturn(List.of("Admin", "User", "VIEWER", "unrecognized"));
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq("users")))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(users.findByEmailIgnoreCase("admin@youngonevn.com")).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(users.findAll()).thenReturn(List.of());
        when(rooms.count()).thenReturn(1L);

        new DataInitializer().seed(users, rooms, equipment, encoder, mongoTemplate, "BootstrapSecret@2026").run();

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).updateMulti(queries.capture(), updates.capture(), eq("users"));
        assertEquals("Admin", queries.getAllValues().get(0).getQueryObject().get("role"));
        assertEquals("ADMIN", updates.getAllValues().get(0).getUpdateObject().get("$set", org.bson.Document.class).get("role"));
        assertEquals("User", queries.getAllValues().get(1).getQueryObject().get("role"));
        assertEquals("USER", updates.getAllValues().get(1).getUpdateObject().get("$set", org.bson.Document.class).get("role"));
        InOrder callOrder = inOrder(mongoTemplate, users);
        callOrder.verify(mongoTemplate).findDistinct(any(Query.class), eq("role"), eq("users"), eq(String.class));
        callOrder.verify(mongoTemplate, times(2)).updateMulti(any(Query.class), any(Update.class), eq("users"));
        callOrder.verify(users).findByEmailIgnoreCase("admin@youngonevn.com");
        verify(users).findAll();
    }

}
