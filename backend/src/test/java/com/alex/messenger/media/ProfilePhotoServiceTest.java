package com.alex.messenger.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfilePhotoServiceTest {

    @Mock
    private MediaService mediaService;

    @Mock
    private PhotoAccessTokenService photoAccessTokenService;

    private ProfilePhotoService profilePhotoService;

    @BeforeEach
    void setUp() {
        profilePhotoService = new ProfilePhotoService(
                mediaService,
                photoAccessTokenService,
                5L * 1024L * 1024L
        );
    }

    @Test
    void buildPhotoAccessReturnsTokenizedBackendUrl() {
        when(photoAccessTokenService.issue("S3", "media", "users/u1/photo.jpg"))
                .thenReturn(new PhotoAccessTokenService.IssuedPhotoAccessToken(
                        "photo-token",
                        Instant.parse("2026-03-12T12:15:00Z")
                ));

        PhotoAccess access = profilePhotoService.buildPhotoAccess("S3", "media", "users/u1/photo.jpg");

        assertThat(access.photoUrl()).isEqualTo("/api/photos/download?accessToken=photo-token");
        assertThat(access.photoAccessExpiresAt()).isEqualTo(Instant.parse("2026-03-12T12:15:00Z"));
    }

    @Test
    void buildPhotoAccessReturnsEmptyAccessForUnsupportedStorage() {
        PhotoAccess access = profilePhotoService.buildPhotoAccess("LOCAL_FS", null, null);

        assertThat(access.photoUrl()).isNull();
        assertThat(access.photoAccessExpiresAt()).isNull();
    }

    @Test
    void downloadByAccessTokenCapsRedirectTtlToTokenExpiry() {
        when(photoAccessTokenService.validate("photo-token"))
                .thenReturn(new PhotoAccessTokenService.ValidatedPhotoAccessToken(
                        "S3",
                        "media",
                        "users/u1/photo.jpg",
                        Instant.now().plusSeconds(20)
                ));
        when(mediaService.buildDownloadAccess(eq("media"), eq("users/u1/photo.jpg"), any(Duration.class)))
                .thenReturn(new PresignedMediaAccess(
                        "https://cdn.example/users/u1/photo.jpg",
                        Instant.parse("2026-03-12T12:00:20Z")
                ));

        PhotoDownloadResult result = profilePhotoService.downloadByAccessToken("photo-token");

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(mediaService).buildDownloadAccess(eq("media"), eq("users/u1/photo.jpg"), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isGreaterThan(Duration.ZERO);
        assertThat(durationCaptor.getValue()).isLessThanOrEqualTo(Duration.ofSeconds(20));
        assertThat(result.redirectUrl()).isEqualTo("https://cdn.example/users/u1/photo.jpg");
    }

    @Test
    void downloadByAccessTokenRejectsUnsupportedStorage() {
        when(photoAccessTokenService.validate("photo-token"))
                .thenReturn(new PhotoAccessTokenService.ValidatedPhotoAccessToken(
                        "LOCAL_FS",
                        null,
                        null,
                        Instant.now().plusSeconds(20)
                ));

        ResponseStatusException exception = catchThrowableOfType(
                () -> profilePhotoService.downloadByAccessToken("photo-token"),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
