package com.alex.messenger.attachment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateAttachmentUploadSessionRequest(
        @NotNull UUID chatId,
        @Size(max = 255) String originalFileName,
        @Size(max = 255) String contentType,
        @Size(max = 16)
        @Pattern(regexp = "(^\\s*$)|(?i)^\\s*(FILE|VOICE|IMAGE|VIDEO|AUDIO|GIF|VIDEO_NOTE)\\s*$")
        String kind,
        @Positive long totalSizeBytes,
        @Positive Long durationMs,
        @Positive Integer width,
        @Positive Integer height,
        Boolean hdPhoto,
        @Size(max = 96) List<@NotNull @Min(0) @Max(100) Integer> waveform,
        UUID albumId,
        @PositiveOrZero Integer albumItemIndex
) {
    @JsonIgnore
    @AssertTrue(message = "Album item index requires album id")
    public boolean hasAlbumForItemIndex() {
        return albumItemIndex == null || albumId != null;
    }
}
