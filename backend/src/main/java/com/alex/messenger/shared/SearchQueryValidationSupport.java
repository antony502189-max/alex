package com.alex.messenger.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class SearchQueryValidationSupport {

    public static final int MAX_QUERY_LENGTH = 255;

    private SearchQueryValidationSupport() {
    }

    public static String normalize(String query) {
        if (query == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        String normalizedQuery = query.trim();
        validateLength(normalizedQuery);
        return normalizedQuery;
    }

    public static String normalizeOptional(String query) {
        if (query == null) {
            return "";
        }
        String normalizedQuery = query.trim();
        validateLength(normalizedQuery);
        return normalizedQuery;
    }

    private static void validateLength(String normalizedQuery) {
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "query must be at most " + MAX_QUERY_LENGTH + " characters"
            );
        }
    }
}
