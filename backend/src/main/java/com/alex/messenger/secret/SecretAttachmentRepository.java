package com.alex.messenger.secret;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretAttachmentRepository extends JpaRepository<SecretAttachmentEntity, UUID> {

    List<SecretAttachmentEntity> findAllByIdIn(Collection<UUID> attachmentIds);

    List<SecretAttachmentEntity> findAllBySecretMessageIdIn(Collection<UUID> secretMessageIds);
}
