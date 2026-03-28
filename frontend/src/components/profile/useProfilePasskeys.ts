import { useEffect, useState } from "react";
import { api } from "../../services/api";
import { devicePasskeys } from "../../services/devicePasskeys";
import type { DevicePasskey } from "../../types";

type UseProfilePasskeysParams = {
  onError: (value: string | null) => void;
  onNotice: (value: string | null) => void;
  phoneNumber: string | null | undefined;
  token: string;
};

export function useProfilePasskeys({
  onError,
  onNotice,
  phoneNumber,
  token
}: UseProfilePasskeysParams) {
  const [localPasskeys, setLocalPasskeys] = useState<DevicePasskey[]>([]);
  const [passkeyLabel, setPasskeyLabel] = useState("");
  const [loadingPasskeys, setLoadingPasskeys] = useState(false);
  const [registeringPasskey, setRegisteringPasskey] = useState(false);
  const [removingPasskeyId, setRemovingPasskeyId] = useState<string | null>(null);

  async function refreshDevicePasskeys(nextPhoneNumber = phoneNumber ?? null) {
    if (!nextPhoneNumber?.trim()) {
      setLocalPasskeys([]);
      setLoadingPasskeys(false);
      return;
    }

    setLoadingPasskeys(true);
    try {
      const nextPasskeys = await devicePasskeys.listForPhoneNumber(nextPhoneNumber);
      setLocalPasskeys(nextPasskeys);
    } catch (passkeyError) {
      onError(passkeyError instanceof Error ? passkeyError.message : "Unable to load device passkeys");
    } finally {
      setLoadingPasskeys(false);
    }
  }

  useEffect(() => {
    void refreshDevicePasskeys();
  }, [phoneNumber]);

  async function handleRegisterPasskey() {
    if (!phoneNumber?.trim()) {
      return;
    }

    setRegisteringPasskey(true);
    onError(null);
    onNotice(null);
    let localPasskey: DevicePasskey | null = null;
    try {
      const options = await api.requestPasskeyRegistrationOptions(token);
      localPasskey = await devicePasskeys.create(
        phoneNumber,
        passkeyLabel.trim() || "device key"
      );
      await api.verifyPasskeyRegistration(token, {
        challengeId: options.challengeId,
        challenge: options.challenge,
        credentialId: localPasskey.credentialId,
        publicKey: localPasskey.publicKey,
        label: localPasskey.label ?? undefined,
        transports: "internal",
        signCount: 0
      });
      await refreshDevicePasskeys(phoneNumber);
      setPasskeyLabel("");
      onNotice("Device passkey registered for this phone.");
    } catch (passkeyError) {
      if (localPasskey) {
        await devicePasskeys.remove(localPasskey.credentialId).catch(() => undefined);
      }
      onError(passkeyError instanceof Error ? passkeyError.message : "Unable to register passkey");
    } finally {
      setRegisteringPasskey(false);
    }
  }

  async function handleRemovePasskey(credentialId: string) {
    setRemovingPasskeyId(credentialId);
    onError(null);
    onNotice(null);
    try {
      await devicePasskeys.remove(credentialId);
      await refreshDevicePasskeys();
      onNotice("Local device passkey removed from this phone.");
    } catch (passkeyError) {
      onError(passkeyError instanceof Error ? passkeyError.message : "Unable to remove passkey");
    } finally {
      setRemovingPasskeyId(null);
    }
  }

  return {
    loadingPasskeys,
    localPasskeys,
    passkeyLabel,
    registeringPasskey,
    removingPasskeyId,
    setPasskeyLabel,
    handleRegisterPasskey,
    handleRemovePasskey,
    refreshDevicePasskeys
  };
}
