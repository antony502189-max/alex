package com.alex.messenger.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<ContactEntity, ContactId> {

    List<ContactEntity> findAllByIdOwnerUserIdOrderByContactNameAsc(UUID ownerUserId);

    boolean existsByIdOwnerUserIdAndIdContactUserId(UUID ownerUserId, UUID contactUserId);
}
