package com.alex.messenger.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @Query("""
        select u
        from UserEntity u
        where u.phoneNumber = :phoneNumber
          and u.deletedAt is null
        """)
    Optional<UserEntity> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("""
        select u
        from UserEntity u
        where u.phoneNumber in :phoneNumbers
          and u.deletedAt is null
        """)
    java.util.List<UserEntity> findAllByPhoneNumberIn(@Param("phoneNumbers") java.util.Collection<String> phoneNumbers);

    @Query("""
        select u
        from UserEntity u
        where lower(u.username) = lower(:username)
          and u.deletedAt is null
        """)
    Optional<UserEntity> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("""
        select u
        from UserEntity u
        where u.id = :id
          and u.bot = true
          and u.deletedAt is null
        """)
    Optional<UserEntity> findByIdAndBotTrue(@Param("id") UUID id);

    @Query("""
        select u
        from UserEntity u
        where u.bot = true
          and u.deletedAt is null
        order by u.displayName asc
        """)
    java.util.List<UserEntity> findAllByBotTrueOrderByDisplayNameAsc();

    @Query("""
        select u
        from UserEntity u
        where u.deletedAt is null
          and (
               lower(u.displayName) like lower(concat('%', :query, '%'))
           or lower(coalesce(u.username, '')) like lower(concat('%', :query, '%'))
           or u.phoneNumber like concat('%', :query, '%')
          )
        order by u.displayName asc
        """)
    java.util.List<UserEntity> search(@Param("query") String query);

    java.util.List<UserEntity> findTop100ByDeletedAtIsNullOrderByLastSeenAtAsc();

    java.util.List<UserEntity> findTop100ByDeletedAtIsNullAndLastSeenAtBeforeOrderByLastSeenAtAsc(java.time.Instant cutoff);

    @Modifying
    @Query("""
        update UserEntity u
        set u.lastSeenAt = :lastSeenAt
        where u.id = :userId
        """)
    int touchLastSeenAt(@Param("userId") UUID userId, @Param("lastSeenAt") java.time.Instant lastSeenAt);
}
