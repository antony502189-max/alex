package com.alex.messenger.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCloseFriendRepository extends JpaRepository<UserCloseFriendEntity, UserCloseFriendId> {

    List<UserCloseFriendEntity> findAllByIdOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    boolean existsByIdOwnerUserIdAndIdFriendUserId(UUID ownerUserId, UUID friendUserId);

    void deleteAllByIdOwnerUserId(UUID ownerUserId);
}
