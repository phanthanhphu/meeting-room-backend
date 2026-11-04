package org.bsl.meetingroom.repository;
import org.bsl.meetingroom.model.MailOutboxMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface MailOutboxRepository extends MongoRepository<MailOutboxMessage,String> {
    List<MailOutboxMessage> findTop100ByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
