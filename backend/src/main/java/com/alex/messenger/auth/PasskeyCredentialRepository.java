package com.alex.messenger.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredentialEntity, UUID> {

    List<PasskeyCredentialEntity> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<PasskeyCredentialEntity> findByCredentialIdAndRevokedAtIsNull(String credentialId);

    long countByUserIdAndRevokedAtIsNull(UUID userId);
}
