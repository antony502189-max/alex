package com.alex.messenger.attachment;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentAccessTokenService {

    public static final String QUERY_PARAMETER = "accessToken";
    private static final String TOKEN_TYPE = "ATTACHMENT_ACCESS";

    private final SecretKey signingKey;
    private final Duration ttl;

    public AttachmentAccessTokenService(
            @Value("${alex.jwt.secret}") String secret,
            @Value("${alex.storage.attachments.access-token-ttl:PT15M}") Duration ttl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public IssuedAttachmentAccessToken issue(UUID userId, UUID attachmentId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("attachmentId", attachmentId.toString())
                .claim("tokenType", TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedAttachmentAccessToken(token, expiresAt);
    }

    public ValidatedAttachmentAccessToken validate(String token, UUID attachmentId) {
        try {
            Claims claims = parseClaims(token);
            if (!TOKEN_TYPE.equals(claims.get("tokenType", String.class))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Attachment access token is invalid");
            }
            UUID tokenAttachmentId = UUID.fromString(claims.get("attachmentId", String.class));
            if (!attachmentId.equals(tokenAttachmentId)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Attachment access token does not match attachment");
            }
            return new ValidatedAttachmentAccessToken(
                    UUID.fromString(claims.getSubject()),
                    tokenAttachmentId,
                    claims.getExpiration().toInstant()
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Attachment access token is invalid", exception);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record IssuedAttachmentAccessToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record ValidatedAttachmentAccessToken(
            UUID userId,
            UUID attachmentId,
            Instant expiresAt
    ) {
    }
}
