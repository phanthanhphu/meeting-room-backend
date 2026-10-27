package org.bsl.meetingroom.common.constants;

import java.time.Duration;

/** Lock expiration settings for booking approval and concurrent edits. */
public final class BookingLockConstants {
    public static final Duration APPROVAL_LOCK_TTL = Duration.ofSeconds(15);
    public static final Duration USER_BOOKING_LOCK_TTL = Duration.ofSeconds(60);
    public static final Duration ROOM_UPDATE_LOCK_TTL = Duration.ofSeconds(60);

    private BookingLockConstants() {
        throw new AssertionError("Utility class");
    }
}
