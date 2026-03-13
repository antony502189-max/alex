package com.alex.messenger.attachment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentUploadSessionRepository extends JpaRepository<AttachmentUploadSessionEntity, UUID> {

    Optional<AttachmentUploadSessionEntity> findByIdAndUploaderUserId(UUID id, UUID uploaderUserId);

    List<AttachmentUploadSessionEntity> findByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
            Collection<String> statuses,
            Instant expiresAt,
            Pageable pageable
    );
}
