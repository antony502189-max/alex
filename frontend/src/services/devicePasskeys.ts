import * as SecureStore from "expo-secure-store";
import nacl from "tweetnacl";
import * as naclUtil from "tweetnacl-util";
import type { DevicePasskey } from "../types";

const STORAGE_KEY = "alex-device-passkeys";

function toHex(bytes: Uint8Array) {
  return [...bytes].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function normalizePhoneNumber(value: string) {
  return value.trim();
}

function normalizeDevicePasskey(value: unknown): DevicePasskey | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const candidate = value as Record<string, unknown>;
  const credentialId =
    typeof candidate.credentialId === "string" ? candidate.credentialId.trim() : "";
  const publicKey = typeof candidate.publicKey === "string" ? candidate.publicKey.trim() : "";
  const phoneNumber =
    typeof candidate.phoneNumber === "string" ? normalizePhoneNumber(candidate.phoneNumber) : "";
  const label =
    typeof candidate.label === "string" && candidate.label.trim().length > 0
      ? candidate.label.trim()
      : null;
  const createdAt = typeof candidate.createdAt === "string" ? candidate.createdAt : "";
  const lastUsedAt = typeof candidate.lastUsedAt === "string" ? candidate.lastUsedAt : null;

  if (!credentialId || !publicKey || !phoneNumber || !createdAt) {
    return null;
  }

  return {
    credentialId,
    publicKey,
    phoneNumber,
    label,
    createdAt,
    lastUsedAt
  };
}

async function readRegistry() {
  const value = await SecureStore.getItemAsync(STORAGE_KEY);
  if (!value) {
    return [] as DevicePasskey[];
  }

  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map(normalizeDevicePasskey)
      .filter((entry): entry is DevicePasskey => !!entry)
      .sort((left, right) => {
        const leftStamp = left.lastUsedAt ?? left.createdAt;
        const rightStamp = right.lastUsedAt ?? right.createdAt;
        return rightStamp.localeCompare(leftStamp);
      });
  } catch {
    return [];
  }
}

async function writeRegistry(passkeys: DevicePasskey[]) {
  if (passkeys.length === 0) {
    await SecureStore.deleteItemAsync(STORAGE_KEY);
    return;
  }

  await SecureStore.setItemAsync(STORAGE_KEY, JSON.stringify(passkeys));
}

export const devicePasskeys = {
  async list() {
    return readRegistry();
  },

  async listForPhoneNumber(phoneNumber: string) {
    const normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    if (!normalizedPhoneNumber) {
      return [] as DevicePasskey[];
    }
    const passkeys = await readRegistry();
    return passkeys.filter((entry) => entry.phoneNumber === normalizedPhoneNumber);
  },

  async create(phoneNumber: string, label?: string | null) {
    const normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    if (!normalizedPhoneNumber) {
      throw new Error("Phone number is required to create a device passkey");
    }

    const keyPair = nacl.box.keyPair();
    const passkey: DevicePasskey = {
      credentialId: toHex(nacl.randomBytes(18)),
      publicKey: naclUtil.encodeBase64(keyPair.publicKey),
      phoneNumber: normalizedPhoneNumber,
      label: label?.trim() ? label.trim() : null,
      createdAt: new Date().toISOString(),
      lastUsedAt: null
    };

    const current = await readRegistry();
    await writeRegistry([
      passkey,
      ...current.filter((entry) => entry.credentialId !== passkey.credentialId)
    ]);
    return passkey;
  },

  async touch(credentialId: string) {
    const current = await readRegistry();
    await writeRegistry(
      current.map((entry) =>
        entry.credentialId === credentialId
          ? {
              ...entry,
              lastUsedAt: new Date().toISOString()
            }
          : entry
      )
    );
  },

  async remove(credentialId: string) {
    const current = await readRegistry();
    await writeRegistry(current.filter((entry) => entry.credentialId !== credentialId));
  }
};
