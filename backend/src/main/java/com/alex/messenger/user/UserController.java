package com.alex.messenger.user;

import com.alex.messenger.shared.CurrentUser;
import com.alex.messenger.shared.SearchQueryValidationSupport;
import com.alex.messenger.user.dto.AddContactRequest;
import com.alex.messenger.user.dto.BlockUserRequest;
import com.alex.messenger.user.dto.BlockedUserResponse;
import com.alex.messenger.user.dto.CloseFriendResponse;
import com.alex.messenger.user.dto.ContactResponse;
import com.alex.messenger.user.dto.ContactNoteResponse;
import com.alex.messenger.user.dto.ImportContactsRequest;
import com.alex.messenger.user.dto.ImportContactsResponse;
import com.alex.messenger.user.dto.ReplaceCloseFriendsRequest;
import com.alex.messenger.user.dto.ReportUserRequest;
import com.alex.messenger.user.dto.UpcomingBirthdayResponse;
import com.alex.messenger.user.dto.UpdatePrivacyExceptionsRequest;
import com.alex.messenger.user.dto.UpdateLanguagePreferencesRequest;
import com.alex.messenger.user.dto.UpdatePrivacyRequest;
import com.alex.messenger.user.dto.UpdateProfileRequest;
import com.alex.messenger.user.dto.UpdateProfileTabRequest;
import com.alex.messenger.user.dto.UpsertContactNoteRequest;
import com.alex.messenger.user.dto.UpsertProfileAudioRequest;
import com.alex.messenger.user.dto.UserLanguagePreferencesResponse;
import com.alex.messenger.user.dto.UserProfileAudioResponse;
import com.alex.messenger.user.dto.UserProfilePreferencesResponse;
import com.alex.messenger.user.dto.UserProfileResponse;
import com.alex.messenger.user.dto.UserProfileRatingResponse;
import com.alex.messenger.user.dto.UserPresenceResponse;
import com.alex.messenger.user.dto.UserPrivacyExceptionsResponse;
import com.alex.messenger.user.dto.UserReportResponse;
import com.alex.messenger.user.dto.UserSearchResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileMetadataService userProfileMetadataService;
    private final UserPresenceService userPresenceService;
    private final UserPrivacyService userPrivacyService;

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> search(@RequestParam String query) {
        SearchQueryValidationSupport.normalize(query);
        return ResponseEntity.ok(userService.search(CurrentUser.id(), query));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        return ResponseEntity.ok(userService.getProfile(CurrentUser.id()));
    }

    @GetMapping("/me/language-preferences")
    public ResponseEntity<UserLanguagePreferencesResponse> languagePreferences() {
        return ResponseEntity.ok(userService.getLanguagePreferences(CurrentUser.id()));
    }

    @GetMapping("/presence")
    public ResponseEntity<List<UserPresenceResponse>> presence(@RequestParam List<UUID> userId) {
        return ResponseEntity.ok(userPresenceService.listPresence(CurrentUser.id(), requirePresenceUserIds(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(CurrentUser.id(), request));
    }

    @PatchMapping("/me/profile-tab")
    public ResponseEntity<UserProfilePreferencesResponse> updateProfileTab(
            @Valid @RequestBody(required = false) UpdateProfileTabRequest request
    ) {
        return ResponseEntity.ok(userProfileMetadataService.updateProfileTab(CurrentUser.id(), request));
    }

    @GetMapping("/me/profile-rating")
    public ResponseEntity<UserProfileRatingResponse> profileRating() {
        return ResponseEntity.ok(userProfileMetadataService.getProfileRating(CurrentUser.id()));
    }

    @GetMapping("/me/profile-audio")
    public ResponseEntity<UserProfileAudioResponse> profileAudio() {
        return ResponseEntity.ok(userProfileMetadataService.getProfileAudio(CurrentUser.id()));
    }

    @PutMapping("/me/profile-audio")
    public ResponseEntity<UserProfileAudioResponse> upsertProfileAudio(
            @Valid @RequestBody(required = false) UpsertProfileAudioRequest request
    ) {
        return ResponseEntity.ok(userProfileMetadataService.upsertProfileAudio(CurrentUser.id(), request));
    }

    @PatchMapping("/me/privacy")
    public ResponseEntity<UserProfileResponse> updatePrivacy(@Valid @RequestBody UpdatePrivacyRequest request) {
        return ResponseEntity.ok(userService.updatePrivacy(CurrentUser.id(), request));
    }

    @PatchMapping("/me/privacy/exceptions")
    public ResponseEntity<UserPrivacyExceptionsResponse> updatePrivacyExceptions(
            @Valid @RequestBody(required = false) UpdatePrivacyExceptionsRequest request
    ) {
        return ResponseEntity.ok(userPrivacyService.updatePrivacyExceptions(CurrentUser.id(), request));
    }

    @GetMapping("/me/privacy/exceptions")
    public ResponseEntity<UserPrivacyExceptionsResponse> privacyExceptions() {
        return ResponseEntity.ok(userPrivacyService.getPrivacyExceptions(CurrentUser.id()));
    }

    @PatchMapping("/me/language-preferences")
    public ResponseEntity<UserLanguagePreferencesResponse> updateLanguagePreferences(
            @Valid @RequestBody UpdateLanguagePreferencesRequest request
    ) {
        return ResponseEntity.ok(userService.updateLanguagePreferences(CurrentUser.id(), request));
    }

    @PostMapping("/me/photo")
    public ResponseEntity<UserProfileResponse> uploadProfilePhoto(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateProfilePhoto(CurrentUser.id(), file));
    }

    @DeleteMapping("/me/photo")
    public ResponseEntity<UserProfileResponse> removeProfilePhoto() {
        return ResponseEntity.ok(userService.removeProfilePhoto(CurrentUser.id()));
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactResponse>> contacts() {
        return ResponseEntity.ok(userService.listContacts(CurrentUser.id()));
    }

    @GetMapping("/contacts/birthdays")
    public ResponseEntity<List<UpcomingBirthdayResponse>> upcomingBirthdays(
            @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(userService.listUpcomingBirthdays(CurrentUser.id(), requireDays(days)));
    }

    @GetMapping("/me/close-friends")
    public ResponseEntity<List<CloseFriendResponse>> closeFriends() {
        return ResponseEntity.ok(userPrivacyService.listCloseFriends(CurrentUser.id()));
    }

    @PutMapping("/me/close-friends")
    public ResponseEntity<List<CloseFriendResponse>> replaceCloseFriends(
            @Valid @RequestBody(required = false) ReplaceCloseFriendsRequest request
    ) {
        return ResponseEntity.ok(userPrivacyService.replaceCloseFriends(CurrentUser.id(), request));
    }

    @GetMapping("/block")
    public ResponseEntity<List<BlockedUserResponse>> blocks() {
        return ResponseEntity.ok(userService.listBlockedUsers(CurrentUser.id()));
    }

    @PostMapping("/block")
    public ResponseEntity<List<BlockedUserResponse>> block(@Valid @RequestBody BlockUserRequest request) {
        return ResponseEntity.ok(userService.blockUser(CurrentUser.id(), request));
    }

    @DeleteMapping("/block/{blockedUserId}")
    public ResponseEntity<List<BlockedUserResponse>> unblock(@PathVariable UUID blockedUserId) {
        return ResponseEntity.ok(userService.unblockUser(CurrentUser.id(), blockedUserId));
    }

    @PostMapping("/report")
    public ResponseEntity<UserReportResponse> report(@Valid @RequestBody ReportUserRequest request) {
        return ResponseEntity.ok(userService.reportUser(CurrentUser.id(), request));
    }

    @PostMapping("/contacts")
    public ResponseEntity<List<ContactResponse>> addContact(@Valid @RequestBody AddContactRequest request) {
        return ResponseEntity.ok(userService.addContact(CurrentUser.id(), request));
    }

    @GetMapping("/contacts/{contactUserId}/note")
    public ResponseEntity<ContactNoteResponse> contactNote(@PathVariable UUID contactUserId) {
        return ResponseEntity.ok(userService.getContactNote(CurrentUser.id(), contactUserId));
    }

    @PutMapping("/contacts/{contactUserId}/note")
    public ResponseEntity<ContactNoteResponse> upsertContactNote(
            @PathVariable UUID contactUserId,
            @Valid @RequestBody(required = false) UpsertContactNoteRequest request
    ) {
        return ResponseEntity.ok(userService.upsertContactNote(CurrentUser.id(), contactUserId, request));
    }

    @PostMapping("/contacts/import")
    public ResponseEntity<ImportContactsResponse> importContacts(@Valid @RequestBody ImportContactsRequest request) {
        return ResponseEntity.ok(userService.importContacts(CurrentUser.id(), request));
    }

    @DeleteMapping("/contacts/{contactUserId}")
    public ResponseEntity<List<ContactResponse>> removeContact(@PathVariable UUID contactUserId) {
        return ResponseEntity.ok(userService.removeContact(CurrentUser.id(), contactUserId));
    }

    private Integer requireDays(Integer days) {
        if (days != null && (days < 1 || days > 365)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 365");
        }
        return days;
    }

    private List<UUID> requirePresenceUserIds(List<UUID> userIds) {
        if (userIds != null && userIds.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presence lookup supports up to 100 users");
        }
        return userIds;
    }
}
