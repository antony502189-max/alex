package com.alex.messenger.call.dto;

public record CallIceServerResponse(
        String url,
        String username,
        String credential
) {
}
