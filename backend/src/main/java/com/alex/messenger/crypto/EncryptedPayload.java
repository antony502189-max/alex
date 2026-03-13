package com.alex.messenger.crypto;

public record EncryptedPayload(
        String ciphertext,
        String nonce,
        int keyVersion
) {
}
