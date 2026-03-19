package com.alex.messenger.auth;

import com.alex.messenger.user.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtService(
            @Value("${alex.jwt.secret}") String secret,
            @Value("${alex.jwt.ttl}") Duration ttl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public IssuedAccessToken issueAccessToken(UserEntity user, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("sessionId", sessionId.toString())
                .claim("tokenType", "ACCESS")
                .claim("phoneNumber", user.getPhoneNumber())
                .claim("displayName", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    public IssuedSignedToken issueIdentityToken(
            UserEntity user,
            UUID sessionId,
            String appId,
            String redirectUri,
            String state,
            Duration identityTtl
    ) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(identityTtl);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("sessionId", sessionId.toString())
                .claim("tokenType", "IDENTITY")
                .claim("appId", appId)
                .claim("redirectUri", redirectUri)
                .claim("state", state)
                .claim("phoneNumber", user.getPhoneNumber())
                .claim("displayName", user.getDisplayName())
                .claim("username", user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedSignedToken(token, expiresAt);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UUID extractSessionId(String token) {
        return UUID.fromString(parseClaims(token).get("sessionId", String.class));
    }

    public boolean isValid(String token) {
        parseClaims(token);
        return true;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record IssuedAccessToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record IssuedSignedToken(
            String token,
            Instant expiresAt
    ) {
    }
}
