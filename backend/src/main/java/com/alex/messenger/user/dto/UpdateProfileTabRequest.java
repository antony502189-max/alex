package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileTabRequest(
        @NotBlank
        @Size(max = 16)
        @Pattern(regexp = "(?i)MEDIA|FILES|LINKS|AUDIO|GIFTS|CHANNELS|GROUPS|STORIES")
        String defaultProfileTab
) {
}
