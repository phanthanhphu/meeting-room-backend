package org.bsl.meetingroom.model;

import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/** Catalog entry (device type), shareable between meeting rooms. */
@Document(collection="meeting_equipment")
public class Equipment {
    @Id private String id;
    @Indexed(unique=true) private String code;
    private String name;
    private String category;
    private String description;
    private EquipmentStatus status=EquipmentStatus.ACTIVE;
    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
    @Version private Long version;

    public String getId(){return id;} public void setId(String v){id=v;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public EquipmentStatus getStatus(){return status;} public void setStatus(EquipmentStatus v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public Long getVersion(){return version;}
}
