package com.alex.messenger.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaProcessingJobRepository extends JpaRepository<MediaProcessingJobEntity, UUID> {

    Optional<MediaProcessingJobEntity> findByOwnerTypeAndOwnerIdAndJobType(
            String ownerType,
            UUID ownerId,
            String jobType
    );

    List<MediaProcessingJobEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
