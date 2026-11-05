package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.model.Booking;
import org.bsl.meetingroom.model.MeetingRoom;
import org.bsl.meetingroom.model.User;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/** Pure HTML generation: all booking/user-controlled text is HTML-escaped. */
public final class BookingMailTemplates {
    private BookingMailTemplates() {}
    public record Content(String subject, String html) {}
    // Use a MIME Content-ID image so Outlook can display the brand logo without
    // downloading remote content. The same bundled icon is used for User and Admin.
    public static final String BRAND_ICON_CID = "natf-meeting-room-brand";
    private static final String BRAND_ICON_RESOURCE = "/mail/meeting-room-brand-icon.png";

    public static byte[] brandIconBytes() {
        try (InputStream input = BookingMailTemplates.class.getResourceAsStream(BRAND_ICON_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing bundled mail icon: " + BRAND_ICON_RESOURCE);
            return input.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load bundled mail icon", ex);
        }
    }

    /** Inline data URI is ONLY for the browser preview; actual email uses CID. */
    public static String browserPreviewHtml(String html, String iconDataUri) {
        return html.replace("cid:" + BRAND_ICON_CID, iconDataUri);
    }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy",Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm",Locale.ENGLISH);

    public static Content created(Booking b,MeetingRoom room,User requester,String url){
        String name = requester==null ? b.getCreatedByAccount() : requester.getFullName();
        String details=details(b,room,name,null);
        return new Content(subject("[Meeting Room] New booking request - ",b),wrap("NEW BOOKING REQUEST","PENDING APPROVAL","#B45309",
                "Dear Room Manager,","A new meeting room request is awaiting your review.",details,
                "Please sign in to review and decide on this request.",url,"Review Booking"));
    }
    public static Content pendingConfirmation(Booking b,MeetingRoom room,User requester,String url){
        String name = requester==null ? b.getCreatedByAccount() : requester.getFullName();
        return new Content(subject("[Meeting Room] Request received - ",b),wrap("BOOKING REQUEST RECEIVED","PENDING APPROVAL","#B45309",
                "Dear "+safe(name)+",","Your request has been received and is <strong>waiting for approval</strong>.",
                userDetails(b,room),"You will receive a separate email when the room manager approves or rejects it.",url,"View My Booking"));
    }
    public static Content cancelled(Booking b,MeetingRoom room,User requester,User actor,String url){
        String name=requester==null?b.getCreatedByAccount():requester.getFullName();
        return new Content(subject("[Meeting Room] Booking cancelled - ",b),wrap("BOOKING CANCELLATION","CANCELLED","#B91C1C",
                "Dear "+safe(name)+",","The following meeting room booking has been <strong>cancelled</strong>.",
                userDetails(b,room),"If the meeting had been approved, the Outlook invitation has been cancelled as well.",url,"View Booking Details"));
    }
    public static Content adminApproved(Booking b,MeetingRoom room,User requester,User manager,String url){
        String name=requester==null?b.getCreatedByAccount():requester.getFullName();
        return new Content(subject("[Meeting Room Admin] Approved booking - ",b),wrap("BOOKING ADMIN NOTIFICATION","APPROVED","#15803D",
                "Dear Room Manager,","A booking has been approved and the requester has been notified separately.",
                details(b,room,name,manager),"This is an informational management notice; it does not reserve your personal Outlook calendar.",url,"View Booking"));
    }
    public static Content adminRejected(Booking b,MeetingRoom room,User requester,User manager,String url){
        String name=requester==null?b.getCreatedByAccount():requester.getFullName();
        return new Content(subject("[Meeting Room Admin] Rejected booking - ",b),wrap("BOOKING ADMIN NOTIFICATION","REJECTED","#B91C1C",
                "Dear Room Manager,","A booking request has been rejected and the requester has been notified.",
                details(b,room,name,manager, tr("rejectionReason","Rejection reason",b.getAdminNote())),"This message is for administrative tracking.",url,"View Booking"));
    }
    public static Content adminCancelled(Booking b,MeetingRoom room,User requester,User actor,String url){
        String name=requester==null?b.getCreatedByAccount():requester.getFullName();
        return new Content(subject("[Meeting Room Admin] Cancelled booking - ",b),wrap("BOOKING ADMIN NOTIFICATION","CANCELLED","#B91C1C",
                "Dear Room Manager,","A meeting room reservation has been cancelled and is no longer active.",
                details(b,room,name,actor),"If the booking had been approved, a separate Outlook cancellation was sent to its owner.",url,"View Booking"));
    }
    /** Preview uses synthetic data and exactly the templates that production sends. */
    public static MailDtos.TemplatePreviewResponse preview(String event){return preview(event,"");}
    public static MailDtos.TemplatePreviewResponse preview(String event,String websiteUrl){
        String key=event==null?"":event.trim().toUpperCase(Locale.ROOT);
        Booking b=new Booking();b.setId("MR-20261106-0012");b.setCreatedByAccount("Nguyen Van An");
        b.setTitle("Weekly IT Project Meeting");b.setPurpose("Weekly project status update");b.setAttendeeCount(8);
        b.setStartAt(java.time.LocalDateTime.of(2026,11,6,9,0));
        b.setEndAt(java.time.LocalDateTime.of(2026,11,6,10,30));b.setAdminNote("Room unavailable at the requested time.");
        MeetingRoom room=new MeetingRoom();room.setName("Conference Room A");room.setLocation("Building A - Floor 2");
        User requester=new User();requester.setFullName("Nguyen Van An");
        User manager=new User();manager.setFullName("Room Manager");
        String base=websiteUrl==null?"":websiteUrl.replaceAll("/+$", "");
        String userUrl=base.isBlank()?null:base+"/my-bookings?booking=sample";
        String adminUrl=base.isBlank()?null:base+"/approvals?booking=sample";
        Content user,admin;
        switch(key){
            case "CREATED", "PENDING" -> {user=pendingConfirmation(b,room,requester,userUrl);admin=created(b,room,requester,adminUrl);}
            case "APPROVED" -> {user=approved(b,room,requester,manager,userUrl);admin=adminApproved(b,room,requester,manager,adminUrl);}
            case "REJECTED" -> {user=rejected(b,room,requester,manager,userUrl);admin=adminRejected(b,room,requester,manager,adminUrl);}
            case "CANCELLED" -> {user=cancelled(b,room,requester,manager,userUrl);admin=adminCancelled(b,room,requester,manager,adminUrl);}
            default -> throw org.bsl.meetingroom.exception.AppException.badRequest("INVALID_MAIL_EVENT","Unknown notification event.");
        }
        String logoPreview = "data:image/png;base64," + Base64.getEncoder().encodeToString(brandIconBytes());
        return new MailDtos.TemplatePreviewResponse(key,user.subject(),browserPreviewHtml(user.html(),logoPreview),
                admin.subject(),browserPreviewHtml(admin.html(),logoPreview));
    }
    public static Content approved(Booking b,MeetingRoom room,User requester,User manager,String url){
        String name=requester==null?b.getCreatedByAccount():requester.getFullName();
        return new Content(subject("[Meeting Room] Booking approved - ",b),wrap("BOOKING CONFIRMATION","APPROVED","#15803D",
                "Dear "+safe(name)+",","Your meeting room booking request has been <strong>approved</strong>.",
                userDetails(b,room),"Select Accept in the Outlook invitation to add this meeting to your calendar.",url,"View Booking Details"));
    }
    public static Content rejected(Booking b,MeetingRoom room,User requester,User manager,String url){
        String name=requester==null?b.getCreatedByAccount():requester.getFullName();
        String reason="<tr><td colspan='2' style='padding:13px;background:#FFF2F2;border:1px solid #F4CACA;border-radius:6px;'>"+
                "<strong style='color:#B91C1C;'>"+part("rejectionReasonLabel","Reason for rejection")+"</strong><br>"+
                part("rejectionReasonValue",safe(b.getAdminNote()))+"</td></tr>";
        return new Content(subject("[Meeting Room] Booking rejected - ",b),wrap("BOOKING STATUS","REJECTED","#B91C1C",
                "Dear "+safe(name)+",","Your meeting room booking request has been <strong>rejected</strong>.",
                userDetails(b,room,reason),"Please choose another available time slot or meeting room.",url,"View Booking Details"));
    }
    public static Content test(String name){
        return new Content("[Meeting Room] Test email",wrap("EMAIL CONFIGURATION","TEST MESSAGE","#2563EB",
                "Dear "+safe(name)+",","This test email confirms that the configured SMTP sender has been accepted by the mail service.",
                "","No booking was created or changed.",null,null));
    }
    private static String subject(String prefix,Booking b){
        String title=b.getTitle()==null?"":b.getTitle().replaceAll("[\\r\\n]"," ");
        return prefix+title.substring(0,Math.min(title.length(),100));
    }
    private static String userDetails(Booking b,MeetingRoom room){return userDetails(b,room,"");}
    /** Public-facing summary: do not expose optional or internal administration fields. */
    private static String userDetails(Booking b,MeetingRoom room,String extra){
        String title=safe(b.getTitle());
        String date=safe(b.getStartAt().format(DATE));
        String time=safe(b.getStartAt().format(TIME)+" - "+b.getEndAt().format(TIME));
        String roomName=safe(room==null?null:room.getName());
        String location=safe(room==null?null:room.getLocation());
        return "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='width:100%;border-collapse:collapse;border:1px solid #E1E9F0;'>"+
                "<tr><td colspan='2' bgcolor='#F6F9FC' style='padding:17px 18px 15px;background:#F6F9FC;'>"+
                part("detailsTitle","<div style='color:#64748B;font-size:11px;letter-spacing:0.7px;font-weight:700;margin-bottom:6px;'>Meeting</div>")+
                part("meetingTitleValue","<div style='color:#173653;font-size:17px;font-weight:700;line-height:1.35;'>"+title+"</div>")+"</td></tr>"+
                "<tr><td width='50%' valign='top' style='padding:15px 18px;border-top:1px solid #E1E9F0;border-right:1px solid #E1E9F0;'>"+
                part("dateLabel","<div style='color:#64748B;font-size:11px;margin-bottom:5px;'>DATE</div>")+
                part("dateValue","<div style='font-size:14px;font-weight:bold;color:#173653;'>"+date+"</div>")+"</td>"+
                "<td width='50%' valign='top' style='padding:15px 18px;border-top:1px solid #E1E9F0;'>"+
                part("timeLabel","<div style='color:#64748B;font-size:11px;margin-bottom:5px;'>TIME</div>")+
                part("timeValue","<div style='font-size:14px;font-weight:bold;color:#173653;'>"+time+"</div>")+"</td></tr>"+
                "<tr><td colspan='2' style='padding:13px 18px;border-top:1px solid #E1E9F0;font-size:13px;line-height:1.6;'>"+
                part("roomLabel","<div style='color:#64748B;'>Meeting Room</div>")+
                part("roomValue","<strong style='color:#173653;'>"+roomName+"</strong>")+"</td></tr>"+
                (location.isBlank()?"":part("locationRow","<tr><td colspan='2' style='padding:13px 18px;border-top:1px solid #E1E9F0;font-size:13px;line-height:1.6;'>"+
                 part("locationLabel","<div style='color:#64748B;'>Location</div>")+
                 part("locationValue","<strong style='color:#173653;'>"+location+"</strong>")+"</td></tr>"))+
                extra+"</table>";
    }
    private static String details(Booking b,MeetingRoom room,String requester,User manager){return details(b,room,requester,manager,"");}
    private static String details(Booking b,MeetingRoom room,String requester,User manager,String extra){
        StringBuilder rows=new StringBuilder();
        if(requester!=null) rows.append(tr("requestedBy","Requested By",requester));
        rows.append(tr("room","Meeting Room",room==null?null:room.getName()));
        rows.append(tr("location","Location",room==null?null:room.getLocation()));
        rows.append(tr("date","Date",b.getStartAt().format(DATE)));
        rows.append(tr("time","Time",b.getStartAt().format(TIME)+" - "+b.getEndAt().format(TIME)));
        rows.append(tr("meetingTitle","Meeting Title",b.getTitle()));
        rows.append(tr("purpose","Purpose",b.getPurpose()));
        rows.append(tr("attendees","Attendees",String.valueOf(b.getAttendeeCount())));
        if(manager!=null) rows.append(tr("processedBy","Processed By",manager.getFullName()));
        rows.append(extra);
        return "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='width:100%;border-collapse:collapse;background:#F6F9FC;'>"+rows+"</table>";
    }
    private static String tr(String key,String label,String value){
        return part(key+"Row","<tr><td style='padding:10px 14px;color:#64748B;width:37%;font-size:13px;border-bottom:1px solid #E5EDF5;'>"+
                part(key+"Label",safe(label))+"</td><td style='padding:10px 14px;color:#183A5B;font-weight:600;font-size:13px;border-bottom:1px solid #E5EDF5;overflow-wrap:anywhere;'>"+
                part(key+"Value",safe(value))+"</td></tr>");
    }
    public static String part(String name,String contents){return "<!--mail:"+name+"-->"+contents+"<!--/mail:"+name+"-->";}
    public static String buttonHtml(String url,String button){
        if(url==null || !url.matches("(?i)^https?://[^\\s<>]+$") || button==null || button.isBlank())return "";
        String href=safe(url);String text=safe(button);
        return "<table role='presentation' cellspacing='0' cellpadding='0' border='0' style='margin:22px 0 12px;border-collapse:collapse;'><tr><td align='left'>"+
            "<!--[if mso]><v:roundrect xmlns:v='urn:schemas-microsoft-com:vml' xmlns:w='urn:schemas-microsoft-com:office:word' href='"+href+"' style='height:44px;v-text-anchor:middle;width:215px;' arcsize='10%' stroke='f' fillcolor='#155A95'><w:anchorlock/><center style='color:#FFFFFF;font-family:Arial,sans-serif;font-size:14px;font-weight:bold;'>"+text+" &gt;</center></v:roundrect><![endif]-->"+
            "<!--[if !mso]><!--><a href='"+href+"' target='_blank' style='background-color:#155A95;border:1px solid #155A95;border-radius:6px;color:#FFFFFF!important;display:inline-block;padding:13px 22px;text-decoration:none;font-family:Arial,Helvetica,sans-serif;font-size:14px;font-weight:700;line-height:18px;'>"+
            "<span style='color:#FFFFFF!important;text-decoration:none;'>"+text+" &#8594;</span></a><!--<![endif]-->"+
            "</td></tr></table>";
    }
    private static String wrap(String heading,String status,String color,String salutation,String intro,String details,String outro,String url,String button){
        String link=buttonHtml(url,button);
        String title=heading.substring(0,1).toUpperCase(Locale.ENGLISH)+heading.substring(1).toLowerCase(Locale.ENGLISH);
        return "<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head>"+
                "<body style='margin:0;padding:0;background-color:#F3F6FA;font-family:Arial,Helvetica,sans-serif;color:#27384A;'>"+
                "<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' bgcolor='#F3F6FA' style='width:100%;border-collapse:collapse;background-color:#F3F6FA;'>"+
                "<tr><td align='center' style='padding:24px 12px;'>"+
                "<table role='presentation' width='580' cellspacing='0' cellpadding='0' border='0' bgcolor='#FFFFFF' style='width:100%;max-width:580px;border-collapse:collapse;background-color:#FFFFFF;border:1px solid #E0E7EF;'>"+
                "<tr><td bgcolor='#153A5B' style='padding:19px 25px;background-color:#153A5B;color:#FFFFFF;'>"+
                "<table role='presentation' cellspacing='0' cellpadding='0' border='0'><tr>"+
                "<td width='39' height='39' bgcolor='#FFFFFF' align='center' valign='middle' style='width:39px;height:39px;background:#FFFFFF;border-radius:4px;'>"+
                "<img src='cid:"+BRAND_ICON_CID+"' alt='Calendar' width='33' height='33' style='width:33px;height:33px;display:block;margin:0 auto;border:0;outline:none;text-decoration:none;'>"+
                "</td>"+
                "<td style='padding-left:12px;color:#FFFFFF;'>"+part("brandTitle","<div style='font-size:15px;font-weight:700;letter-spacing:0.3px;'>Meeting Room Booking</div>")+
                part("brandSubtitle","<div style='font-size:11px;color:#C8D9E8;margin-top:4px;'>AUTOMATED NOTIFICATION</div>")+"</td></tr></table></td></tr>"+
                "<tr><td style='padding:24px 27px 20px;'>"+
                part("heading","<div style='font-size:23px;line-height:1.25;font-weight:700;color:#153A5B;margin-bottom:12px;'>"+safe(title)+"</div>")+
                part("statusLabel","<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;margin:0 0 20px;'>"+
                "<tr><td align='center' valign='middle' bgcolor='#F8FAFD' style='padding:12px 14px;border-left:4px solid "+color+";background-color:#F8FAFD;color:"+color+";font-size:12px;font-weight:700;letter-spacing:0.2px;text-align:center;vertical-align:middle;'>"+safe(status)+"</td></tr></table>")+
                part("greeting","<p style='margin:0 0 11px;font-size:14px;line-height:1.5;color:#27384A;'>"+salutation+"</p>")+
                part("intro","<p style='margin:0 0 21px;font-size:14px;line-height:1.65;color:#27384A;'>"+intro+"</p>")+
                details+
                part("outro","<p style='margin:17px 0 0;font-size:13px;line-height:1.6;color:#526578;'>"+safe(outro)+"</p>")+
                part("button",link)+"</td></tr>"+
                part("footer","<tr><td bgcolor='#F8FAFC' style='padding:15px 24px;text-align:center;border-top:1px solid #E7EDF3;background:#F8FAFC;'>"+
                    "<p style='margin:0;font-size:11px;color:#8493A7;line-height:1.6;'>This is an automated message from Meeting Room Booking.<br>Please do not reply to this email.</p></td></tr>")+
                "</table></td></tr></table></body></html>";
    }
    public static String safe(String value){
        if(value==null)return "";
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}
