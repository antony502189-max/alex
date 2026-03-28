import { useEffect, useState } from "react";
import { api } from "../../services/api";
import { registerForPushNotificationsAsync } from "../../services/notifications";
import { useAppStore } from "../../store/useAppStore";
import type {
  AccountDeletionJob,
  AccountExportJob,
  AuthSecurityEvent,
  AuthSession,
  PhoneChangeChallenge
} from "../../types";
import { toAuthSession } from "./profileSessionUtils";

type UseProfileSecurityControlsParams = {
  onError: (value: string | null) => void;
  onNotice: (value: string | null) => void;
  setSession: (session: AuthSession) => void;
  token: string;
};

export function useProfileSecurityControls({
  onError,
  onNotice,
  setSession,
  token
}: UseProfileSecurityControlsParams) {
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [twoFactorHint, setTwoFactorHint] = useState("");
  const [twoFactorEnabledAt, setTwoFactorEnabledAt] = useState<string | null>(null);
  const [twoFactorPassword, setTwoFactorPassword] = useState("");
  const [twoFactorDisablePassword, setTwoFactorDisablePassword] = useState("");
  const [updatingTwoFactor, setUpdatingTwoFactor] = useState(false);
  const [securityEvents, setSecurityEvents] = useState<AuthSecurityEvent[]>([]);
  const [newPhoneNumber, setNewPhoneNumber] = useState("");
  const [phoneChangeChallenge, setPhoneChangeChallenge] = useState<PhoneChangeChallenge | null>(null);
  const [phoneChangeCode, setPhoneChangeCode] = useState("");
  const [changingPhone, setChangingPhone] = useState(false);
  const [accountExportJob, setAccountExportJob] = useState<AccountExportJob | null>(null);
  const [accountDeletionJob, setAccountDeletionJob] = useState<AccountDeletionJob | null>(null);
  const [deletionReason, setDeletionReason] = useState("");
  const [deletionDelayDays, setDeletionDelayDays] = useState("30");
  const [exportingAccount, setExportingAccount] = useState(false);
  const [schedulingDeletion, setSchedulingDeletion] = useState(false);
  const [refreshingPush, setRefreshingPush] = useState(false);
  const [clearingPush, setClearingPush] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      onError(null);
      try {
        const [twoFactorStatus, nextSecurityEvents] = await Promise.all([
          api.getTwoFactorStatus(token),
          api.getSecurityEvents(token)
        ]);

        if (!cancelled) {
          setTwoFactorEnabled(twoFactorStatus.enabled);
          setTwoFactorHint(twoFactorStatus.hint ?? "");
          setTwoFactorEnabledAt(twoFactorStatus.enabledAt);
          setSecurityEvents(nextSecurityEvents);
        }
      } catch (loadError) {
        if (!cancelled) {
          onError(loadError instanceof Error ? loadError.message : "Unable to load security settings");
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
    };
  }, [onError, token]);

  async function handleEnableTwoFactor() {
    setUpdatingTwoFactor(true);
    onError(null);
    try {
      const nextStatus = await api.enableTwoFactor(token, {
        password: twoFactorPassword,
        hint: twoFactorHint.trim() || undefined
      });
      setTwoFactorEnabled(nextStatus.enabled);
      setTwoFactorHint(nextStatus.hint ?? "");
      setTwoFactorEnabledAt(nextStatus.enabledAt);
      setTwoFactorPassword("");
      const latestSession = useAppStore.getState().session;
      if (latestSession) {
        setSession({
          ...latestSession,
          trustedSession: true
        });
      }
    } catch (twoFactorError) {
      onError(
        twoFactorError instanceof Error
          ? twoFactorError.message
          : "Unable to enable two-factor"
      );
    } finally {
      setUpdatingTwoFactor(false);
    }
  }

  async function handleDisableTwoFactor() {
    setUpdatingTwoFactor(true);
    onError(null);
    try {
      const nextStatus = await api.disableTwoFactor(token, {
        password: twoFactorDisablePassword
      });
      setTwoFactorEnabled(nextStatus.enabled);
      setTwoFactorHint(nextStatus.hint ?? "");
      setTwoFactorEnabledAt(nextStatus.enabledAt);
      setTwoFactorDisablePassword("");
    } catch (twoFactorError) {
      onError(
        twoFactorError instanceof Error
          ? twoFactorError.message
          : "Unable to disable two-factor"
      );
    } finally {
      setUpdatingTwoFactor(false);
    }
  }

  async function handleRefreshPushToken() {
    setRefreshingPush(true);
    onError(null);
    onNotice(null);
    try {
      const pushToken = await registerForPushNotificationsAsync();
      if (!pushToken) {
        onNotice("Push token was not granted on this device.");
        return;
      }
      await api.updateCurrentPushToken(token, {
        provider: "EXPO",
        pushToken
      });
      onNotice("Push notifications refreshed for this device.");
    } catch (pushError) {
      onError(pushError instanceof Error ? pushError.message : "Unable to refresh push token");
    } finally {
      setRefreshingPush(false);
    }
  }

  async function handleClearPushToken() {
    setClearingPush(true);
    onError(null);
    onNotice(null);
    try {
      await api.clearCurrentPushToken(token);
      onNotice("Push notifications disabled for this device.");
    } catch (pushError) {
      onError(pushError instanceof Error ? pushError.message : "Unable to clear push token");
    } finally {
      setClearingPush(false);
    }
  }

  async function handleRequestPhoneChange() {
    if (!newPhoneNumber.trim()) {
      return;
    }

    setChangingPhone(true);
    onError(null);
    onNotice(null);
    try {
      const challenge = await api.requestPhoneChange(token, {
        newPhoneNumber: newPhoneNumber.trim()
      });
      setPhoneChangeChallenge(challenge);
      setPhoneChangeCode(challenge.debugCode ?? "");
      onNotice(`Verification code requested for ${challenge.newPhoneNumber}.`);
    } catch (phoneError) {
      onError(
        phoneError instanceof Error ? phoneError.message : "Unable to request phone change"
      );
    } finally {
      setChangingPhone(false);
    }
  }

  async function handleVerifyPhoneChange() {
    if (!phoneChangeChallenge || !phoneChangeCode.trim()) {
      return;
    }

    setChangingPhone(true);
    onError(null);
    onNotice(null);
    try {
      const result = await api.verifyPhoneChange(token, {
        challengeId: phoneChangeChallenge.challengeId,
        code: phoneChangeCode.trim()
      });
      const nextSession = toAuthSession(result);
      if (!nextSession) {
        throw new Error("Phone change completed without a valid session payload");
      }
      setSession(nextSession);
      setPhoneChangeChallenge(null);
      setPhoneChangeCode("");
      setNewPhoneNumber("");
      onNotice(`Phone number updated to ${nextSession.phoneNumber}.`);
    } catch (phoneError) {
      onError(
        phoneError instanceof Error ? phoneError.message : "Unable to verify phone change"
      );
    } finally {
      setChangingPhone(false);
    }
  }

  async function handleExportAccount() {
    setExportingAccount(true);
    onError(null);
    onNotice(null);
    try {
      const job = await api.exportAccount(token, {
        format: "JSON",
        includeAttachmentsMetadata: true
      });
      setAccountExportJob(job);
      onNotice("Account export requested.");
    } catch (exportError) {
      onError(
        exportError instanceof Error
          ? exportError.message
          : "Unable to request account export"
      );
    } finally {
      setExportingAccount(false);
    }
  }

  async function handleScheduleDeletion() {
    const parsedDelayDays = Number.parseInt(deletionDelayDays.trim(), 10);
    if (!Number.isFinite(parsedDelayDays) || parsedDelayDays <= 0) {
      onError("Deletion delay must be a positive number of days.");
      return;
    }

    setSchedulingDeletion(true);
    onError(null);
    onNotice(null);
    try {
      const job = await api.scheduleAccountDeletion(token, {
        reason: deletionReason.trim() || undefined,
        delayDays: parsedDelayDays
      });
      setAccountDeletionJob(job);
      onNotice("Account deletion scheduled.");
    } catch (deleteError) {
      onError(
        deleteError instanceof Error
          ? deleteError.message
          : "Unable to schedule account deletion"
      );
    } finally {
      setSchedulingDeletion(false);
    }
  }

  return {
    accountDeletionJob,
    accountExportJob,
    changingPhone,
    clearingPush,
    deletionDelayDays,
    deletionReason,
    exportingAccount,
    handleClearPushToken,
    handleDisableTwoFactor,
    handleEnableTwoFactor,
    handleExportAccount,
    handleRefreshPushToken,
    handleRequestPhoneChange,
    handleScheduleDeletion,
    handleVerifyPhoneChange,
    newPhoneNumber,
    phoneChangeChallenge,
    phoneChangeCode,
    refreshingPush,
    schedulingDeletion,
    securityEvents,
    setDeletionDelayDays,
    setDeletionReason,
    setNewPhoneNumber,
    setPhoneChangeCode,
    setTwoFactorDisablePassword,
    setTwoFactorHint,
    setTwoFactorPassword,
    twoFactorDisablePassword,
    twoFactorEnabled,
    twoFactorEnabledAt,
    twoFactorHint,
    twoFactorPassword,
    updatingTwoFactor
  };
}
