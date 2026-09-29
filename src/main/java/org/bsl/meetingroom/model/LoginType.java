package org.bsl.meetingroom.model;

public enum LoginType {
    DOMAIN,
    SYSTEM;

    public static LoginType from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DOMAIN;
        }

        String normalized = value.trim().toUpperCase();

        if ("LOCAL".equals(normalized) || "INTERNAL".equals(normalized)) {
            return SYSTEM;
        }

        try {
            return LoginType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported login type: " + value);
        }
    }
}
