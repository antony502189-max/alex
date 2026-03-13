package com.alex.messenger.sticker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StickerRepository extends JpaRepository<StickerEntity, UUID> {

    List<StickerEntity> findAllByPackIdInOrderByPackIdAscPositionAsc(List<UUID> packIds);
}
