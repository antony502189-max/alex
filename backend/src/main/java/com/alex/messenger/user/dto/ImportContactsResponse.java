package com.alex.messenger.user.dto;

import java.util.List;

public record ImportContactsResponse(
        int importedCount,
        int matchedCount,
        boolean persistedMatches,
        List<String> unmatchedPhoneNumbers,
        List<ContactResponse> matchedUsers
) {
}
