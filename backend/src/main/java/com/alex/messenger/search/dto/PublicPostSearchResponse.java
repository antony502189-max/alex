package com.alex.messenger.search.dto;

import java.util.List;

public record PublicPostSearchResponse(
        String query,
        List<PublicPostSearchResult> posts
) {
}
