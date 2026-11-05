package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.MailTemplateCustomizationRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailTemplateEditorServiceTest {
    private final MailTemplateCustomizationRepository repository=mock(MailTemplateCustomizationRepository.class);
    private final MailTemplateEditorService editor=new MailTemplateEditorService(repository);
    private Booking booking(){
        Booking b=new Booking();b.setId("test/100");b.setTitle("Team <Sync>");
        b.setCreatedByAccount("Account 10");
        b.setStartAt(LocalDateTime.of(2026,11,7,9,0));b.setEndAt(LocalDateTime.of(2026,11,7,10,0));
        return b;
    }
    private MeetingRoom room(){MeetingRoom r=new MeetingRoom();r.setName("Room A");return r;}

    @Test void restoringDefaultDoesNotNeedMigration(){
        var view=editor.getEditor("APPROVED","USER","https://meeting.example.com");
        assertFalse(view.customized());
        assertTrue(view.fields().get("subject").contains("{bookingTitle}"));
        assertEquals("{bookingUrl}",view.fields().get("buttonUrl"));
        assertEquals("Booking confirmation",view.fields().get("heading"));
    }
    @Test void blankTitlesHideEntireBlocksAndDynamicLinkUsesBookingId(){
        var defaults=editor.getEditor("APPROVED","USER","https://meeting.example.com").fields();
        var custom=new LinkedHashMap<>(defaults);
        custom.put("heading","");custom.put("brandTitle","");custom.put("statusLabel","");
        custom.put("footer","");custom.put("subject","Approved: {bookingTitle}");
        custom.put("buttonUrl","https://intranet.example.com/meet/{bookingId}");
        when(repository.findById("APPROVED_USER"))
            .thenReturn(Optional.of(new MailTemplateCustomization("APPROVED_USER",custom)));
        Booking b=booking();MeetingRoom r=room();
        var original=BookingMailTemplates.approved(b,r,null,null,"https://meeting.example.com/my-bookings?booking=test");
        var result=editor.apply("APPROVED","USER",original,b,r,null,null,
                "https://meeting.example.com/my-bookings?booking=test","https://meeting.example.com");
        assertEquals("Approved: Team <Sync>",result.subject());
        assertFalse(result.html().contains("Booking confirmation"));
        assertFalse(result.html().contains("Meeting Room Booking</div>"));
        assertFalse(result.html().contains("This is an automated message"));
        assertFalse(result.html().contains("APPROVED</td></tr>"));
        assertTrue(result.html().contains("https://intranet.example.com/meet/test%2F100"));
    }
    @Test void freeTextEscapesHtmlAndDoesNotAllowUnknownVariables(){
        var defaults=editor.getEditor("REJECTED","ADMIN","https://meeting.example.com").fields();
        var custom=new LinkedHashMap<>(defaults);
        custom.put("heading","<script>alert('x')</script>");
        var view=editor.previewDraft("REJECTED","ADMIN",custom,"https://meeting.example.com");
        assertFalse(view.adminHtml().contains("<script>"));
        assertTrue(view.adminHtml().contains("&lt;script&gt;"));
        custom.put("subject","{unknownKey}");
        assertThrows(org.bsl.meetingroom.exception.AppException.class,
            ()->editor.save("REJECTED","ADMIN",custom,"https://meeting.example.com"));
        verify(repository,never()).save(any());
    }
    @Test void userLocationTitleAndContentCanBeEditedIndependently(){
        var custom=new LinkedHashMap<>(editor.getEditor("CREATED","USER","https://meeting.example.com").fields());
        assertEquals("Location",custom.get("locationLabel"));
        assertEquals("{location}",custom.get("locationValue"));
        custom.put("locationLabel","Venue");
        custom.put("locationValue","At {location}");
        var preview=editor.previewDraft("CREATED","USER",custom,"https://meeting.example.com");
        assertTrue(preview.userHtml().contains("Venue"));
        assertTrue(preview.userHtml().contains("At Building A - Floor 2"));
        assertFalse(preview.userHtml().contains("At {location}"));
        custom.put("locationLabel","");
        var noTitle=editor.previewDraft("CREATED","USER",custom,"https://meeting.example.com");
        assertFalse(noTitle.userHtml().contains("Venue"));
        assertTrue(noTitle.userHtml().contains("At Building A - Floor 2"));
        custom.put("locationValue","");
        var noLocation=editor.previewDraft("CREATED","USER",custom,"https://meeting.example.com");
        assertFalse(noLocation.userHtml().contains("Building A - Floor 2"));
    }
    @Test void adminDetailLabelsAndDynamicValuesAreEditable(){
        var custom=new LinkedHashMap<>(editor.getEditor("APPROVED","ADMIN","https://meeting.example.com").fields());
        custom.put("roomLabel","Conference Venue");
        custom.put("roomValue","{roomName} - {location}");
        custom.put("dateLabel","Meeting date");
        custom.put("purposeValue","<script>unsafe</script> {purpose}");
        var preview=editor.previewDraft("APPROVED","ADMIN",custom,"https://meeting.example.com");
        assertTrue(preview.adminHtml().contains("Conference Venue"));
        assertTrue(preview.adminHtml().contains("Conference Room A - Building A - Floor 2"));
        assertTrue(preview.adminHtml().contains("Meeting date"));
        assertFalse(preview.adminHtml().contains("<script>"));
        assertTrue(preview.adminHtml().contains("&lt;script&gt;"));
    }
    @Test void seedInitializesAllEightTemplatesWithCompleteFields(){
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        editor.initializeTemplatesInDatabase();
        var captor=org.mockito.ArgumentCaptor.forClass(MailTemplateCustomization.class);
        verify(repository,times(8)).save(captor.capture());
        var all=captor.getAllValues();
        assertEquals(8,all.stream().map(MailTemplateCustomization::getId).distinct().count());
        for(var record:all){
            assertEquals(32,record.getFields().size(),record.getId());
            assertTrue(record.getFields().containsKey("subject"));
            assertEquals("{location}",record.getFields().get("locationValue"));
            assertTrue(record.getFields().containsKey("footer"));
        }
    }
    @Test void existingLegacyCustomizationIsExpandedWithoutLosingEditedFields(){
        var sparse=Map.of("heading","Admin edited heading","locationLabel","Venue");
        when(repository.findById("CREATED_USER"))
            .thenReturn(Optional.of(new MailTemplateCustomization("CREATED_USER",sparse)));
        editor.initializeTemplatesInDatabase();
        var captor=org.mockito.ArgumentCaptor.forClass(MailTemplateCustomization.class);
        verify(repository,times(8)).save(captor.capture());
        var migrated=captor.getAllValues().stream()
            .filter(row->row.getId().equals("CREATED_USER")).findFirst().orElseThrow();
        assertEquals(32,migrated.getFields().size());
        assertEquals("Admin edited heading",migrated.getFields().get("heading"));
        assertEquals("Venue",migrated.getFields().get("locationLabel"));
    }
    @Test void savePersistsFullFieldsEvenWhenTheyMatchDefaults(){
        var defaults=editor.getEditor("CREATED","USER","https://meeting.example.com").fields();
        editor.save("CREATED","USER",defaults,"https://meeting.example.com");
        var captor=org.mockito.ArgumentCaptor.forClass(MailTemplateCustomization.class);
        verify(repository).save(captor.capture());
        assertEquals("CREATED_USER",captor.getValue().getId());
        assertEquals(32,captor.getValue().getFields().size());
        verify(repository,never()).deleteById(anyString());
    }
    @Test void resetPersistsDefaultsAndDoesNotDeleteRecord(){
        editor.reset("CANCELLED","USER");
        var captor=org.mockito.ArgumentCaptor.forClass(MailTemplateCustomization.class);
        verify(repository).save(captor.capture());
        assertEquals("CANCELLED_USER",captor.getValue().getId());
        assertEquals(32,captor.getValue().getFields().size());
        verify(repository,never()).deleteById(anyString());
    }
}
