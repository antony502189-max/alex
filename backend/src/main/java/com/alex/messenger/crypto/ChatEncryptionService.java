package com.alex.messenger.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_METADATA_ALGORITHM = "AES256_GCM";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int NONCE_LENGTH = 12;
    private static final int KEY_SIZE = 256;

    private final EncryptionKeyRepository encryptionKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public EncryptedPayload encrypt(UUID chatId, String plaintext) {
        byte[] plainBytes = plaintext == null ? new byte[0] : plaintext.getBytes(StandardCharsets.UTF_8);
        EncryptedBinaryPayload encryptedPayload = encryptBytes(chatId, plainBytes);
        return new EncryptedPayload(
                Base64.getEncoder().encodeToString(encryptedPayload.ciphertext()),
                encryptedPayload.nonce(),
                encryptedPayload.keyVersion()
        );
    }

    @Transactional
    public EncryptedBinaryPayload encryptBytes(UUID chatId, byte[] plaintext) {
        EncryptionKeyEntity encryptionKey = getOrCreateActiveKey(chatId);
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, toSecretKey(encryptionKey), new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            cipher.updateAAD(buildAad(chatId, encryptionKey.getKeyVersion()));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedBinaryPayload(
                    ciphertext,
                    Base64.getEncoder().encodeToString(nonce),
                    encryptionKey.getKeyVersion()
            );
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encrypt message", exception);
        }
    }

    @Transactional(readOnly = true)
    public String decrypt(UUID chatId, String ciphertext, String nonce, int keyVersion) {
        return new String(
                decryptBytes(chatId, Base64.getDecoder().decode(ciphertext), nonce, keyVersion),
                StandardCharsets.UTF_8
        );
    }

    @Transactional(readOnly = true)
    public byte[] decryptBytes(UUID chatId, byte[] ciphertext, String nonce, int keyVersion) {
        EncryptionKeyEntity encryptionKey = encryptionKeyRepository.findByChatIdAndKeyVersion(chatId, keyVersion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing chat key version"));

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    toSecretKey(encryptionKey),
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(nonce))
            );
            cipher.updateAAD(buildAad(chatId, keyVersion));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to decrypt message", exception);
        }
    }

    private byte[] buildAad(UUID chatId, int keyVersion) {
        return (chatId + ":" + keyVersion).getBytes(StandardCharsets.UTF_8);
    }

    private SecretKey toSecretKey(EncryptionKeyEntity encryptionKey) {
        return new SecretKeySpec(Base64.getDecoder().decode(encryptionKey.getKeyMaterial()), KEY_ALGORITHM);
    }

    private EncryptionKeyEntity getOrCreateActiveKey(UUID chatId) {
        return encryptionKeyRepository.findFirstByChatIdAndActiveTrue(chatId)
                .orElseGet(() -> createKey(chatId));
    }

    private EncryptionKeyEntity createKey(UUID chatId) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGenerator.init(KEY_SIZE);
            SecretKey key = keyGenerator.generateKey();

            int nextVersion = encryptionKeyRepository.findTopByChatIdOrderByKeyVersionDesc(chatId)
                    .map(existingKey -> existingKey.getKeyVersion() + 1)
                    .orElse(1);

            EncryptionKeyEntity entity = new EncryptionKeyEntity();
            entity.setChatId(chatId);
            entity.setAlgorithm(KEY_METADATA_ALGORITHM);
            entity.setKeyVersion(nextVersion);
            entity.setKeyMaterial(Base64.getEncoder().encodeToString(key.getEncoded()));
            entity.setActive(true);
            entity.setCreatedAt(Instant.now());
            return encryptionKeyRepository.save(entity);
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate chat key", exception);
        } catch (DataIntegrityViolationException duplicateKeyRace) {
            return encryptionKeyRepository.findFirstByChatIdAndActiveTrue(chatId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load chat key"));
        }
    }
}
