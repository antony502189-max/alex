package com.alex.messenger.config;

import java.util.Set;

final class StompUserQueueDestinationPolicy {

    private static final Set<String> ALLOWED_DESTINATIONS = Set.of(
            "/user/queue/messages",
            "/user/queue/story-events",
            "/user/queue/calls",
            "/user/queue/secret-chats"
    );

    private StompUserQueueDestinationPolicy() {
    }

    static boolean isAllowed(String destination) {
        return destination != null && ALLOWED_DESTINATIONS.contains(destination);
    }
}
