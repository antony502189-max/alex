package com.alex.messenger.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPhoneNumber(String phoneNumber);

    java.util.List<UserEntity> findAllByPhoneNumberIn(java.util.Collection<String> phoneNumbers);

    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    Optional<UserEntity> findByIdAndBotTrue(UUID id);

    java.util.List<UserEntity> findAllByBotTrueOrderByDisplayNameAsc();

    @Query("""
        select u
        from UserEntity u
        where lower(u.displayName) like lower(concat('%', :query, '%'))
           or lower(coalesce(u.username, '')) like lower(concat('%', :query, '%'))
           or u.phoneNumber like concat('%', :query, '%')
        order by u.displayName asc
        """)
    java.util.List<UserEntity> search(@Param("query") String query);

    @Modifying
    @Query("""
        update UserEntity u
        set u.lastSeenAt = :lastSeenAt
        where u.id = :userId
        """)
    int touchLastSeenAt(@Param("userId") UUID userId, @Param("lastSeenAt") java.time.Instant lastSeenAt);
}
