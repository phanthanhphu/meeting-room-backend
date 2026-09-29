package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import org.bsl.meetingroom.model.Role;

public interface UserRepository extends MongoRepository<User,String>{
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByDomainAccountIgnoreCase(String domainAccount);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleAndEnabledTrue(Role role);
    List<User> findByRoleInAndEnabledTrue(Collection<Role> roles);
    List<User> findByEnabledTrue();
}
