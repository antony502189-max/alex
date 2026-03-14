package com.alex.messenger.monetization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMonetizationWithdrawalRepository extends JpaRepository<ChannelMonetizationWithdrawalEntity, UUID> {

    List<ChannelMonetizationWithdrawalEntity> findAllByChannelChatIdOrderByRequestedAtDesc(UUID channelChatId);

    Optional<ChannelMonetizationWithdrawalEntity> findByIdAndChannelChatId(UUID id, UUID channelChatId);

    Optional<ChannelMonetizationWithdrawalEntity> findFirstByProviderReferenceOrderByRequestedAtDesc(String providerReference);

    @Query(value = """
        SELECT *
        FROM channel_monetization_withdrawals
        WHERE status = 'PENDING'
          AND requested_at <= :eligibleBefore
        ORDER BY requested_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationWithdrawalEntity> lockPendingBatch(
            @Param("eligibleBefore") Instant eligibleBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM channel_monetization_withdrawals
        WHERE status = 'PROCESSING'
          AND processing_at <= :eligibleBefore
        ORDER BY processing_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationWithdrawalEntity> lockProcessingBatch(
            @Param("eligibleBefore") Instant eligibleBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM channel_monetization_withdrawals
        WHERE channel_chat_id = :chatId
          AND status = 'PROCESSING'
        ORDER BY processing_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationWithdrawalEntity> lockProcessingByChannel(
            @Param("chatId") UUID chatId,
            @Param("batchSize") int batchSize
    );
}
