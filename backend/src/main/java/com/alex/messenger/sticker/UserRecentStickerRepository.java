package com.alex.messenger.sticker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRecentStickerRepository extends JpaRepository<UserRecentStickerEntity, UserStickerId> {

    List<UserRecentStickerEntity> findAllByIdUserIdOrderByUsedAtDesc(UUID userId);
}
