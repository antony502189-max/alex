package com.alex.messenger.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.user.dto.ReplaceCloseFriendsRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserPrivacyServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserPrivacyExceptionRepository userPrivacyExceptionRepository;

    @Mock
    private UserCloseFriendRepository userCloseFriendRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    private UserPrivacyService userPrivacyService;

    @BeforeEach
    void setUp() {
        userPrivacyService = new UserPrivacyService(
                userRepository,
                contactRepository,
                userPrivacyExceptionRepository,
                userCloseFriendRepository,
                profilePhotoService
        );
    }

    @Test
    void canViewPhoneAllowsExplicitExceptionEvenWhenBasePrivacyDenies() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        owner.setPhonePrivacy("NOBODY");

        UserPrivacyExceptionEntity exception = new UserPrivacyExceptionEntity();
        exception.setOwnerUserId(ownerUserId);
        exception.setTargetUserId(requesterId);
        exception.setPrivacyType("PHONE");
        exception.setAccessMode("ALLOW");

        when(userPrivacyExceptionRepository.findAllByOwnerUserIdAndTargetUserId(ownerUserId, requesterId))
                .thenReturn(List.of(exception));

        assertThat(userPrivacyService.canViewPhone(requesterId, owner)).isTrue();
    }

    @Test
    void replaceCloseFriendsRejectsDeletedUsers() {
        UUID ownerUserId = UUID.randomUUID();
        UUID deletedUserId = UUID.randomUUID();

        UserEntity deletedUser = new UserEntity();
        deletedUser.setId(deletedUserId);
        deletedUser.setDeletedAt(Instant.parse("2026-03-19T10:00:00Z"));

        when(userRepository.findAllById(any())).thenReturn(List.of(deletedUser));

        ResponseStatusException exception = catchThrowableOfType(
                () -> userPrivacyService.replaceCloseFriends(
                        ownerUserId,
                        new ReplaceCloseFriendsRequest(List.of(deletedUserId))
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
