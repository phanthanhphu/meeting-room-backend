package org.bsl.meetingroom.mail;

import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.mail.MailDtos.TemplatePreviewResponse;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.MailTemplateCustomizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Structured, escaped and persisted editable content; delivery and Outlook ICS logic remain unchanged. */
@Service
public class MailTemplateEditorService {
    private static final List<String> EVENT_NAMES=List.of("CREATED","APPROVED","REJECTED","CANCELLED");
    private static final Set<String> EVENTS=Set.copyOf(EVENT_NAMES);
    private static final List<String> TEXT_FIELDS=List.of("subject","brandTitle","brandSubtitle","heading",
            "statusLabel","greeting","intro","detailsTitle","outro","buttonText","buttonUrl","footer");
    // Every meeting-detail entry has its own editable label (Title) and value (Content).
    // Keep existing field names intact so previously saved templates remain compatible.
    private static final List<String> DETAIL_FIELDS=List.of(
            "meetingTitleLabel","meetingTitleValue", "dateLabel","dateValue", "timeLabel","timeValue",
            "roomLabel","roomValue", "locationLabel","locationValue", "requestedByLabel","requestedByValue",
            "purposeLabel","purposeValue", "attendeesLabel","attendeesValue", "processedByLabel","processedByValue",
            "rejectionReasonLabel","rejectionReasonValue");
    private static final List<String> FIELDS;
    static {
        List<String> combined=new ArrayList<>(TEXT_FIELDS);
        combined.addAll(DETAIL_FIELDS);
        FIELDS=List.copyOf(combined);
    }
    private static final Map<String,String> DEFAULT_DETAIL_VALUES=Map.ofEntries(
            Map.entry("meetingTitleValue","{bookingTitle}"), Map.entry("dateValue","{date}"),
            Map.entry("timeValue","{time}"), Map.entry("roomValue","{roomName}"),
            Map.entry("locationValue","{location}"), Map.entry("requestedByValue","{requesterName}"),
            Map.entry("purposeValue","{purpose}"), Map.entry("attendeesValue","{attendees}"),
            Map.entry("processedByValue","{managerName}"),Map.entry("rejectionReasonValue","{adminNote}"));
    private static final Map<String,String> DEFAULT_DETAIL_LABELS=Map.ofEntries(
            Map.entry("meetingTitleLabel","Meeting Title"),Map.entry("dateLabel","Date"),
            Map.entry("timeLabel","Time"),Map.entry("roomLabel","Meeting Room"),
            Map.entry("locationLabel","Location"),Map.entry("requestedByLabel","Requested By"),
            Map.entry("purposeLabel","Purpose"),Map.entry("attendeesLabel","Attendees"),
            Map.entry("processedByLabel","Processed By"),Map.entry("rejectionReasonLabel","Rejection reason"));
    private static final Pattern TOKEN=Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> TOKENS=Set.of("bookingId","bookingTitle","roomName","location",
            "date","time","requesterName","managerName","bookingUrl","websiteUrl","status",
            "purpose","attendees","adminNote");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    private final MailTemplateCustomizationRepository repository;

    public MailTemplateEditorService(MailTemplateCustomizationRepository repository){this.repository=repository;}

    /**
     * Store every editable field for every event/audience in MongoDB, including fields that
     * still have their default value. Upgrades fill missing fields from existing sparse
     * customizations WITHOUT replacing admin-edited values. No manual Mongo migration needed.
     * This runs at application start, not on every email delivery or page refresh.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeTemplatesInDatabase(){
        for(String ev:EVENT_NAMES){
            for(String aud:List.of("USER","ADMIN")){
                String id=key(ev,aud);
                Map<String,String> defaults=defaultFields(ev,aud);
                Optional<MailTemplateCustomization> stored=repository.findById(id);
                Map<String,String> merged=withDefaults(defaults,stored.map(MailTemplateCustomization::getFields).orElse(null));
                if(stored.isEmpty() || !merged.equals(stored.get().getFields())){
                    repository.save(new MailTemplateCustomization(id,merged));
                }
            }
        }
    }

    /** Fields follow the stable Editor schema. Ignore unsupported legacy keys. */
    private static Map<String,String> withDefaults(Map<String,String> defaults,Map<String,String> stored){
        Map<String,String> merged=new LinkedHashMap<>(defaults);
        if(stored!=null){
            for(String field:FIELDS){
                if(stored.get(field)!=null)merged.put(field,stored.get(field));
            }
        }
        return merged;
    }

