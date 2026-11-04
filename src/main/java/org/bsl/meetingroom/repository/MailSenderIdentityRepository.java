package org.bsl.meetingroom.repository;
import org.bsl.meetingroom.model.MailSenderIdentity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;
public interface MailSenderIdentityRepository extends MongoRepository<MailSenderIdentity,String> {
    Optional<MailSenderIdentity> findByEmailIgnoreCase(String email);
    List<MailSenderIdentity> findAllByOrderByEmailAsc();
}
