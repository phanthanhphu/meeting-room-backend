package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.AppNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NotificationRepository extends MongoRepository<AppNotification,String> {
    List<AppNotification> findTop50ByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
    List<AppNotification> findByRecipientUserIdAndReadFalse(String recipientUserId);
    long countByRecipientUserIdAndReadFalse(String recipientUserId);
    void deleteByRecipientUserId(String recipientUserId);
}
