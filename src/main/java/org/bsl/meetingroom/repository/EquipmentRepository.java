package org.bsl.meetingroom.repository;

import org.bsl.meetingroom.model.Equipment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends MongoRepository<Equipment,String> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<Equipment> findByNameIgnoreCase(String name);
    List<Equipment> findAllByOrderByNameAsc();
}
