package com.alex.messenger.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPrivacyExceptionRepository extends JpaRepository<UserPrivacyExceptionEntity, UUID> {

    List<UserPrivacyExceptionEntity> findAllByOwnerUserIdAndPrivacyTypeOrderByCreatedAtAsc(UUID ownerUserId, String privacyType);

    void deleteAllByOwnerUserIdAndPrivacyType(UUID ownerUserId, String privacyType);

    List<UserPrivacyExceptionEntity> findAllByOwnerUserIdAndTargetUserId(UUID ownerUserId, UUID targetUserId);
}
