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
import com.alex.messenger.user.dto.ImportContactsRequest;
import com.alex.messenger.user.dto.ImportedPhoneContactPayload;
import com.alex.messenger.user.dto.ReportUserRequest;
import com.alex.messenger.user.dto.UpdateLanguagePreferencesRequest;
import com.alex.messenger.user.dto.UpsertContactNoteRequest;
import com.alex.messenger.user.dto.UpcomingBirthdayResponse;
import com.alex.messenger.user.dto.UserReportResponse;
import com.alex.messenger.user.dto.UserSearchResponse;
import java.time.LocalDate;
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
    private ContactNoteRepository contactNoteRepository;

    @Mock
    private BlockedUserRepository blockedUserRepository;

    @Mock
    private UserReportRepository userReportRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private UserPrivacyService userPrivacyService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                contactRepository,
                contactNoteRepository,
                blockedUserRepository,
                userReportRepository,
                profilePhotoService,
                userPresenceService,
                userPrivacyService
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
                .thenReturn(new UserPresenceService.UserPresenceView(false, null, "HIDDEN", "hidden"));
        when(userPrivacyService.canViewPhone(any(), any())).thenReturn(true);

        List<UserSearchResponse> results = userService.search(requesterId, "vi");

        assertThat(results).extracting(UserSearchResponse::userId)
                .containsExactly(visibleUser.getId());
    }

    @Test
    void searchRejectsTooLongQuery() {
        UUID requesterId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> userService.search(requesterId, "a".repeat(256)),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).search(any());
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

    @Test
    void upsertContactNoteDeletesMetadataWhenEverythingIsCleared() {
        UUID requesterId = UUID.randomUUID();
        UUID contactUserId = UUID.randomUUID();

        ContactNoteEntity existing = new ContactNoteEntity();
        existing.setId(new ContactNoteId(requesterId, contactUserId));
        existing.setNote("Remember this");
        existing.setBirthday(LocalDate.of(1999, 1, 1));

        when(contactRepository.existsByIdOwnerUserIdAndIdContactUserId(requesterId, contactUserId)).thenReturn(true);
        when(contactNoteRepository.findByIdOwnerUserIdAndIdContactUserId(requesterId, contactUserId))
                .thenReturn(Optional.of(existing));

        var response = userService.upsertContactNote(
                requesterId,
                contactUserId,
                new UpsertContactNoteRequest("   ", null)
        );

        assertThat(response.note()).isNull();
        assertThat(response.birthday()).isNull();
        assertThat(response.updatedAt()).isNull();
        verify(contactNoteRepository).delete(existing);
    }

    @Test
    void listUpcomingBirthdaysReturnsOnlyActiveContactsWithinWindow() {
        UUID requesterId = UUID.randomUUID();
        UUID soonContactId = UUID.randomUUID();
        UUID laterContactId = UUID.randomUUID();
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        ContactNoteEntity soonNote = new ContactNoteEntity();
        soonNote.setId(new ContactNoteId(requesterId, soonContactId));
        soonNote.setBirthday(today.plusDays(3));

        ContactNoteEntity laterNote = new ContactNoteEntity();
        laterNote.setId(new ContactNoteId(requesterId, laterContactId));
        laterNote.setBirthday(today.plusDays(20));

        ContactEntity soonContact = new ContactEntity();
        soonContact.setId(new ContactId(requesterId, soonContactId));
        soonContact.setContactName("Soon");

        ContactEntity laterContact = new ContactEntity();
        laterContact.setId(new ContactId(requesterId, laterContactId));
        laterContact.setContactName("Later");

        UserEntity soonUser = user("Soon User", soonContactId);
        UserEntity laterUser = user("Later User", laterContactId);
        laterUser.setDeletedAt(java.time.Instant.parse("2026-03-19T10:00:00Z"));

        when(contactNoteRepository.findAllByIdOwnerUserIdAndBirthdayIsNotNullOrderByBirthdayAsc(requesterId))
                .thenReturn(List.of(soonNote, laterNote));
        when(contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId))
                .thenReturn(List.of(soonContact, laterContact));
        when(userRepository.findAllById(List.of(soonContactId, laterContactId)))
                .thenReturn(List.of(soonUser, laterUser));

        List<UpcomingBirthdayResponse> birthdays = userService.listUpcomingBirthdays(requesterId, 14);

        assertThat(birthdays).hasSize(1);
        assertThat(birthdays.get(0).contactUserId()).isEqualTo(soonContactId);
        assertThat(birthdays.get(0).contactName()).isEqualTo("Soon");
        assertThat(birthdays.get(0).daysUntil()).isEqualTo(3);
    }

    @Test
    void listUpcomingBirthdaysRejectsInvalidWindow() {
        UUID requesterId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> userService.listUpcomingBirthdays(requesterId, 366),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importContactsRejectsOversizedBatch() {
        UUID requesterId = UUID.randomUUID();
        var contacts = new java.util.ArrayList<ImportedPhoneContactPayload>();
        for (int index = 0; index < 1001; index++) {
            contacts.add(new ImportedPhoneContactPayload("+375290000" + index, "User " + index));
        }

        ResponseStatusException exception = catchThrowableOfType(
                () -> userService.importContacts(requesterId, new ImportContactsRequest(contacts, true)),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importContactsRejectsMissingPayload() {
        UUID requesterId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> userService.importContacts(requesterId, null),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
