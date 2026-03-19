package com.alex.messenger.call.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCallCommentRequest(
        @NotBlank @Size(max = 500) String content
) {
}
