package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.Booking;
import org.bsl.meetingroom.model.MeetingRoom;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class OutlookCalendarInviteTest {
    private final Clock vietnam = Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private Booking booking(){
        Booking b=new Booking();b.setId("booking123");b.setTitle("Họp nhóm, dự án");
        b.setPurpose("Duyệt kế hoạch");
        b.setStartAt(LocalDateTime.of(2026,11,6,9,0));
        b.setEndAt(LocalDateTime.of(2026,11,6,10,30));return b;
    }
    private MeetingRoom room(){MeetingRoom r=new MeetingRoom();r.setName("Phòng 01");r.setLocation("Tầng 2");return r;}
    private String calendar(boolean cancel){return OutlookCalendarInvite.build(booking(),room(),
            "alert.st@youngonevn.com","phupt.st@youngonevn.com",cancel,vietnam,null);}

    @Test void approvedMeetingIsOutlookRequestWithUtcDateAndRsvp(){
        String ics=calendar(false);
        assertTrue(ics.contains("METHOD:REQUEST\r\n"));
        assertTrue(ics.contains("STATUS:CONFIRMED\r\n"));
        assertTrue(ics.contains("DTSTART:20261106T020000Z\r\n"));
        assertTrue(ics.contains("DTEND:20261106T033000Z\r\n"));
        assertTrue(ics.contains("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:"));
        assertTrue(ics.contains("ORGANIZER:mailto:alert.st@youngonevn.com"));
    }
    @Test void cancelMatchesApprovalUidAndIncreasesSequence(){
        String request=calendar(false),cancel=calendar(true);
        assertTrue(cancel.contains("METHOD:CANCEL\r\n"));
        assertTrue(cancel.contains("STATUS:CANCELLED\r\n"));
        assertTrue(request.contains("UID:booking-booking123@youngonevn.com"));
        assertTrue(cancel.contains("UID:booking-booking123@youngonevn.com"));
        assertTrue(request.contains("SEQUENCE:1\r\n"));
        assertTrue(cancel.contains("SEQUENCE:2\r\n"));
    }
    @Test void calendarEscapesContentAndFoldsUtf8Lines(){
        Booking b=booking();b.setTitle("Họp, dự án; kiểm tra\\ tiếp\\n"+"á".repeat(130));
        String ics=OutlookCalendarInvite.build(b,room(),"alert.st@youngonevn.com",
                "phupt.st@youngonevn.com",false,vietnam,null);
        assertTrue(ics.contains("Họp\\, dự án\\; kiểm tra"));
        for(String line:ics.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length<=75);
    }
    @Test void invitationShowsWebLinkWithoutBookingId() {
        String ics=OutlookCalendarInvite.build(booking(),room(),"alert.st@youngonevn.com",
                "phupt.st@youngonevn.com",false,vietnam,"https://booking.example.com/my-bookings?booking=booking123");
        assertFalse(ics.contains("Booking ID:"));
        assertFalse(ics.contains("Purpose:"));
        assertTrue(ics.contains("View booking: https://booking.example.com"));
        assertTrue(ics.contains("URL:https://booking.example.com"));
    }
}
