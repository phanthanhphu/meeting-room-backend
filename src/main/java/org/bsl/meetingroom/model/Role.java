package org.bsl.meetingroom.model;

public enum Role {
    USER,
    VIEWER,
    ROOM_MANAGER,
    ADMIN;

    public boolean canViewAllBookings() {
        return this == VIEWER || this == ROOM_MANAGER || this == ADMIN;
    }

    public boolean canManageBookings() {
        return this == ROOM_MANAGER || this == ADMIN;
    }

    public boolean canManageRooms() {
        return this == ROOM_MANAGER || this == ADMIN;
    }

    public boolean canManageUsers() {
        return this == ADMIN;
    }

    public boolean isReadOnly() {
        return this == VIEWER;
    }
}
