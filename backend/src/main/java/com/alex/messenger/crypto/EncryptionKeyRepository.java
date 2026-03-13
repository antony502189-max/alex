package com.alex.messenger.crypto;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncryptionKeyRepository extends JpaRepository<EncryptionKeyEntity, UUID> {

    Optional<EncryptionKeyEntity> findFirstByChatIdAndActiveTrue(UUID chatId);

    Optional<EncryptionKeyEntity> findByChatIdAndKeyVersion(UUID chatId, Integer keyVersion);

    Optional<EncryptionKeyEntity> findTopByChatIdOrderByKeyVersionDesc(UUID chatId);
}
