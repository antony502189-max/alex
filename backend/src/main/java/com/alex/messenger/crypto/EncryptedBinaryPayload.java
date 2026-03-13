package com.alex.messenger.crypto;

public record EncryptedBinaryPayload(
        byte[] ciphertext,
        String nonce,
        int keyVersion
) {
}
