package org.bsl.meetingroom.repository;
import org.bsl.meetingroom.model.MailSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface MailSettingsRepository extends MongoRepository<MailSettings,String> {}
