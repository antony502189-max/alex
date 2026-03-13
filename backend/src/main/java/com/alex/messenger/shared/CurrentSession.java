package com.alex.messenger.shared;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

public final class CurrentSession {

    private CurrentSession() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof String sessionId)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Session not found");
        }
        return UUID.fromString(sessionId);
    }
}
