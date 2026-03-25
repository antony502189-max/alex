package com.alex.messenger.sticker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteStickerRepository extends JpaRepository<UserFavoriteStickerEntity, UserStickerId> {

    List<UserFavoriteStickerEntity> findAllByIdUserIdOrderByCreatedAtDesc(UUID userId);
}
