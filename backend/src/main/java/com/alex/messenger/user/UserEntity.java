package com.alex.messenger.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number", nullable = false, unique = true, length = 32)
    private String phoneNumber;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "username", unique = true, length = 64)
    private String username;

    @Column(name = "about", length = 255)
    private String about;

    @Column(name = "is_bot", nullable = false)
    private boolean bot;

    @Column(name = "bot_description", length = 255)
    private String botDescription;

    @Column(name = "bot_supports_inline", nullable = false)
    private boolean botSupportsInline;

    @Column(name = "bot_web_app_url", length = 512)
    private String botWebAppUrl;

    @Column(name = "phone_privacy", nullable = false, length = 16)
    private String phonePrivacy;

    @Column(name = "last_seen_privacy", nullable = false, length = 16)
    private String lastSeenPrivacy;

    @Column(name = "story_privacy", nullable = false, length = 16)
    private String storyPrivacy;

    @Column(name = "preferred_language", length = 16)
    private String preferredLanguage;

    @Column(name = "translation_target_language", length = 16)
    private String translationTargetLanguage;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "photo_storage_provider", length = 32)
    private String photoStorageProvider;

    @Column(name = "photo_bucket_name", length = 255)
    private String photoBucketName;

    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    @Column(name = "photo_content_type", length = 255)
    private String photoContentType;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @Column(name = "two_factor_password_hash", length = 255)
    private String twoFactorPasswordHash;

    @Column(name = "two_factor_password_salt", length = 255)
    private String twoFactorPasswordSalt;

    @Column(name = "two_factor_hint", length = 120)
    private String twoFactorHint;

    @Column(name = "two_factor_enabled_at")
    private Instant twoFactorEnabledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (phonePrivacy == null) {
            phonePrivacy = "EVERYBODY";
        }
        if (lastSeenPrivacy == null) {
            lastSeenPrivacy = "EVERYBODY";
        }
        if (storyPrivacy == null) {
            storyPrivacy = "EVERYBODY";
        }
    }
}
