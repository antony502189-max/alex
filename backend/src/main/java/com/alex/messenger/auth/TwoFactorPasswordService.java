package com.alex.messenger.auth;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TwoFactorPasswordService {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSalt() {
        byte[] salt = new byte[Math.max(16, authProperties.getTwoFactor().getSaltLengthBytes())];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    saltBytes,
                    Math.max(100_000, authProperties.getTwoFactor().getPbkdf2Iterations()),
                    256
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash two-factor password", exception);
        }
    }

    public boolean matches(String password, String salt, String passwordHash) {
        return hashPassword(password, salt).equals(passwordHash);
    }
}
