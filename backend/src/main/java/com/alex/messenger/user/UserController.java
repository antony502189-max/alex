package com.alex.messenger.user;

import com.alex.messenger.shared.CurrentUser;
import com.alex.messenger.user.dto.AddContactRequest;
import com.alex.messenger.user.dto.BlockUserRequest;
import com.alex.messenger.user.dto.BlockedUserResponse;
import com.alex.messenger.user.dto.ContactResponse;
import com.alex.messenger.user.dto.ImportContactsRequest;
import com.alex.messenger.user.dto.ImportContactsResponse;
import com.alex.messenger.user.dto.ReportUserRequest;
import com.alex.messenger.user.dto.UpdateLanguagePreferencesRequest;
import com.alex.messenger.user.dto.UpdatePrivacyRequest;
import com.alex.messenger.user.dto.UpdateProfileRequest;
import com.alex.messenger.user.dto.UserLanguagePreferencesResponse;
import com.alex.messenger.user.dto.UserProfileResponse;
import com.alex.messenger.user.dto.UserPresenceResponse;
import com.alex.messenger.user.dto.UserReportResponse;
import com.alex.messenger.user.dto.UserSearchResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserPresenceService userPresenceService;

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> search(@RequestParam String query) {
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
        return ResponseEntity.ok(userPresenceService.listPresence(CurrentUser.id(), userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(CurrentUser.id(), request));
    }

    @PatchMapping("/me/privacy")
    public ResponseEntity<UserProfileResponse> updatePrivacy(@Valid @RequestBody UpdatePrivacyRequest request) {
        return ResponseEntity.ok(userService.updatePrivacy(CurrentUser.id(), request));
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

    @PostMapping("/contacts/import")
    public ResponseEntity<ImportContactsResponse> importContacts(@Valid @RequestBody ImportContactsRequest request) {
        return ResponseEntity.ok(userService.importContacts(CurrentUser.id(), request));
    }

    @DeleteMapping("/contacts/{contactUserId}")
    public ResponseEntity<List<ContactResponse>> removeContact(@PathVariable UUID contactUserId) {
        return ResponseEntity.ok(userService.removeContact(CurrentUser.id(), contactUserId));
    }
}
