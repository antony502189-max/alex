package com.alex.messenger.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.user.dto.BlockUserRequest;
import com.alex.messenger.user.dto.ReportUserRequest;
import com.alex.messenger.user.dto.UpdateLanguagePreferencesRequest;
import com.alex.messenger.user.dto.UserReportResponse;
import com.alex.messenger.user.dto.UserSearchResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private BlockedUserRepository blockedUserRepository;

    @Mock
    private UserReportRepository userReportRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    @Mock
    private UserPresenceService userPresenceService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                contactRepository,
                blockedUserRepository,
                userReportRepository,
                profilePhotoService,
                userPresenceService
        );
    }

    @Test
    void searchHidesBlockedRelationshipsInBothDirections() {
        UUID requesterId = UUID.randomUUID();
        UUID hiddenByRequesterId = UUID.randomUUID();
        UUID blockerId = UUID.randomUUID();

        UserEntity visibleUser = user("Visible", UUID.randomUUID());
        UserEntity hiddenByRequester = user("Hidden", hiddenByRequesterId);
        UserEntity blocker = user("Blocker", blockerId);

        when(blockedUserRepository.findBlockedUserIdsByOwnerUserId(requesterId))
                .thenReturn(List.of(hiddenByRequesterId));
        when(blockedUserRepository.findOwnerUserIdsByBlockedUserId(requesterId))
                .thenReturn(List.of(blockerId));
        when(userRepository.search("vi"))
                .thenReturn(List.of(visibleUser, hiddenByRequester, blocker));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any()))
                .thenReturn(new PhotoAccess(null, null));
        when(userPresenceService.resolvePresence(any(), any()))
                .thenReturn(new UserPresenceService.UserPresenceView(false, null));

        List<UserSearchResponse> results = userService.search(requesterId, "vi");

        assertThat(results).extracting(UserSearchResponse::userId)
                .containsExactly(visibleUser.getId());
    }

    @Test
    void blockUserRejectsSelfBlocking() {
        UUID requesterId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> userService.blockUser(requesterId, new BlockUserRequest(requesterId)),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(blockedUserRepository, never()).save(any(BlockedUserEntity.class));
    }

    @Test
    void reportUserPersistsNormalizedCategory() {
        UUID requesterId = UUID.randomUUID();
        UUID reportedUserId = UUID.randomUUID();

        UserEntity reportedUser = user("Reported", reportedUserId);
        UserReportEntity savedReport = new UserReportEntity();
        savedReport.setId(UUID.randomUUID());
        savedReport.setReportedUserId(reportedUserId);
        savedReport.setCategory("SPAM");
        savedReport.setCreatedAt(java.time.Instant.parse("2026-03-12T12:00:00Z"));

        when(userRepository.findById(reportedUserId)).thenReturn(Optional.of(reportedUser));
        when(userReportRepository.save(any(UserReportEntity.class))).thenReturn(savedReport);

        UserReportResponse response = userService.reportUser(
                requesterId,
                new ReportUserRequest(reportedUserId, "spam", "Repeated phishing attempts")
        );

        assertThat(response.reportId()).isEqualTo(savedReport.getId());
        assertThat(response.reportedUserId()).isEqualTo(reportedUserId);
        assertThat(response.category()).isEqualTo("SPAM");
    }

    @Test
    void updateLanguagePreferencesNormalizesValues() {
        UUID requesterId = UUID.randomUUID();
        UserEntity requester = user("Requester", requesterId);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.updateLanguagePreferences(
                requesterId,
                new UpdateLanguagePreferencesRequest(" EN ", " DE ")
        );

        assertThat(response.preferredLanguage()).isEqualTo("en");
        assertThat(response.translationTargetLanguage()).isEqualTo("de");
        assertThat(requester.getPreferredLanguage()).isEqualTo("en");
        assertThat(requester.getTranslationTargetLanguage()).isEqualTo("de");
    }

    private UserEntity user(String displayName, UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setPhoneNumber("+375291234567");
        user.setPhonePrivacy("EVERYBODY");
        user.setLastSeenPrivacy("EVERYBODY");
        user.setStoryPrivacy("EVERYBODY");
        return user;
    }
}
