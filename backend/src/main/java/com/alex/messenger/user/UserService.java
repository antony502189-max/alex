package com.alex.messenger.user;

import com.alex.messenger.user.dto.AddContactRequest;
import com.alex.messenger.user.dto.BlockUserRequest;
import com.alex.messenger.user.dto.BlockedUserResponse;
import com.alex.messenger.user.dto.ContactResponse;
import com.alex.messenger.user.dto.ImportContactsRequest;
import com.alex.messenger.user.dto.ImportContactsResponse;
import com.alex.messenger.user.dto.ImportedPhoneContactPayload;
import com.alex.messenger.user.dto.ReportUserRequest;
import com.alex.messenger.user.dto.UpdateLanguagePreferencesRequest;
import com.alex.messenger.user.dto.UpdatePrivacyRequest;
import com.alex.messenger.user.dto.UpdateProfileRequest;
import com.alex.messenger.user.dto.UserLanguagePreferencesResponse;
import com.alex.messenger.user.dto.UserProfileResponse;
import com.alex.messenger.user.dto.UserReportResponse;
import com.alex.messenger.user.dto.UserSearchResponse;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.media.StoredPhotoReference;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> PRIVACY_VALUES = Set.of("EVERYBODY", "CONTACTS", "NOBODY");
    private static final Set<String> REPORT_CATEGORIES = Set.of(
            "SPAM",
            "ABUSE",
            "IMPERSONATION",
            "SCAM",
            "COPYRIGHT",
            "OTHER"
    );

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final UserReportRepository userReportRepository;
    private final ProfilePhotoService profilePhotoService;
    private final UserPresenceService userPresenceService;

    @Transactional(readOnly = true)
    public List<UserSearchResponse> search(UUID requesterId, String query) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        Set<UUID> hiddenUserIds = getHiddenUserIds(requesterId);

        return userRepository.search(normalizedQuery).stream()
                .filter(user -> !user.getId().equals(requesterId))
                .filter(user -> !hiddenUserIds.contains(user.getId()))
                .limit(20)
                .map(user -> {
                    PhotoAccess photoAccess = buildPhotoAccess(user);
                    UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);
                    return new UserSearchResponse(
                            user.getId(),
                            user.isBot() ? null : (canViewPhone(requesterId, user) ? user.getPhoneNumber() : null),
                            user.getDisplayName(),
                            user.getUsername(),
                            user.isBot(),
                            user.getBotDescription(),
                            user.isBotSupportsInline(),
                            user.getBotWebAppUrl(),
                            photoAccess.photoUrl(),
                            photoAccess.photoAccessExpiresAt(),
                            presence.online(),
                            presence.lastSeenAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlockedUsers(UUID requesterId) {
        List<BlockedUserEntity> relationships = blockedUserRepository.findAllByIdOwnerUserIdOrderByCreatedAtDesc(requesterId);
        List<UserEntity> users = userRepository.findAllById(
                relationships.stream().map(relationship -> relationship.getId().getBlockedUserId()).toList()
        );
        var usersById = users.stream().collect(Collectors.toMap(UserEntity::getId, user -> user));

        return relationships.stream()
                .map(relationship -> {
                    UserEntity user = usersById.get(relationship.getId().getBlockedUserId());
                    return user != null ? toBlockedUser(user, relationship.getCreatedAt(), requesterId) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID requesterId) {
        return toProfile(getUser(requesterId), requesterId);
    }

    @Transactional(readOnly = true)
    public UserLanguagePreferencesResponse getLanguagePreferences(UUID requesterId) {
        UserEntity user = getUser(requesterId);
        return new UserLanguagePreferencesResponse(
                user.getPreferredLanguage(),
                user.getTranslationTargetLanguage()
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID requesterId, UpdateProfileRequest request) {
        UserEntity user = getUser(requesterId);

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.about() != null) {
            user.setAbout(request.about().trim().isBlank() ? null : request.about().trim());
        }
        if (request.username() != null) {
            user.setUsername(normalizeAndValidateUsername(requesterId, request.username()));
        }

        return toProfile(userRepository.save(user), requesterId);
    }

    @Transactional
    public UserProfileResponse updatePrivacy(UUID requesterId, UpdatePrivacyRequest request) {
        UserEntity user = getUser(requesterId);
        user.setPhonePrivacy(normalizePrivacy(request.phonePrivacy()));
        user.setLastSeenPrivacy(normalizePrivacy(request.lastSeenPrivacy()));
        user.setStoryPrivacy(normalizePrivacy(request.storyPrivacy()));
        return toProfile(userRepository.save(user), requesterId);
    }

    @Transactional
    public UserLanguagePreferencesResponse updateLanguagePreferences(
            UUID requesterId,
            UpdateLanguagePreferencesRequest request
    ) {
        UserEntity user = getUser(requesterId);
        user.setPreferredLanguage(normalizeLanguage(request.preferredLanguage()));
        user.setTranslationTargetLanguage(normalizeLanguage(request.translationTargetLanguage()));
        UserEntity saved = userRepository.save(user);
        return new UserLanguagePreferencesResponse(saved.getPreferredLanguage(), saved.getTranslationTargetLanguage());
    }

    @Transactional
    public UserProfileResponse updateProfilePhoto(UUID requesterId, MultipartFile file) {
        UserEntity user = getUser(requesterId);
        StoredPhotoReference photo = profilePhotoService.uploadUserPhoto(requesterId, file);

        String previousStorageProvider = user.getPhotoStorageProvider();
        String previousBucketName = user.getPhotoBucketName();
        String previousObjectKey = user.getPhotoObjectKey();

        user.setPhotoStorageProvider(photo.storageProvider());
        user.setPhotoBucketName(photo.bucketName());
        user.setPhotoObjectKey(photo.objectKey());
        user.setPhotoContentType(photo.contentType());
        user.setPhotoUpdatedAt(java.time.Instant.now());

        UserProfileResponse response = toProfile(userRepository.save(user), requesterId);
        profilePhotoService.deletePhoto(previousStorageProvider, previousBucketName, previousObjectKey);
        return response;
    }

    @Transactional
    public UserProfileResponse removeProfilePhoto(UUID requesterId) {
        UserEntity user = getUser(requesterId);
        String previousStorageProvider = user.getPhotoStorageProvider();
        String previousBucketName = user.getPhotoBucketName();
        String previousObjectKey = user.getPhotoObjectKey();

        user.setPhotoStorageProvider(null);
        user.setPhotoBucketName(null);
        user.setPhotoObjectKey(null);
        user.setPhotoContentType(null);
        user.setPhotoUpdatedAt(null);

        UserProfileResponse response = toProfile(userRepository.save(user), requesterId);
        profilePhotoService.deletePhoto(previousStorageProvider, previousBucketName, previousObjectKey);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts(UUID requesterId) {
        List<ContactEntity> contacts = contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId);
        List<UserEntity> users = userRepository.findAllById(
                contacts.stream().map(contact -> contact.getId().getContactUserId()).toList()
        );
        var usersById = users.stream().collect(Collectors.toMap(UserEntity::getId, user -> user));

        return contacts.stream()
                .map(contact -> {
                    UserEntity user = usersById.get(contact.getId().getContactUserId());
                    if (user == null) {
                        return null;
                    }
                    return toContactResponse(requesterId, user, contact.getContactName());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public List<ContactResponse> addContact(UUID requesterId, AddContactRequest request) {
        if (requesterId.equals(request.contactUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add yourself as a contact");
        }

        UserEntity contactUser = getUser(request.contactUserId());
        String contactName = request.contactName() != null && !request.contactName().isBlank()
                ? request.contactName().trim()
                : contactUser.getDisplayName();

        ContactEntity entity = new ContactEntity();
        entity.setId(new ContactId(requesterId, request.contactUserId()));
        entity.setContactName(contactName);
        contactRepository.save(entity);
        return listContacts(requesterId);
    }

    @Transactional
    public ImportContactsResponse importContacts(UUID requesterId, ImportContactsRequest request) {
        List<ImportedPhoneContactPayload> importedContacts = request.contacts().stream()
                .filter(contact -> contact != null)
                .map(this::normalizeImportedContact)
                .filter(contact -> contact != null)
                .toList();
        if (importedContacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid contacts were provided");
        }

        Set<UUID> hiddenUserIds = getHiddenUserIds(requesterId);
        Map<String, String> contactNamesByPhone = importedContacts.stream()
                .collect(Collectors.toMap(
                        ImportedPhoneContactPayload::phoneNumber,
                        contact -> contact.contactName() != null ? contact.contactName() : "",
                        (left, right) -> left
                ));
        List<UserEntity> matches = userRepository.findAllByPhoneNumberIn(contactNamesByPhone.keySet()).stream()
                .filter(user -> !user.getId().equals(requesterId))
                .filter(user -> !hiddenUserIds.contains(user.getId()))
                .toList();

        boolean persistMatches = Boolean.TRUE.equals(request.persistMatches());
        if (persistMatches) {
            for (UserEntity user : matches) {
                ContactEntity entity = new ContactEntity();
                entity.setId(new ContactId(requesterId, user.getId()));
                entity.setContactName(resolveImportedContactName(contactNamesByPhone.get(user.getPhoneNumber()), user));
                contactRepository.save(entity);
            }
        }

        Set<String> matchedPhones = matches.stream()
                .map(UserEntity::getPhoneNumber)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> unmatchedPhoneNumbers = importedContacts.stream()
                .map(ImportedPhoneContactPayload::phoneNumber)
                .filter(phoneNumber -> !matchedPhones.contains(phoneNumber))
                .distinct()
                .toList();

        return new ImportContactsResponse(
                importedContacts.size(),
                matches.size(),
                persistMatches,
                unmatchedPhoneNumbers,
                matches.stream()
                        .map(user -> toContactResponse(
                                requesterId,
                                user,
                                resolveImportedContactName(contactNamesByPhone.get(user.getPhoneNumber()), user)
                        ))
                        .toList()
        );
    }

    @Transactional
    public List<BlockedUserResponse> blockUser(UUID requesterId, BlockUserRequest request) {
        if (requesterId.equals(request.blockedUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot block yourself");
        }

        getUser(request.blockedUserId());
        BlockedUserId relationshipId = new BlockedUserId(requesterId, request.blockedUserId());
        if (!blockedUserRepository.existsById(relationshipId)) {
            BlockedUserEntity relationship = new BlockedUserEntity();
            relationship.setId(relationshipId);
            blockedUserRepository.save(relationship);
        }

        return listBlockedUsers(requesterId);
    }

    @Transactional
    public List<BlockedUserResponse> unblockUser(UUID requesterId, UUID blockedUserId) {
        blockedUserRepository.deleteById(new BlockedUserId(requesterId, blockedUserId));
        return listBlockedUsers(requesterId);
    }

    @Transactional
    public UserReportResponse reportUser(UUID requesterId, ReportUserRequest request) {
        if (requesterId.equals(request.reportedUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot report yourself");
        }

        getUser(request.reportedUserId());
        UserReportEntity report = new UserReportEntity();
        report.setReporterUserId(requesterId);
        report.setReportedUserId(request.reportedUserId());
        report.setCategory(normalizeReportCategory(request.category()));
        report.setDetails(normalizeReportDetails(request.details()));

        UserReportEntity saved = userReportRepository.save(report);
        return new UserReportResponse(saved.getId(), saved.getReportedUserId(), saved.getCategory(), saved.getCreatedAt());
    }

    @Transactional
    public List<ContactResponse> removeContact(UUID requesterId, UUID contactUserId) {
        contactRepository.deleteById(new ContactId(requesterId, contactUserId));
        return listContacts(requesterId);
    }

    @Transactional(readOnly = true)
    public boolean isDirectInteractionBlocked(UUID requesterId, UUID peerUserId) {
        return blockedUserRepository.existsByIdOwnerUserIdAndIdBlockedUserId(requesterId, peerUserId)
                || blockedUserRepository.existsByIdOwnerUserIdAndIdBlockedUserId(peerUserId, requesterId);
    }

    public boolean canViewPhone(UUID requesterId, UserEntity targetUser) {
        if (requesterId.equals(targetUser.getId())) {
            return true;
        }

        String privacy = targetUser.getPhonePrivacy() != null ? targetUser.getPhonePrivacy() : "EVERYBODY";
        return switch (privacy) {
            case "EVERYBODY" -> true;
            case "CONTACTS" -> contactRepository.existsByIdOwnerUserIdAndIdContactUserId(targetUser.getId(), requesterId);
            case "NOBODY" -> false;
            default -> true;
        };
    }

    private UserProfileResponse toProfile(UserEntity user, UUID requesterId) {
        PhotoAccess photoAccess = buildPhotoAccess(user);
        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);
        return new UserProfileResponse(
                user.getId(),
                user.isBot() ? null : (canViewPhone(requesterId, user) ? user.getPhoneNumber() : null),
                user.getDisplayName(),
                user.getUsername(),
                user.isBot(),
                user.getBotDescription(),
                user.isBotSupportsInline(),
                user.getBotWebAppUrl(),
                user.getAbout(),
                photoAccess.photoUrl(),
                photoAccess.photoAccessExpiresAt(),
                user.getPhonePrivacy(),
                user.getLastSeenPrivacy(),
                user.getStoryPrivacy(),
                user.getPreferredLanguage(),
                user.getTranslationTargetLanguage(),
                presence.lastSeenAt(),
                presence.online()
        );
    }

    private ContactResponse toContactResponse(UUID requesterId, UserEntity user, String contactName) {
        PhotoAccess photoAccess = buildPhotoAccess(user);
        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);
        return new ContactResponse(
                user.getId(),
                contactName,
                user.getDisplayName(),
                user.getUsername(),
                user.isBot(),
                user.getBotDescription(),
                user.isBotSupportsInline(),
                user.getBotWebAppUrl(),
                user.isBot() ? null : user.getPhoneNumber(),
                photoAccess.photoUrl(),
                photoAccess.photoAccessExpiresAt(),
                presence.online(),
                presence.lastSeenAt()
        );
    }

    private BlockedUserResponse toBlockedUser(UserEntity user, java.time.Instant blockedAt, UUID requesterId) {
        PhotoAccess photoAccess = buildPhotoAccess(user);
        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);
        return new BlockedUserResponse(
                user.getId(),
                user.isBot() ? null : (canViewPhone(requesterId, user) ? user.getPhoneNumber() : null),
                user.getDisplayName(),
                user.getUsername(),
                user.isBot(),
                user.getBotDescription(),
                user.isBotSupportsInline(),
                user.getBotWebAppUrl(),
                photoAccess.photoUrl(),
                photoAccess.photoAccessExpiresAt(),
                presence.online(),
                presence.lastSeenAt(),
                blockedAt
        );
    }

    private PhotoAccess buildPhotoAccess(UserEntity user) {
        return profilePhotoService.buildPhotoAccess(
                user.getPhotoStorageProvider(),
                user.getPhotoBucketName(),
                user.getPhotoObjectKey()
        );
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Set<UUID> getHiddenUserIds(UUID requesterId) {
        Set<UUID> hiddenUserIds = new LinkedHashSet<>(blockedUserRepository.findBlockedUserIdsByOwnerUserId(requesterId));
        hiddenUserIds.addAll(blockedUserRepository.findOwnerUserIdsByBlockedUserId(requesterId));
        return hiddenUserIds;
    }

    private String normalizePrivacy(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PRIVACY_VALUES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported privacy value");
        }
        return normalized;
    }

    private String normalizeLanguage(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        String result = normalized.toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z-]{2,16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language code");
        }
        return result;
    }

    private String normalizeAndValidateUsername(UUID requesterId, String username) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must match [a-z0-9_]{3,64}");
        }
        userRepository.findByUsernameIgnoreCase(normalized)
                .filter(existing -> !existing.getId().equals(requesterId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
                });
        return normalized;
    }

    private String normalizeReportCategory(String value) {
        String normalized = value != null ? value.trim().toUpperCase(Locale.ROOT) : "OTHER";
        if (!REPORT_CATEGORIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported report category");
        }
        return normalized;
    }

    private String normalizeReportDetails(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private ImportedPhoneContactPayload normalizeImportedContact(ImportedPhoneContactPayload contact) {
        String normalizedPhoneNumber = contact.phoneNumber() != null ? contact.phoneNumber().trim() : "";
        if (normalizedPhoneNumber.isBlank()) {
            return null;
        }
        String normalizedContactName = contact.contactName() != null && !contact.contactName().trim().isBlank()
                ? contact.contactName().trim()
                : null;
        return new ImportedPhoneContactPayload(normalizedPhoneNumber, normalizedContactName);
    }

    private String resolveImportedContactName(String requestedName, UserEntity user) {
        return requestedName != null && !requestedName.isBlank() ? requestedName : user.getDisplayName();
    }
}
