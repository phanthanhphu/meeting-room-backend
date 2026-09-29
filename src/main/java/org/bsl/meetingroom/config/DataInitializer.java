package org.bsl.meetingroom.config;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@youngonevn.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    @Bean
    CommandLineRunner seed(UserRepository users, MeetingRoomRepository rooms, PasswordEncoder encoder) {
        return args -> {
            ensureDefaultAdmin(users, encoder);

            for (User existing : users.findAll()) {
                boolean changed = false;
                if (existing.getAccountSource() == null) {
                    existing.setAccountSource(existing.getPasswordHash() == null || existing.getPasswordHash().isBlank()
                            ? AccountSource.DOMAIN : AccountSource.SYSTEM);
                    changed = true;
                }
                // Only administrator-created SYSTEM accounts get the legacy IT fallback.
                // Auto-provisioned DOMAIN accounts intentionally keep Department blank.
                if (existing.getAccountSource() == AccountSource.SYSTEM
                        && (existing.getDepartment() == null || existing.getDepartment().isBlank())) {
                    existing.setDepartment("IT");
                    changed = true;
                }
                if (changed) users.save(existing);
            }

            if (rooms.count() == 0) {
                rooms.save(room("MR-01", "Lotus Room", "Floor 1", 8, "TV, HDMI, Whiteboard"));
                rooms.save(room("MR-02", "Saigon Room", "Floor 2", 16, "Projector, Teams Camera, Speaker"));
                rooms.save(room("MR-03", "Executive Room", "Floor 3", 24, "4K Display, Video Conference, Whiteboard"));
            }
        };
    }

    private void ensureDefaultAdmin(UserRepository users, PasswordEncoder encoder) {
        User admin = users.findByEmailIgnoreCase(DEFAULT_ADMIN_EMAIL)
                .or(() -> users.findByUsernameIgnoreCase(DEFAULT_ADMIN_USERNAME))
                .orElseGet(User::new);

        boolean isNew = admin.getId() == null;
        boolean passwordChanged = admin.getPasswordHash() == null
                || !encoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPasswordHash());
        boolean authChanged = passwordChanged || admin.getRole() != Role.ADMIN || !admin.isEnabled();

        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setEmail(DEFAULT_ADMIN_EMAIL);
        admin.setFullName("Administrator");
        admin.setDepartment("IT");
        admin.setAccountSource(AccountSource.SYSTEM);
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        if (passwordChanged) {
            admin.setPasswordHash(encoder.encode(DEFAULT_ADMIN_PASSWORD));
        }
        if (!isNew && authChanged) {
            admin.setAuthVersion(admin.getAuthVersion() + 1);
        }

        users.save(admin);
    }

    private MeetingRoom room(String code, String name, String location, int capacity, String amenities) {
        MeetingRoom r = new MeetingRoom();
        r.setCode(code);
        r.setName(name);
        r.setLocation(location);
        r.setCapacity(capacity);
        r.setAmenities(amenities);
        r.setDescription("Meeting room " + name);
        r.setStatus(RoomStatus.AVAILABLE);
        return r;
    }
}
