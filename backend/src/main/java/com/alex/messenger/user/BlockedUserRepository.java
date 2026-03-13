package com.alex.messenger.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockedUserRepository extends JpaRepository<BlockedUserEntity, BlockedUserId> {

    boolean existsByIdOwnerUserIdAndIdBlockedUserId(UUID ownerUserId, UUID blockedUserId);

    List<BlockedUserEntity> findAllByIdOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

    @Query("""
            select relationship.id.blockedUserId
            from BlockedUserEntity relationship
            where relationship.id.ownerUserId = :ownerUserId
            """)
    List<UUID> findBlockedUserIdsByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);

    @Query("""
            select relationship.id.ownerUserId
            from BlockedUserEntity relationship
            where relationship.id.blockedUserId = :blockedUserId
            """)
    List<UUID> findOwnerUserIdsByBlockedUserId(@Param("blockedUserId") UUID blockedUserId);
}
