package com.alex.messenger.premium;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremiumCustomEmojiRepository extends JpaRepository<PremiumCustomEmojiEntity, UUID> {

    List<PremiumCustomEmojiEntity> findAllByOrderByPositionAscCreatedAtAsc();
}
