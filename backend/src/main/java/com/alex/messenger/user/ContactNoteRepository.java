package com.alex.messenger.user;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactNoteRepository extends JpaRepository<ContactNoteEntity, ContactNoteId> {

    Optional<ContactNoteEntity> findByIdOwnerUserIdAndIdContactUserId(UUID ownerUserId, UUID contactUserId);

    List<ContactNoteEntity> findAllByIdOwnerUserIdAndBirthdayIsNotNullOrderByBirthdayAsc(UUID ownerUserId);

    List<ContactNoteEntity> findAllByIdOwnerUserIdAndBirthdayBetweenOrderByBirthdayAsc(
            UUID ownerUserId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );
}
