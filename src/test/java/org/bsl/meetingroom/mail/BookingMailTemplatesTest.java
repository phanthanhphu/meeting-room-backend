package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class BookingMailTemplatesTest {
    private Booking example() {
        Booking b=new Booking();
        b.setId("book-1");b.setTitle("Weekly <script>alert(1)</script>");
        b.setStartAt(LocalDateTime.of(2026,10,30,9,0));
        b.setEndAt(LocalDateTime.of(2026,10,30,10,30));
        b.setAttendeeCount(3);
        b.setAdminNote("<b>Unavailable</b>");
        return b;
    }
    private MeetingRoom room(){MeetingRoom room=new MeetingRoom();room.setId("room");room.setName("Room A");room.setLocation("Floor 2");return room;}
    @Test void approvalHasStatusAndEscapesBookingTitle() {
        var content=BookingMailTemplates.approved(example(),room(),null,null,null);
        assertTrue(content.html().contains("APPROVED"));
        assertTrue(content.html().contains("Weekly &lt;script&gt;"));
        assertFalse(content.html().contains("<script>"));
        assertTrue(content.html().contains("09:00 - 10:30"));
        assertFalse(content.subject().contains("\n"));
    }
    @Test void rejectionEscapesReason() {
        var content=BookingMailTemplates.rejected(example(),room(),null,null,null);
        assertTrue(content.html().contains("Reason for rejection"));
        assertTrue(content.html().contains("&lt;b&gt;Unavailable&lt;/b&gt;"));
        assertFalse(content.html().contains("<b>Unavailable</b>"));
    }
    @Test void newBookingHasPendingStatus(){
        var content=BookingMailTemplates.created(example(),room(),null,null);
        assertTrue(content.html().contains("PENDING APPROVAL"));
    }
    @Test void userEmailsHideInternalFieldsAndAdminKeepsRelevantFields() {
        var example=example();var room=room();
        var manager=new User();manager.setFullName("Room Manager");
        var userContents=java.util.List.of(
            BookingMailTemplates.pendingConfirmation(example,room,null,"https://booking.example.com/my-bookings?booking=book-1"),
            BookingMailTemplates.approved(example,room,null,manager,"https://booking.example.com/my-bookings?booking=book-1"),
            BookingMailTemplates.rejected(example,room,null,manager,"https://booking.example.com/my-bookings?booking=book-1"),
            BookingMailTemplates.cancelled(example,room,null,manager,"https://booking.example.com/my-bookings?booking=book-1"));
        for (var mail : userContents) {
            assertFalse(mail.html().contains("Booking ID"));
            assertFalse(mail.html().contains("Attendees"));
            assertFalse(mail.html().contains("Purpose"));
            assertFalse(mail.html().contains("Processed By"));
            assertTrue(mail.html().contains("booking.example.com/my-bookings?booking=book-1"));
            assertTrue(mail.html().contains("href='https://booking.example.com/my-bookings?booking=book-1'"));
            assertFalse(mail.html().contains("Open booking in browser"));
            assertFalse(mail.html().contains("Button not working?"));
        }
        var admin=BookingMailTemplates.adminApproved(example,room,null,manager,"https://booking.example.com/approvals?booking=book-1");
        assertFalse(admin.html().contains("Booking ID"));
        assertTrue(admin.html().contains("Attendees"));
        assertTrue(admin.html().contains("Purpose"));
        assertTrue(admin.html().contains("Processed By"));
        assertTrue(admin.html().contains("booking.example.com/approvals?booking=book-1"));
        assertFalse(admin.html().contains("Open booking in browser"));
        assertFalse(admin.html().contains("Button not working?"));
    }

    @Test void allAdminEventsUseOneSalutationAndHideBookingId() {
        Booking b=example(); MeetingRoom r=room();
        User manager=new User(); manager.setFullName("Room Manager");
        var adminContents=java.util.List.of(
            BookingMailTemplates.created(b,r,null,"https://booking.example.com/approvals?booking=book-1"),
            BookingMailTemplates.adminApproved(b,r,null,manager,"https://booking.example.com/approvals?booking=book-1"),
            BookingMailTemplates.adminRejected(b,r,null,manager,"https://booking.example.com/approvals?booking=book-1"),
            BookingMailTemplates.adminCancelled(b,r,null,manager,"https://booking.example.com/approvals?booking=book-1"));
        for (var mail : adminContents) {
            assertEquals(1, mail.html().split("Dear Room Manager,",-1).length-1);
            assertFalse(mail.html().contains("Dear Administrator,"));
            assertFalse(mail.html().contains("Booking ID"));
            assertTrue(mail.html().contains("href='https://booking.example.com/approvals?booking=book-1'"));
            assertTrue(mail.html().contains("cid:"+BookingMailTemplates.BRAND_ICON_CID));
        }
        var preview = BookingMailTemplates.preview("APPROVED", "https://booking.example.com");
        assertFalse(preview.adminHtml().contains("Booking ID"));
        assertTrue(preview.adminHtml().contains("Dear Room Manager,"));
    }
    @Test void previewUsesConfiguredWebsiteUrl() {
        var preview=BookingMailTemplates.preview("APPROVED","https://booking.example.com");
        assertTrue(preview.userHtml().contains("booking.example.com/my-bookings?booking=sample"));
        assertTrue(preview.adminHtml().contains("booking.example.com/approvals?booking=sample"));
        assertFalse(preview.userHtml().contains("Booking ID"));
        assertFalse(BookingMailTemplates.preview("APPROVED", "").userHtml().contains("href='https://meeting.example.com"));
    }
    @Test void userAndAdminUseEmbeddedIconAndBrowserPreviewShowsIt() {
        Booking booking=example();
        MeetingRoom meetingRoom=room();
        String cid="cid:" + BookingMailTemplates.BRAND_ICON_CID;
        assertTrue(BookingMailTemplates.pendingConfirmation(booking,meetingRoom,null,null).html().contains(cid));
        assertTrue(BookingMailTemplates.created(booking,meetingRoom,null,null).html().contains(cid));
        assertTrue(BookingMailTemplates.brandIconBytes().length>100);
        var preview=BookingMailTemplates.preview("APPROVED", "https://booking.example.com");
        assertTrue(preview.userHtml().contains("data:image/png;base64,"));
        assertTrue(preview.adminHtml().contains("data:image/png;base64,"));
        assertFalse(preview.userHtml().contains(cid));
    }

    @Test void statusIsHorizontallyAndVerticallyCenteredForAllMailEvents() {
        Booking b = example();
        MeetingRoom r = room();
        User manager = new User(); manager.setFullName("Room Manager");
        var mails = java.util.List.of(
            BookingMailTemplates.created(b,r,null,null),
            BookingMailTemplates.pendingConfirmation(b,r,null,null),
            BookingMailTemplates.approved(b,r,null,manager,null),
            BookingMailTemplates.adminApproved(b,r,null,manager,null),
            BookingMailTemplates.rejected(b,r,null,manager,null),
            BookingMailTemplates.adminRejected(b,r,null,manager,null),
            BookingMailTemplates.cancelled(b,r,null,manager,null),
            BookingMailTemplates.adminCancelled(b,r,null,manager,null),
            BookingMailTemplates.test("Tester")
        );
        for (var mail : mails) {
            assertTrue(mail.html().matches("(?s).*<td align='center' valign='middle' bgcolor='#F8FAFD' style='[^']*text-align:center;vertical-align:middle;'>.*"), mail.subject());
        }
    }
}
