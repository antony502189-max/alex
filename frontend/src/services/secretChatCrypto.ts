import * as FileSystem from "expo-file-system/legacy";
import * as SecureStore from "expo-secure-store";
import nacl from "tweetnacl";
import * as naclUtil from "tweetnacl-util";
import type {
  SecretChatAttachment,
  SecretChatMessage,
  SecretChatPayload,
  SecretChatSummary
} from "../types";

type SecretChatKeyPair = {
  publicKey: string;
  privateKey: string;
};

type EncryptBinaryResult = {
  ciphertextBase64: string;
  nonce: string;
};

const KEY_PREFIX = "alex-secret-chat";
const REGISTRY_STORAGE_KEY = `${KEY_PREFIX}:registry`;
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function privateKeyStorageKey(secretChatId: string) {
  return `${KEY_PREFIX}:${secretChatId}:private`;
}

async function getTrackedSecretChatIds() {
  const value = await SecureStore.getItemAsync(REGISTRY_STORAGE_KEY);
  if (!value) {
    return [] as string[];
  }

  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((item): item is string => typeof item === "string" && item.length > 0);
  } catch {
    return [];
  }
}

async function writeTrackedSecretChatIds(secretChatIds: string[]) {
  const nextSecretChatIds = [...new Set(secretChatIds)].sort();
  if (nextSecretChatIds.length === 0) {
    await SecureStore.deleteItemAsync(REGISTRY_STORAGE_KEY);
    return;
  }

  await SecureStore.setItemAsync(REGISTRY_STORAGE_KEY, JSON.stringify(nextSecretChatIds));
}

async function trackSecretChatId(secretChatId: string) {
  const currentIds = await getTrackedSecretChatIds();
  if (currentIds.includes(secretChatId)) {
    return;
  }

  await writeTrackedSecretChatIds([...currentIds, secretChatId]);
}

function resolvePeerPublicKey(summary: SecretChatSummary) {
  return summary.direction === "OUTGOING"
    ? summary.recipientPublicKey
    : summary.initiatorPublicKey;
}

