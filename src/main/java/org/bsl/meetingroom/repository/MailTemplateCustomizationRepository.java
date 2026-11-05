package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.MailTemplateCustomization;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MailTemplateCustomizationRepository extends MongoRepository<MailTemplateCustomization,String> {}
