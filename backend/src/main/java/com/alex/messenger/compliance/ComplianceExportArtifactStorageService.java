package com.alex.messenger.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceExportArtifactStorageService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final ObjectMapper objectMapper;
    private final Path artifactDirectory;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ComplianceExportArtifactStorageService(
            ObjectMapper objectMapper,
            @Value("${alex.storage.root}") String storageRoot,
            ComplianceProperties complianceProperties
    ) {
        this.objectMapper = objectMapper;
        this.artifactDirectory = Path.of(storageRoot).toAbsolutePath().resolve("compliance-exports");
        this.encryptionKey = new SecretKeySpec(
                deriveEncryptionKey(complianceProperties.getExport().getEncryptionSecret()),
                "AES"
        );
    }

    public StoredComplianceArtifact writeArtifact(ComplianceCaseExportArtifactPayload payload) {
        ensureArtifactDirectoryExists();
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Path storagePath = artifactDirectory.resolve(payload.artifactId() + ".bin");
        try {
            byte[] serializedPayload = objectMapper.writeValueAsBytes(payload);
            byte[] encryptedPayload = encrypt(serializedPayload, iv);
            Files.write(storagePath, encryptedPayload);
            return new StoredComplianceArtifact(
                    storagePath.toString(),
                    Base64.getEncoder().encodeToString(iv),
                    encryptedPayload.length
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to persist compliance export artifact",
                    exception
            );
        }
    }

    public ComplianceCaseExportArtifactPayload readArtifact(ComplianceCaseExportArtifactEntity artifact) {
        try {
            byte[] encryptedPayload = Files.readAllBytes(Path.of(artifact.getStoragePath()));
            byte[] decryptedPayload = decrypt(
                    encryptedPayload,
                    Base64.getDecoder().decode(artifact.getEncryptionIv())
            );
            return objectMapper.readValue(decryptedPayload, ComplianceCaseExportArtifactPayload.class);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read compliance export artifact",
                    exception
            );
        }
    }

    public void deleteArtifact(ComplianceCaseExportArtifactEntity artifact) {
        if (artifact == null || artifact.getStoragePath() == null || artifact.getStoragePath().isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(artifact.getStoragePath()));
        } catch (IOException ignored) {
            // Non-fatal during cleanup; metadata remains the source of truth.
        }
    }

    private void ensureArtifactDirectoryExists() {
        try {
            Files.createDirectories(artifactDirectory);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to initialize compliance export storage directory",
                    exception
            );
        }
    }

    private byte[] encrypt(byte[] serializedPayload, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(serializedPayload);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to encrypt compliance export artifact",
                    exception
            );
        }
    }

    private byte[] decrypt(byte[] encryptedPayload, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(encryptedPayload);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to decrypt compliance export artifact",
                    exception
            );
        }
    }

    private byte[] deriveEncryptionKey(String encryptionSecret) {
        String normalizedSecret = encryptionSecret != null ? encryptionSecret.trim() : "";
        if (normalizedSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compliance export encryption secret is blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizedSecret.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 32);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to initialize compliance export encryption key",
                    exception
            );
        }
    }

    public record StoredComplianceArtifact(
            String storagePath,
            String encryptionIv,
            long sizeBytes
    ) {
    }
}
