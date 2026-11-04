package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.Booking;
import org.bsl.meetingroom.model.MeetingRoom;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** RFC 5545 iCalendar invitation/update. Only approved bookings send REQUEST;
 * cancelling a previously approved booking sends CANCEL with the same UID.
 * Outlook renders Accept/Tentative/Decline from the MIME text/calendar part. */
public final class OutlookCalendarInvite {
    private OutlookCalendarInvite() {}
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC);

    public static String build(Booking b, MeetingRoom room, String organizer, String attendee,
                               boolean cancelled, Clock clock, String bookingUrl) {
        if (b.getId() == null || b.getStartAt() == null || b.getEndAt() == null)
            throw new IllegalArgumentException("Booking ID/start/end required for calendar invitation");
        String method = cancelled ? "CANCEL" : "REQUEST";
        ZoneId zone = clock.getZone();
        Instant dtStamp = b.getUpdatedAt() == null ? clock.instant() : b.getUpdatedAt().atZone(zone).toInstant();
        long sequence = b.getVersion() == null ? (cancelled ? 2 : 1) : Math.max(0L, b.getVersion());
        String uid = "booking-" + b.getId().replaceAll("[^A-Za-z0-9-]", "") + "@youngonevn.com";
        String location = value(room.getName()) + (room.getLocation() == null || room.getLocation().isBlank()
                ? "" : " - " + room.getLocation());
        // Keep calendar description concise and aligned with user-facing email.
        String description = "Meeting room: " + value(room.getName());
        // Outlook may display calendar DESCRIPTION instead of the HTML email body.
        // Put the same website deep link here as well, without internal Booking ID.
        if (bookingUrl != null && bookingUrl.matches("(?i)^https?://[^\\s<>]+$")) {
            description += "\\nView booking: " + bookingUrl;
        }
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "PRODID:-//BSL//Meeting Room Booking//EN");
        line(out, "VERSION:2.0");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:" + method);
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + uid);
        line(out, "DTSTAMP:" + UTC.format(dtStamp));
        line(out, "DTSTART:" + utc(b.getStartAt(), zone));
        line(out, "DTEND:" + utc(b.getEndAt(), zone));
        line(out, "SEQUENCE:" + sequence);
        line(out, "STATUS:" + (cancelled ? "CANCELLED" : "CONFIRMED"));
        line(out, "SUMMARY:" + escape(value(b.getTitle())));
        line(out, "LOCATION:" + escape(location));
        line(out, "DESCRIPTION:" + escape(description.replace("\\n", "\n")));
        line(out, "ORGANIZER:mailto:" + safeAddress(organizer));
        line(out, "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:" + safeAddress(attendee));
        if (bookingUrl != null && bookingUrl.matches("(?i)^https?://[^\\s<>]+$")) {
            line(out, "URL:" + bookingUrl);
        }
        line(out, "END:VEVENT");
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private static String utc(LocalDateTime dateTime, ZoneId zone) {
        return UTC.format(dateTime.atZone(zone).toInstant());
    }
    private static String value(String s) { return s == null ? "" : s; }
    private static String safeAddress(String s) {
        if (s == null || !s.matches("(?i)^[^\\s@<>:;,]+@[^\\s@<>:;,]+\\.[^\\s@<>:;,]+$"))
            throw new IllegalArgumentException("Invalid calendar participant email");
        return s;
    }
    /** Escape text before folding to <= 75 UTF-8 octets per physical line. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }
    private static void line(StringBuilder out, String logical) {
        int count = 0;
        for (int i = 0; i < logical.length();) {
            int cp = logical.codePointAt(i);
            String unit = new String(Character.toChars(cp));
            int bytes = unit.getBytes(StandardCharsets.UTF_8).length;
            if (count + bytes > 75) {
                out.append("\r\n ");
                count = 1; // continuation space counts toward physical line length
            }
            out.append(unit);
            count += bytes;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }
}
