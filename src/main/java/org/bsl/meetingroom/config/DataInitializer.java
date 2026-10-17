package org.bsl.meetingroom.config;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.LinkedHashSet;
import java.text.Normalizer;
import java.util.Locale;

@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String USERS_COLLECTION = "users";
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@youngonevn.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    @Bean
    CommandLineRunner seed(UserRepository users, MeetingRoomRepository rooms, EquipmentRepository equipment, PasswordEncoder encoder, MongoTemplate mongoTemplate) {
        return args -> {
            // Normalize legacy MongoDB enum values before any UserRepository read.
            // Spring Data maps enums case-sensitively: "Admin" cannot be read as Role.ADMIN.
            normalizeLegacyRoles(mongoTemplate);
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
            migrateLegacyAmenities(rooms, equipment);
        };
    }

    /** One-time, non-destructive bridge from legacy free-text amenities to the catalog. */
    private void migrateLegacyAmenities(MeetingRoomRepository rooms, EquipmentRepository equipment) {
        for (MeetingRoom room : rooms.findAll()) {
            if (!room.getEquipmentIds().isEmpty() || room.getAmenities()==null || room.getAmenities().isBlank()) continue;
            var ids=new LinkedHashSet<String>();
            for (String raw : room.getAmenities().split(",")) {
                String name=raw.trim();
                if(name.isEmpty())continue;
                Equipment item=equipment.findByNameIgnoreCase(name).orElse(null);
                if(item==null){
                    String code=Normalizer.normalize(name,Normalizer.Form.NFD)
                            .replaceAll("\\p{M}","").toUpperCase(Locale.ROOT)
                            .replaceAll("[^A-Z0-9]+","_").replaceAll("^_+|_+$","");
                    if(code.isBlank())code="DEVICE";
                    code="EQ_"+code.substring(0,Math.min(code.length(),34));
                    String base=code;
                    int suffix=2;
                    while(equipment.existsByCodeIgnoreCase(code)){
                        String end="_"+(suffix++);
                        code=base.substring(0,Math.min(base.length(),40-end.length()))+end;
                    }
                    item=new Equipment();item.setCode(code);item.setName(name);
                    item.setCategory(inferCategory(name));item.setDescription("");
                    item.setStatus(EquipmentStatus.ACTIVE);
                    item=equipment.save(item);
                }
                ids.add(item.getId());
            }
            if(!ids.isEmpty()){
                room.setEquipmentIds(List.copyOf(ids));
                rooms.save(room);
            }
        }
    }

    private String inferCategory(String name){
        String n=name.toLowerCase(Locale.ROOT);
        if(n.contains("projector")||n.contains("tv")||n.contains("display")||n.contains("screen"))return "DISPLAY";
        if(n.contains("camera")||n.contains("video")||n.contains("conference"))return "VIDEO";
        if(n.contains("speaker")||n.contains("microphone")||n.contains("audio"))return "AUDIO";
        if(n.contains("whiteboard")||n.contains("board"))return "PRESENTATION";
        if(n.contains("hdmi")||n.contains("wifi")||n.contains("wi-fi"))return "CONNECTIVITY";
        return "OTHER";
    }

    /**
     * MongoDB data fix: translate only recognized Role values to their canonical
     * Java enum spelling, without deleting users or changing their privileges.
     * Reading the raw collection strings first is essential: UserRepository
     * cannot deserialize legacy values such as "Admin".
     *
     * Runs at every startup, but after the first correction it has no writes.
     * Unknown roles are left untouched and logged for manual investigation.
     */
    private void normalizeLegacyRoles(MongoTemplate mongoTemplate) {
        List<String> storedRoles = mongoTemplate.findDistinct(
                new Query(), "role", USERS_COLLECTION, String.class);

        for (String storedRole : storedRoles) {
            if (storedRole == null) continue;

            String canonical = storedRole.trim().toUpperCase(Locale.ROOT);
            try {
                Role.valueOf(canonical);
            } catch (IllegalArgumentException ex) {
                log.warn("Unknown role '{}' in MongoDB users collection; needs manual review", storedRole);
                continue;
            }
            if (storedRole.equals(canonical)) continue;

            var result = mongoTemplate.updateMulti(
                    Query.query(Criteria.where("role").is(storedRole)),
                    Update.update("role", canonical),
                    USERS_COLLECTION);
            log.info("Migrated user role '{}' -> '{}' ({} user(s))",
                    storedRole, canonical, result.getModifiedCount());
        }
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