    /** Get default text and placeholder definitions without using real booking data. */
    private static Map<String,String> defaultFields(String event,String audience){
        PreviewSample sample=sample(event,"");
        return defaults(audience.equals("USER")?sample.user():sample.admin(),event,audience);
    }

    private static String event(String event){
        String key=event==null?"":event.trim().toUpperCase(Locale.ROOT);
        if(!EVENTS.contains(key))throw AppException.badRequest("INVALID_MAIL_EVENT","Unknown template event.");
        return key;
    }
    private static String audience(String audience){
        String key=audience==null?"":audience.trim().toUpperCase(Locale.ROOT);
        if(!key.equals("USER") && !key.equals("ADMIN"))
            throw AppException.badRequest("INVALID_MAIL_AUDIENCE","Expected USER or ADMIN.");
        return key;
    }
    private static String key(String event,String audience){return event(event)+"_"+audience(audience);}
    private static String marker(String name){return "<!--mail:"+name+"-->";}
    private static String closing(String name){return "<!--/mail:"+name+"-->";}
    private static String raw(String html,String name){
        int at=html.indexOf(marker(name));
        if(at<0)return "";
        int from=at+marker(name).length(),to=html.indexOf(closing(name),from);
        return to<0?"":html.substring(from,to);
    }
    private static String extract(String html,String name){
        String content=raw(html,name).replaceAll("(?i)<br\\s*/?>","\n")
                .replaceAll("(?s)<[^>]*>","");
        return HtmlUtils.htmlUnescape(content).trim();
    }
    private static String replace(String html,String name,String replacement){
        String begin=marker(name), end=closing(name);
        int at=html.indexOf(begin);
        if(at<0)return html;
        int to=html.indexOf(end,at+begin.length());
        if(to<0)return html;
        return html.substring(0,at)+begin+replacement+end+html.substring(to+end.length());
    }
    private static String plain(String value){
        return BookingMailTemplates.safe(value).replace("\r\n","\n").replace("\r","\n").replace("\n","<br>");
    }
    private static String tag(String field,String text){
        if(text.isBlank())return "";
        String style=switch(field){
            case "brandTitle" -> "font-size:15px;font-weight:700;letter-spacing:0.3px;";
            case "brandSubtitle" -> "font-size:11px;color:#C8D9E8;margin-top:4px;";
            case "heading" -> "font-size:23px;line-height:1.25;font-weight:700;color:#153A5B;margin-bottom:12px;";
            case "detailsTitle" -> "color:#64748B;font-size:11px;letter-spacing:0.7px;font-weight:700;margin-bottom:6px;";
            default -> "";
        };
        if(!style.isEmpty())return "<div style='"+style+"'>"+plain(text)+"</div>";
        style=switch(field){
            case "greeting" -> "margin:0 0 11px;font-size:14px;line-height:1.5;color:#27384A;";
            case "intro" -> "margin:0 0 21px;font-size:14px;line-height:1.65;color:#27384A;";
            case "outro" -> "margin:17px 0 0;font-size:13px;line-height:1.6;color:#526578;";
            case "footer" -> "margin:0;font-size:11px;color:#8493A7;line-height:1.6;";
            default -> "";
        };
        String result="<p style='"+style+"'>"+plain(text)+"</p>";
        if(field.equals("footer"))return "<tr><td bgcolor='#F8FAFC' style='padding:15px 24px;text-align:center;border-top:1px solid #E7EDF3;background:#F8FAFC;'>"+result+"</td></tr>";
        return result;
    }
    /** Replace only the text of a detail label/value, retaining the source HTML layout and escaping input. */
    private static String detailMarkup(String original,String value){
        if(value.isBlank())return "";
        Matcher wrapper=Pattern.compile("(?s)^\\s*(<([a-zA-Z]+)[^>]*>).*?(</\\2>)\\s*$").matcher(original);
        if(wrapper.matches())return wrapper.group(1)+plain(value)+wrapper.group(3);
        return plain(value);
    }
    private static Map<String,String> tokens(Booking b, MeetingRoom room, User requester, User actor,
                                              String originalUrl,String websiteUrl,String event){
        String title=b==null?"":Objects.toString(b.getTitle(),"");
        String name=requester==null?(b==null?"":Objects.toString(b.getCreatedByAccount(),"")):Objects.toString(requester.getFullName(),"");
        String manager=actor==null?"":Objects.toString(actor.getFullName(),"");
        String date=b==null||b.getStartAt()==null?"":b.getStartAt().format(DATE);
        String time=b==null||b.getStartAt()==null||b.getEndAt()==null?"":b.getStartAt().format(TIME)+" - "+b.getEndAt().format(TIME);
        Map<String,String> values=new HashMap<>();
        values.put("bookingId",b==null?"":Objects.toString(b.getId(),""));values.put("bookingTitle",title);
        values.put("requesterName",name);values.put("managerName",manager);
        values.put("roomName",room==null?"":Objects.toString(room.getName(),""));
        values.put("location",room==null?"":Objects.toString(room.getLocation(),""));
        values.put("date",date);values.put("time",time);
        values.put("purpose",b==null?"":Objects.toString(b.getPurpose(),""));
        values.put("attendees",b==null?"":String.valueOf(b.getAttendeeCount()));
        values.put("adminNote",b==null?"":Objects.toString(b.getAdminNote(),""));
        values.put("bookingUrl",Objects.toString(originalUrl,""));
        values.put("websiteUrl",Objects.toString(websiteUrl,""));values.put("status",event);
        return values;
    }
    private static String interpolate(String input, Map<String,String> values,boolean url){
        Matcher matcher=TOKEN.matcher(input);StringBuffer out=new StringBuffer();
        while(matcher.find()){
            String value=values.getOrDefault(matcher.group(1),"");
            if(url && !matcher.group(1).equals("bookingUrl") && !matcher.group(1).equals("websiteUrl"))
                value=URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");
            matcher.appendReplacement(out,Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
    private static String normalizeUrl(String url){
        if(url==null || url.isBlank())return null;
        try{
            java.net.URI uri=new java.net.URI(url);
            if(!Set.of("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT)) ||
                    uri.getHost()==null || uri.getUserInfo()!=null || url.matches(".*[\\s<>\\\"'].*"))return null;
            return url;
        }catch(Exception ex){return null;}
    }
    public BookingMailTemplates.Content apply(String event,String audience,BookingMailTemplates.Content original,
            Booking b,MeetingRoom room,User requester,User actor,String defaultLink,String websiteUrl){
        String ev=event(event),aud=audience(audience);
        Optional<MailTemplateCustomization> stored=repository.findById(key(ev,aud));
        if(stored.isEmpty())return original; // safe before initial seeding completes
        Map<String,String> defaults=defaultFields(ev,aud);
        Map<String,String> fullFields=withDefaults(defaults,stored.get().getFields());
        // Render from the saved fields, not a diff against Java defaults. Even fields that
        // match defaults in MongoDB must win over hard-coded source text (e.g. the DB value
        // "Date" must not be replaced by the original HTML literal "DATE").
        Map<String,String> values=tokens(b,room,requester,actor,defaultLink,websiteUrl,ev);
        return applyFields(original,fullFields,values,defaultButtonLabel(ev,aud));
    }
    private static BookingMailTemplates.Content applyFields(BookingMailTemplates.Content source,Map<String,String> config,
                                                            Map<String,String> vars,String defaultButtonText){
        String html=source.html(),subject=source.subject();
        if(config.containsKey("subject"))subject=interpolate(config.get("subject"),vars,false).replaceAll("[\\r\\n]+"," ").trim();
        for(String part:List.of("brandTitle","brandSubtitle","heading","statusLabel","greeting","intro","detailsTitle","outro","footer")){
            if(!config.containsKey(part))continue;
            String text=interpolate(config.get(part),vars,false);
            // Preserve exact source markup when its visible content already matches the
            // configured value, but never compare against a second hard-coded default.
            // In particular, preserve inline rich text and Outlook-specific structure.
            String original=raw(html,part);
            if(text.equals(extract(html,part)))continue;
            // The badge is a special section: an empty label removes the badge entirely.
            if(part.equals("statusLabel")){
                // Preserve the original event color (green APPROVED, red REJECTED, etc.).
                String originalBadge=raw(html,part), originalText=extract(html,part);
                html=replace(html,part,text.isBlank()?"":
                        originalBadge.replace(BookingMailTemplates.safe(originalText),plain(text)));
            }
            else if(part.equals("detailsTitle"))
                html=replace(html,part,detailMarkup(original,text));
            else html=replace(html,part,tag(part,text));
        }
        for(String part:DETAIL_FIELDS){
            if(!config.containsKey(part))continue;
            String old=raw(html,part);
            if(old.isEmpty())continue; // e.g. Processed By is absent for a newly created booking.
            String value=interpolate(config.get(part),vars,false);
            if(!value.equals(extract(html,part)))
                html=replace(html,part,detailMarkup(old,value));
        }
        // If Location content is explicitly cleared, hide the entire row (no empty spacing).
        if(config.containsKey("locationValue") && interpolate(config.get("locationValue"),vars,false).isBlank())
            html=replace(html,"locationRow","");
        if(config.containsKey("buttonText") || config.containsKey("buttonUrl")){
            String label=interpolate(config.getOrDefault("buttonText",defaultButtonText),vars,false);
            String href=normalizeUrl(interpolate(config.getOrDefault("buttonUrl","{bookingUrl}"),vars,true));
            html=replace(html,"button",BookingMailTemplates.buttonHtml(href,label));
        }
        return new BookingMailTemplates.Content(subject,html);
    }
    public MailDtos.TemplateEditorResponse getEditor(String event,String audience,String site){
        String ev=event(event),aud=audience(audience);
        PreviewSample sample=sample(ev,site);
        BookingMailTemplates.Content original=aud.equals("USER")?sample.user():sample.admin();
        Map<String,String> defaults=defaults(original,ev,aud);
        Optional<MailTemplateCustomization> saved=repository.findById(key(ev,aud));
        Map<String,String> merged=withDefaults(defaults,saved.map(MailTemplateCustomization::getFields).orElse(null));
        boolean customized=!onlyChanged(merged,defaults).isEmpty();
        return new MailDtos.TemplateEditorResponse(ev,aud,customized,merged,defaults);
    }
    private static String defaultButtonLabel(String event,String audience){
        return event.equals("CREATED")?(audience.equals("USER")?"View My Booking":"Review Booking"):
               (audience.equals("USER")?"View Booking Details":"View Booking");
    }
    private static Map<String,String> defaults(BookingMailTemplates.Content mail,String event,String audience){
        Map<String,String> defaults=new LinkedHashMap<>();
        defaults.put("subject",mail.subject().replace("Weekly IT Project Meeting","{bookingTitle}"));
        for(String field:List.of("brandTitle","brandSubtitle","heading","statusLabel","greeting","intro","detailsTitle","outro")){
            String value=extract(mail.html(),field).replace("Nguyen Van An","{requesterName}");
            defaults.put(field,value);
        }
        for(String field:DETAIL_FIELDS){
            if(DEFAULT_DETAIL_VALUES.containsKey(field)) defaults.put(field,DEFAULT_DETAIL_VALUES.get(field));
            else {
                String extracted=extract(mail.html(),field);
                defaults.put(field,extracted.isBlank()?DEFAULT_DETAIL_LABELS.getOrDefault(field,""):extracted);
            }
        }
        defaults.put("buttonText",defaultButtonLabel(event,audience));
        defaults.put("buttonUrl","{bookingUrl}");
        defaults.put("footer",extract(mail.html(),"footer"));
        return defaults;
    }
    private static void validateFields(Map<String,String> fields){
        if(fields==null || !fields.keySet().equals(new LinkedHashSet<>(FIELDS)))
            throw AppException.badRequest("INVALID_TEMPLATE_FIELDS","All supported email fields must be provided.");
        for(String field:FIELDS){
            String val=fields.get(field);
            if(val==null || val.length()>(field.equals("buttonUrl")?2048:field.equals("subject")?300:3000))
                throw AppException.badRequest("INVALID_TEMPLATE_FIELD","Invalid size for "+field+".");
            Matcher m=TOKEN.matcher(val);
            while(m.find())if(!TOKENS.contains(m.group(1)))
                throw AppException.badRequest("UNKNOWN_MAIL_VARIABLE","Unsupported variable: "+m.group(1));
            // Reject all leftover brace markers: most often a misspelled variable.
            if(val.replaceAll("\\{[a-zA-Z]+}","").matches("(?s).*[{}].*"))
                throw AppException.badRequest("INVALID_MAIL_VARIABLE","Invalid placeholder in "+field+".");
        }
        if(fields.get("subject").isBlank())throw AppException.badRequest("INVALID_SUBJECT","Email Subject cannot be blank.");
        String buttonUrl=fields.get("buttonUrl");
        if(!buttonUrl.isBlank() && !buttonUrl.contains("{bookingUrl}") && !buttonUrl.contains("{websiteUrl}") &&
            !buttonUrl.matches("(?i)^https?://.*"))
            throw AppException.badRequest("INVALID_TEMPLATE_URL","Button link must use HTTP(S) or a permitted URL variable.");
        if(buttonUrl.matches("(?s).*[\\r\\n<>\\\"'].*"))
            throw AppException.badRequest("INVALID_TEMPLATE_URL","Button link contains invalid characters.");
    }
    private static Map<String,String> onlyChanged(Map<String,String> fields,BookingMailTemplates.Content base,
                                                   String event,String audience){
        return onlyChanged(fields,defaults(base,event,audience));
    }
    private static Map<String,String> onlyChanged(Map<String,String> fields,Map<String,String> defaults){
        Map<String,String> changes=new LinkedHashMap<>();
        for(String field:FIELDS){
            if(!Objects.equals(fields.get(field),defaults.get(field)))changes.put(field,fields.get(field));
        }
        return changes;
    }
    public MailDtos.TemplateEditorResponse save(String event,String audience,Map<String,String> fields,String site){
        String ev=event(event),aud=audience(audience);
        validateFields(fields);
        // Persist the COMPLETE editor content, even when nothing was customized.
        // Use a copy to make sure the saved state cannot be mutated after validation.
        repository.save(new MailTemplateCustomization(key(ev,aud),fields));
        return getEditor(ev,aud,site);
    }
    public void reset(String event,String audience){
        String ev=event(event),aud=audience(audience);
        // Keep the database record; Reset Default restores its entire field map.
        repository.save(new MailTemplateCustomization(key(ev,aud),defaultFields(ev,aud)));
    }
    /** Preview unsaved edits for the chosen audience without saving or queueing messages. */
    public TemplatePreviewResponse previewDraft(String event,String audience,Map<String,String> fields,String site){
        String ev=event(event),aud=audience(audience);
        validateFields(fields);
        PreviewSample sample=sample(ev,site);
        String url=Objects.toString(site,"");
        BookingMailTemplates.Content user=aud.equals("USER")?
            applyFields(sample.user(),fields,tokens(sample.booking(),sample.room(),sample.requester(),sample.manager(),
                sample.userUrl(),url,ev),defaultButtonLabel(ev,aud)):
            apply(ev,"USER",sample.user(),sample.booking(),sample.room(),sample.requester(),sample.manager(),sample.userUrl(),url);
        BookingMailTemplates.Content admin=aud.equals("ADMIN")?
            applyFields(sample.admin(),fields,tokens(sample.booking(),sample.room(),sample.requester(),sample.manager(),
                sample.adminUrl(),url,ev),defaultButtonLabel(ev,aud)):
            apply(ev,"ADMIN",sample.admin(),sample.booking(),sample.room(),sample.requester(),sample.manager(),sample.adminUrl(),url);
        String icon="data:image/png;base64,"+Base64.getEncoder().encodeToString(BookingMailTemplates.brandIconBytes());
        return new TemplatePreviewResponse(ev,user.subject(),BookingMailTemplates.browserPreviewHtml(user.html(),icon),
            admin.subject(),BookingMailTemplates.browserPreviewHtml(admin.html(),icon));
    }
    public TemplatePreviewResponse preview(String event,String site){
        PreviewSample sample=sample(event(event),site);
        String url=Objects.toString(site,"");
        BookingMailTemplates.Content user=apply(sample.event(),"USER",sample.user(),sample.booking(),sample.room(),sample.requester(),sample.manager(),sample.userUrl(),url);
        BookingMailTemplates.Content admin=apply(sample.event(),"ADMIN",sample.admin(),sample.booking(),sample.room(),sample.requester(),sample.manager(),sample.adminUrl(),url);
        String icon="data:image/png;base64,"+Base64.getEncoder().encodeToString(BookingMailTemplates.brandIconBytes());
        return new TemplatePreviewResponse(sample.event(),user.subject(),BookingMailTemplates.browserPreviewHtml(user.html(),icon),
                admin.subject(),BookingMailTemplates.browserPreviewHtml(admin.html(),icon));
    }
    private record PreviewSample(String event, Booking booking,MeetingRoom room, User requester,User manager,
                                 String userUrl,String adminUrl,BookingMailTemplates.Content user, BookingMailTemplates.Content admin){}
    private static PreviewSample sample(String event,String websiteUrl){
        Booking b=new Booking();b.setId("MR-20261106-0012");b.setCreatedByAccount("Nguyen Van An");
        b.setTitle("Weekly IT Project Meeting");b.setPurpose("Weekly project status update");b.setAttendeeCount(8);
        b.setStartAt(LocalDateTime.of(2026,11,6,9,0));b.setEndAt(LocalDateTime.of(2026,11,6,10,30));
        b.setAdminNote("Room unavailable at the requested time.");
        MeetingRoom room=new MeetingRoom();room.setName("Conference Room A");room.setLocation("Building A - Floor 2");
        User owner=new User();owner.setFullName("Nguyen Van An");
        User manager=new User();manager.setFullName("Room Manager");
        String base=websiteUrl==null?"":websiteUrl.replaceAll("/+$","");
        String userUrl=base.isBlank()?null:base+"/my-bookings?booking=sample";
        String adminUrl=base.isBlank()?null:base+"/approvals?booking=sample";
        BookingMailTemplates.Content user,admin;
        switch(event){
            case "CREATED" -> {user=BookingMailTemplates.pendingConfirmation(b,room,owner,userUrl);admin=BookingMailTemplates.created(b,room,owner,adminUrl);}
            case "APPROVED" -> {user=BookingMailTemplates.approved(b,room,owner,manager,userUrl);admin=BookingMailTemplates.adminApproved(b,room,owner,manager,adminUrl);}
            case "REJECTED" -> {user=BookingMailTemplates.rejected(b,room,owner,manager,userUrl);admin=BookingMailTemplates.adminRejected(b,room,owner,manager,adminUrl);}
            case "CANCELLED" -> {user=BookingMailTemplates.cancelled(b,room,owner,manager,userUrl);admin=BookingMailTemplates.adminCancelled(b,room,owner,manager,adminUrl);}
            default -> throw AppException.badRequest("INVALID_MAIL_EVENT","Unknown event.");
        }
        return new PreviewSample(event,b,room,owner,manager,userUrl,adminUrl,user,admin);
    }
}
