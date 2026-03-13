package com.alex.messenger.sticker;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StickerPackRepository extends JpaRepository<StickerPackEntity, UUID> {

    List<StickerPackEntity> findAllByOrderByTitleAsc();
}
