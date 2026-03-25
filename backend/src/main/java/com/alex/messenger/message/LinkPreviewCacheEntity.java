package com.alex.messenger.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "link_preview_cache")
public class LinkPreviewCacheEntity {

    @Id
    @Column(name = "normalized_url", nullable = false, length = 1000)
    private String normalizedUrl;

    @Column(name = "canonical_url", length = 1000)
    private String canonicalUrl;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "site_name", length = 255)
    private String siteName;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
