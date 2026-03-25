package com.alex.messenger.sticker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRecentGifRepository extends JpaRepository<UserRecentGifEntity, UserAttachmentUsageId> {

    List<UserRecentGifEntity> findAllByIdUserIdOrderByUsedAtDesc(UUID userId);
}
