package com.alex.messenger.media;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoAccessTokenService {

    public static final String QUERY_PARAMETER = "accessToken";
    private static final String TOKEN_TYPE = "PHOTO_ACCESS";

    private final SecretKey signingKey;
    private final Duration ttl;

    public PhotoAccessTokenService(
            @Value("${alex.jwt.secret}") String secret,
            @Value("${alex.storage.profile-photos.access-token-ttl:PT15M}") Duration ttl
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public IssuedPhotoAccessToken issue(String storageProvider, String bucketName, String objectKey) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .claim("tokenType", TOKEN_TYPE)
                .claim("storageProvider", storageProvider)
                .claim("bucketName", bucketName)
                .claim("objectKey", objectKey)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedPhotoAccessToken(token, expiresAt);
    }

    public ValidatedPhotoAccessToken validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TOKEN_TYPE.equals(claims.get("tokenType", String.class))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Photo access token is invalid");
            }
            return new ValidatedPhotoAccessToken(
                    claims.get("storageProvider", String.class),
                    claims.get("bucketName", String.class),
                    claims.get("objectKey", String.class),
                    claims.getExpiration().toInstant()
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Photo access token is invalid", exception);
        }
    }

    public record IssuedPhotoAccessToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record ValidatedPhotoAccessToken(
            String storageProvider,
            String bucketName,
            String objectKey,
            Instant expiresAt
    ) {
    }
}