function toHex(bytes: Uint8Array) {
  return [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function normalizeSecretAttachment(value: unknown): SecretChatAttachment | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const attachment = value as Record<string, unknown>;
  const attachmentId = typeof attachment.attachmentId === "string" ? attachment.attachmentId : null;
  const kind = typeof attachment.kind === "string" ? attachment.kind : null;
  const originalFileName = typeof attachment.originalFileName === "string" ? attachment.originalFileName : null;
  const contentType = typeof attachment.contentType === "string" ? attachment.contentType : null;
  const fileSizeBytes = typeof attachment.fileSizeBytes === "number" ? attachment.fileSizeBytes : null;
  const fileNonce = typeof attachment.fileNonce === "string" ? attachment.fileNonce : null;
  const durationMs =
    typeof attachment.durationMs === "number" && Number.isFinite(attachment.durationMs)
      ? Math.max(0, Math.round(attachment.durationMs))
      : null;

  if (!attachmentId || !kind || !originalFileName || !contentType || fileSizeBytes == null || !fileNonce) {
    return null;
  }

  if (!["FILE", "IMAGE", "VOICE", "VIDEO"].includes(kind)) {
    return null;
  }

  return {
    attachmentId,
    kind: kind as SecretChatAttachment["kind"],
    originalFileName,
    contentType,
    fileSizeBytes,
    fileNonce,
    durationMs
  };
}

function normalizePayload(value: unknown): SecretChatPayload {
  if (!value || typeof value !== "object") {
    return {
      version: 1,
      text: typeof value === "string" ? value : null,
      attachments: []
    };
  }

  const payload = value as Record<string, unknown>;
  return {
    version: 1,
    text: typeof payload.text === "string" ? payload.text : null,
    attachments: Array.isArray(payload.attachments)
      ? payload.attachments
          .map(normalizeSecretAttachment)
          .filter((attachment): attachment is SecretChatAttachment => !!attachment)
      : []
  };
}

async function deriveSharedKey(summary: SecretChatSummary, privateKey?: string) {
  const peerPublicKey = resolvePeerPublicKey(summary);
  if (!peerPublicKey) {
    throw new Error("Peer public key is not available yet");
  }

  const ownPrivateKey = naclUtil.decodeBase64(privateKey ?? await secretChatCrypto.getPrivateKey(summary.secretChatId));
  const peerPublicKeyBytes = naclUtil.decodeBase64(peerPublicKey);
  return nacl.box.before(peerPublicKeyBytes, ownPrivateKey);
}

async function encryptBinary(summary: SecretChatSummary, bytes: Uint8Array): Promise<EncryptBinaryResult> {
  const sharedKey = await deriveSharedKey(summary);
  const nonce = nacl.randomBytes(nacl.secretbox.nonceLength);
  const ciphertext = nacl.secretbox(bytes, nonce, sharedKey);
  return {
    ciphertextBase64: naclUtil.encodeBase64(ciphertext),
    nonce: naclUtil.encodeBase64(nonce)
  };
}

async function decryptBinary(summary: SecretChatSummary, ciphertextBase64: string, nonce: string) {
  const sharedKey = await deriveSharedKey(summary);
  const plaintext = nacl.secretbox.open(
    naclUtil.decodeBase64(ciphertextBase64),
    naclUtil.decodeBase64(nonce),
    sharedKey
  );

  if (!plaintext) {
    throw new Error("Unable to decrypt secret-chat payload");
  }

  return plaintext;
}

export const secretChatCrypto = {
  generateKeyPair(): SecretChatKeyPair {
    const keyPair = nacl.box.keyPair();
    return {
      publicKey: naclUtil.encodeBase64(keyPair.publicKey),
      privateKey: naclUtil.encodeBase64(keyPair.secretKey)
    };
  },

  async storePrivateKey(secretChatId: string, privateKey: string) {
    await SecureStore.setItemAsync(privateKeyStorageKey(secretChatId), privateKey);
    await trackSecretChatId(secretChatId);
  },

  async hasPrivateKey(secretChatId: string) {
    const value = await SecureStore.getItemAsync(privateKeyStorageKey(secretChatId));
    if (value) {
      await trackSecretChatId(secretChatId);
    }
    return !!value;
  },

  async clearPrivateKey(secretChatId: string) {
    await SecureStore.deleteItemAsync(privateKeyStorageKey(secretChatId));
    const currentIds = await getTrackedSecretChatIds();
    await writeTrackedSecretChatIds(currentIds.filter((currentId) => currentId !== secretChatId));
  },

  async clearAllPrivateKeys() {
    const currentIds = await getTrackedSecretChatIds();
    await Promise.allSettled(
      currentIds.map((secretChatId) => SecureStore.deleteItemAsync(privateKeyStorageKey(secretChatId)))
    );
    await SecureStore.deleteItemAsync(REGISTRY_STORAGE_KEY);
  },

  async prunePrivateKeys(retainedSecretChatIds: string[]) {
    const retainedIds = new Set(retainedSecretChatIds);
    const currentIds = await getTrackedSecretChatIds();
    const removedIds = currentIds.filter((secretChatId) => !retainedIds.has(secretChatId));

    await Promise.allSettled(
      removedIds.map((secretChatId) => SecureStore.deleteItemAsync(privateKeyStorageKey(secretChatId)))
    );
    await writeTrackedSecretChatIds(currentIds.filter((secretChatId) => retainedIds.has(secretChatId)));
  },

  async getPrivateKey(secretChatId: string) {
    const value = await SecureStore.getItemAsync(privateKeyStorageKey(secretChatId));
    if (!value) {
      throw new Error("Missing local secret-chat key for this device");
    }
    await trackSecretChatId(secretChatId);
    return value;
  },

  deriveFingerprint(summary: SecretChatSummary, privateKey: string) {
    const peerPublicKey = resolvePeerPublicKey(summary);
    if (!peerPublicKey) {
      throw new Error("Peer public key is not available yet");
    }
    if (!privateKey.trim()) {
      throw new Error("Missing private key material for this secret chat");
    }

    const ownPrivateKey = naclUtil.decodeBase64(privateKey);
    const peerPublicKeyBytes = naclUtil.decodeBase64(peerPublicKey);
    const sharedKey = nacl.box.before(peerPublicKeyBytes, ownPrivateKey);
    return toHex(nacl.hash(sharedKey).slice(0, 8));
  },

  async deriveStoredFingerprint(summary: SecretChatSummary) {
    return this.deriveFingerprint(summary, await this.getPrivateKey(summary.secretChatId));
  },

  async encryptPayload(summary: SecretChatSummary, payload: SecretChatPayload) {
    return encryptBinary(
      summary,
      encoder.encode(
        JSON.stringify({
          version: 1,
          text: payload.text ?? null,
          attachments: payload.attachments ?? []
        })
      )
    );
  },

  async encryptText(summary: SecretChatSummary, plaintext: string) {
    return this.encryptPayload(summary, {
      version: 1,
      text: plaintext,
      attachments: []
    });
  },

  async decryptPayload(summary: SecretChatSummary, message: SecretChatMessage) {
    const plaintext = await decryptBinary(summary, message.ciphertext, message.nonce);
    const decoded = decoder.decode(plaintext);

    try {
      return normalizePayload(JSON.parse(decoded));
    } catch {
      return {
        version: 1,
        text: decoded,
        attachments: []
      };
    }
  },

  async decryptMessage(summary: SecretChatSummary, message: SecretChatMessage) {
    const payload = await this.decryptPayload(summary, message);
    return payload.text ?? "";
  },

  async encryptAttachmentToFile(
    summary: SecretChatSummary,
    sourceUri: string,
    encryptedTargetUri: string
  ) {
    const fileBase64 = await FileSystem.readAsStringAsync(sourceUri, {
      encoding: FileSystem.EncodingType.Base64
    });
    const encrypted = await encryptBinary(summary, naclUtil.decodeBase64(fileBase64));
    await FileSystem.writeAsStringAsync(encryptedTargetUri, encrypted.ciphertextBase64, {
      encoding: FileSystem.EncodingType.Base64
    });
    return {
      fileNonce: encrypted.nonce
    };
  },

  async decryptAttachmentToFile(
    summary: SecretChatSummary,
    ciphertextUri: string,
    targetUri: string,
    fileNonce: string
  ) {
    const ciphertextBase64 = await FileSystem.readAsStringAsync(ciphertextUri, {
      encoding: FileSystem.EncodingType.Base64
    });
    const plaintext = await decryptBinary(summary, ciphertextBase64, fileNonce);
    await FileSystem.writeAsStringAsync(targetUri, naclUtil.encodeBase64(plaintext), {
      encoding: FileSystem.EncodingType.Base64
    });
    return targetUri;
  }
};
