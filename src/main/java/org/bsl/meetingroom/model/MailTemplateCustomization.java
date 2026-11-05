package org.bsl.meetingroom.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saved per event and recipient type. Never stores evaluated booking data. */
@Document(collection = "mail_template_customizations")
public class MailTemplateCustomization {
    @Id private String id;
    private Map<String,String> fields = new LinkedHashMap<>();
    public MailTemplateCustomization() {}
    public MailTemplateCustomization(String id,Map<String,String> fields) {
        this.id=id;this.fields=new LinkedHashMap<>(fields);
    }
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public Map<String,String> getFields(){return fields==null?Map.of():fields;}
    public void setFields(Map<String,String> fields){this.fields=new LinkedHashMap<>(fields);}
}
