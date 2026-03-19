package com.alex.messenger.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UpdatePrivacyExceptionsRequest(
        List<@NotNull UUID> phoneAllowedUserIds,
        List<@NotNull UUID> phoneDisallowedUserIds,
        List<@NotNull UUID> lastSeenAllowedUserIds,
        List<@NotNull UUID> lastSeenDisallowedUserIds,
        List<@NotNull UUID> storyAllowedUserIds,
        List<@NotNull UUID> storyDisallowedUserIds
) {

    @JsonIgnore
    @AssertTrue(message = "Phone privacy exception lists overlap")
    public boolean hasNoPhoneOverlap() {
        return hasNoOverlap(phoneAllowedUserIds, phoneDisallowedUserIds);
    }

    @JsonIgnore
    @AssertTrue(message = "Last seen privacy exception lists overlap")
    public boolean hasNoLastSeenOverlap() {
        return hasNoOverlap(lastSeenAllowedUserIds, lastSeenDisallowedUserIds);
    }

    @JsonIgnore
    @AssertTrue(message = "Story privacy exception lists overlap")
    public boolean hasNoStoryOverlap() {
        return hasNoOverlap(storyAllowedUserIds, storyDisallowedUserIds);
    }

    private static boolean hasNoOverlap(List<UUID> allowed, List<UUID> denied) {
        if (allowed == null || denied == null) {
            return true;
        }
        Set<UUID> allowedIds = allowed.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return denied.stream()
                .filter(Objects::nonNull)
                .noneMatch(allowedIds::contains);
    }
}
