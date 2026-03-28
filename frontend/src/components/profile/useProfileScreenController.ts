import { useState } from "react";
import { useAppStore } from "../../store/useAppStore";
import { useProfileIdentitySettings } from "./useProfileIdentitySettings";
import { useProfileLocalAccounts } from "./useProfileLocalAccounts";
import { useProfilePasskeys } from "./useProfilePasskeys";
import { useProfileSecurityControls } from "./useProfileSecurityControls";

type UseProfileScreenControllerParams = {
  token: string;
};

export function useProfileScreenController({
  token
}: UseProfileScreenControllerParams) {
  const session = useAppStore((state) => state.session);
  const setSession = useAppStore((state) => state.setSession);
  const notificationSettings = useAppStore((state) => state.notificationSettings);
  const dataStorageSettings = useAppStore((state) => state.dataStorageSettings);
  const appearanceSettings = useAppStore((state) => state.appearanceSettings);
  const disclosureState = useAppStore((state) => state.disclosureState);
  const updateNotificationSettings = useAppStore((state) => state.updateNotificationSettings);
  const updateDataStorageSettings = useAppStore((state) => state.updateDataStorageSettings);
  const updateAppearanceSettings = useAppStore((state) => state.updateAppearanceSettings);
  const acknowledgePrivacyDisclosure = useAppStore(
    (state) => state.acknowledgePrivacyDisclosure
  );

  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const localAccounts = useProfileLocalAccounts();
  const identity = useProfileIdentitySettings({
    onError: setError,
    setSession,
    token
  });
  const security = useProfileSecurityControls({
    onError: setError,
    onNotice: setNotice,
    setSession,
    token
  });
  const passkeys = useProfilePasskeys({
    onError: setError,
    onNotice: setNotice,
    phoneNumber: session?.phoneNumber,
    token
  });

  return {
    error,
    identity,
    localAccounts,
    notice,
    passkeys,
    notificationSettings,
    dataStorageSettings,
    appearanceSettings,
    disclosureState,
    updateNotificationSettings,
    updateDataStorageSettings,
    updateAppearanceSettings,
    acknowledgePrivacyDisclosure,
    security,
    session,
    setError,
    setNotice
  };
}

export type ProfileScreenController = ReturnType<typeof useProfileScreenController>;
