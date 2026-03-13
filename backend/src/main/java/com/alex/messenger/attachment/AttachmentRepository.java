package com.alex.messenger.attachment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, UUID> {

    List<AttachmentEntity> findAllByIdIn(Collection<UUID> ids);

    List<AttachmentEntity> findAllByAlbumIdOrderByAlbumItemIndexAscCreatedAtAsc(UUID albumId);
}
