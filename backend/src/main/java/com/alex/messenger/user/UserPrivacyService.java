package com.alex.messenger.user;

import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.user.dto.CloseFriendResponse;
import com.alex.messenger.user.dto.ReplaceCloseFriendsRequest;
import com.alex.messenger.user.dto.UpdatePrivacyExceptionsRequest;
import com.alex.messenger.user.dto.UserPrivacyExceptionsResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserPrivacyService {

    private static final Set<String> PRIVACY_TYPES = Set.of("PHONE", "LAST_SEEN", "STORY");

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final UserPrivacyExceptionRepository userPrivacyExceptionRepository;
    private final UserCloseFriendRepository userCloseFriendRepository;
    private final ProfilePhotoService profilePhotoService;

    @Transactional(readOnly = true)
    public UserPrivacyExceptionsResponse getPrivacyExceptions(UUID ownerUserId) {
        return new UserPrivacyExceptionsResponse(
                idsFor(ownerUserId, "PHONE", "ALLOW"),
                idsFor(ownerUserId, "PHONE", "DENY"),
                idsFor(ownerUserId, "LAST_SEEN", "ALLOW"),
                idsFor(ownerUserId, "LAST_SEEN", "DENY"),
                idsFor(ownerUserId, "STORY", "ALLOW"),
                idsFor(ownerUserId, "STORY", "DENY")
        );
    }

    @Transactional
    public UserPrivacyExceptionsResponse updatePrivacyExceptions(UUID ownerUserId, UpdatePrivacyExceptionsRequest request) {
        UpdatePrivacyExceptionsRequest effectiveRequest = request != null
                ? request
                : new UpdatePrivacyExceptionsRequest(null, null, null, null, null, null);
        replaceExceptions(ownerUserId, "PHONE", effectiveRequest.phoneAllowedUserIds(), effectiveRequest.phoneDisallowedUserIds());
        replaceExceptions(ownerUserId, "LAST_SEEN", effectiveRequest.lastSeenAllowedUserIds(), effectiveRequest.lastSeenDisallowedUserIds());
        replaceExceptions(ownerUserId, "STORY", effectiveRequest.storyAllowedUserIds(), effectiveRequest.storyDisallowedUserIds());
        return getPrivacyExceptions(ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<CloseFriendResponse> listCloseFriends(UUID ownerUserId) {
        List<UserCloseFriendEntity> relationships = userCloseFriendRepository.findAllByIdOwnerUserIdOrderByCreatedAtAsc(ownerUserId);
        if (relationships.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                relationships.stream().map(relationship -> relationship.getId().getFriendUserId()).toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return relationships.stream().map(relationship -> {
            UserEntity user = usersById.get(relationship.getId().getFriendUserId());
            if (user == null || user.getDeletedAt() != null) {
                return null;
            }
            PhotoAccess photoAccess = profilePhotoService.buildPhotoAccess(
                    user.getPhotoStorageProvider(),
                    user.getPhotoBucketName(),
                    user.getPhotoObjectKey()
            );
            return new CloseFriendResponse(
                    user.getId(),
                    user.getDisplayName(),
                    user.getUsername(),
                    photoAccess != null ? photoAccess.photoUrl() : null,
                    photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                    relationship.getCreatedAt()
            );
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public List<CloseFriendResponse> replaceCloseFriends(UUID ownerUserId, ReplaceCloseFriendsRequest request) {
        List<UUID> requestedUserIds = request != null && request.userIds() != null ? request.userIds() : List.of();
        Set<UUID> uniqueUserIds = new LinkedHashSet<>(requestedUserIds);
        uniqueUserIds.remove(ownerUserId);
        if (!uniqueUserIds.isEmpty()) {
            List<UserEntity> users = findActiveUsers(uniqueUserIds);
            if (users.size() != uniqueUserIds.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more users were not found");
            }
            boolean hasNonContact = uniqueUserIds.stream()
                    .anyMatch(friendUserId -> !contactRepository.existsByIdOwnerUserIdAndIdContactUserId(ownerUserId, friendUserId));
            if (hasNonContact) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Close friends must belong to your contacts");
            }
        }
        userCloseFriendRepository.deleteAllByIdOwnerUserId(ownerUserId);
        for (UUID friendUserId : uniqueUserIds) {
            UserCloseFriendEntity entity = new UserCloseFriendEntity();
            entity.setId(new UserCloseFriendId(ownerUserId, friendUserId));
            userCloseFriendRepository.save(entity);
        }
        return listCloseFriends(ownerUserId);
    }

    @Transactional(readOnly = true)
    public boolean canViewPhone(UUID requesterId, UserEntity targetUser) {
        return canView(requesterId, targetUser, "PHONE", targetUser.getPhonePrivacy(), false);
    }

    @Transactional(readOnly = true)
    public boolean canViewLastSeen(UUID requesterId, UserEntity targetUser) {
        return canView(requesterId, targetUser, "LAST_SEEN", targetUser.getLastSeenPrivacy(), false);
    }

    @Transactional(readOnly = true)
    public boolean canViewStory(UUID requesterId, UserEntity targetUser) {
        return canView(requesterId, targetUser, "STORY", targetUser.getStoryPrivacy(), true);
    }

    @Transactional(readOnly = true)
    public boolean isCloseFriend(UUID ownerUserId, UUID requesterId) {
        return userCloseFriendRepository.existsByIdOwnerUserIdAndIdFriendUserId(ownerUserId, requesterId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getCloseFriendIds(UUID ownerUserId) {
        return userCloseFriendRepository.findAllByIdOwnerUserIdOrderByCreatedAtAsc(ownerUserId).stream()
                .map(entity -> entity.getId().getFriendUserId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean canView(
            UUID requesterId,
            UserEntity targetUser,
            String privacyType,
            String defaultPrivacy,
            boolean allowCloseFriends
    ) {
        if (targetUser == null || targetUser.getDeletedAt() != null) {
            return false;
        }
        if (requesterId.equals(targetUser.getId())) {
            return true;
        }
        UserPrivacyExceptionEntity exception = userPrivacyExceptionRepository
                .findAllByOwnerUserIdAndTargetUserId(targetUser.getId(), requesterId)
                .stream()
                .filter(item -> privacyType.equals(item.getPrivacyType()))
                .findFirst()
                .orElse(null);
        if (exception != null) {
            return "ALLOW".equals(exception.getAccessMode());
        }

        String privacy = defaultPrivacy != null ? defaultPrivacy.trim().toUpperCase(Locale.ROOT) : "EVERYBODY";
        return switch (privacy) {
            case "EVERYBODY" -> true;
            case "CONTACTS" -> contactRepository.existsByIdOwnerUserIdAndIdContactUserId(targetUser.getId(), requesterId);
            case "NOBODY" -> false;
            case "CLOSE_FRIENDS" -> allowCloseFriends && isCloseFriend(targetUser.getId(), requesterId);
            default -> true;
        };
    }

    private List<UUID> idsFor(UUID ownerUserId, String privacyType, String accessMode) {
        return userPrivacyExceptionRepository.findAllByOwnerUserIdAndPrivacyTypeOrderByCreatedAtAsc(ownerUserId, privacyType)
                .stream()
                .filter(item -> accessMode.equals(item.getAccessMode()))
                .map(UserPrivacyExceptionEntity::getTargetUserId)
                .toList();
    }

    private void replaceExceptions(UUID ownerUserId, String privacyType, List<UUID> allowedIds, List<UUID> deniedIds) {
        if (!PRIVACY_TYPES.contains(privacyType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported privacy type");
        }
        Set<UUID> allowed = normalizeTargetIds(ownerUserId, allowedIds);
        Set<UUID> denied = normalizeTargetIds(ownerUserId, deniedIds);
        Set<UUID> overlap = new LinkedHashSet<>(allowed);
        overlap.retainAll(denied);
        if (!overlap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Privacy exception lists overlap");
        }
        Set<UUID> allTargets = new LinkedHashSet<>(allowed);
        allTargets.addAll(denied);
        if (!allTargets.isEmpty()) {
            List<UserEntity> users = findActiveUsers(allTargets);
            if (users.size() != allTargets.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more users were not found");
            }
        }
        userPrivacyExceptionRepository.deleteAllByOwnerUserIdAndPrivacyType(ownerUserId, privacyType);
        for (UUID targetUserId : allowed) {
            userPrivacyExceptionRepository.save(newException(ownerUserId, targetUserId, privacyType, "ALLOW"));
        }
        for (UUID targetUserId : denied) {
            userPrivacyExceptionRepository.save(newException(ownerUserId, targetUserId, privacyType, "DENY"));
        }
    }

    private Set<UUID> normalizeTargetIds(UUID ownerUserId, List<UUID> ids) {
        Set<UUID> normalized = ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
        normalized.remove(ownerUserId);
        return normalized;
    }

    private UserPrivacyExceptionEntity newException(UUID ownerUserId, UUID targetUserId, String privacyType, String accessMode) {
        UserPrivacyExceptionEntity entity = new UserPrivacyExceptionEntity();
        entity.setOwnerUserId(ownerUserId);
        entity.setTargetUserId(targetUserId);
        entity.setPrivacyType(privacyType);
        entity.setAccessMode(accessMode);
        return entity;
    }

    private List<UserEntity> findActiveUsers(Set<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .filter(user -> user.getDeletedAt() == null)
                .toList();
    }
}
