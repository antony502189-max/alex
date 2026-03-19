package com.alex.messenger.attachment.dto;

public record TrimAttachmentRequest(
        Long startMs,
        Long endMs
) {
}
