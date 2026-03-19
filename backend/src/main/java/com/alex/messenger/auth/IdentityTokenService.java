package com.alex.messenger.auth;

import com.alex.messenger.auth.dto.TelegramIdentityTokenRequest;
import com.alex.messenger.auth.dto.TelegramIdentityTokenResponse;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class IdentityTokenService {

    private final UserRepository userRepository;
    private final UserSessionService userSessionService;
    private final JwtService jwtService;
    private final AuthProperties authProperties;

    @Transactional(readOnly = true)
    public TelegramIdentityTokenResponse issueToken(
            UUID userId,
            UUID sessionId,
            TelegramIdentityTokenRequest request
    ) {
        userSessionService.requireOwnedSession(sessionId, userId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "User is deleted");
        }
        JwtService.IssuedSignedToken issued = jwtService.issueIdentityToken(
                user,
                sessionId,
                request.appId(),
                request.redirectUri(),
                request.state(),
                authProperties.getIdentity().getTtl()
        );
        return new TelegramIdentityTokenResponse(
                issued.token(),
                issued.expiresAt(),
                request.appId(),
                request.redirectUri(),
                request.state(),
                user.getId(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getUsername()
        );
    }
}
