import { useEffect, useRef, useState } from "react";
import { Platform } from "react-native";
import { api } from "../../services/api";
import { devicePasskeys } from "../../services/devicePasskeys";
import { useAppStore } from "../../store/useAppStore";
import type {
  AuthFlowResult,
  AuthSession,
  DevicePasskey,
  LoginCodeChallenge,
  QrLoginStatus
} from "../../types";
import {
  buildQrDeviceSummary,
  describeQrStatus,
  normalizeQrToken,
  toAuthSession,
  type AuthMode
} from "./authPresentation";

type UseAuthScreenControllerParams = {
  onAuthenticated?: (session: AuthSession) => void;
};

function buildDeviceContext() {
  return {
    appVersion: "0.1.0",
    deviceName: `${Platform.OS} device`,
    platform: Platform.OS
  };
}

export function useAuthScreenController({
  onAuthenticated
}: UseAuthScreenControllerParams = {}) {
  const setSession = useAppStore((state) => state.setSession);
  const qrPollingRef = useRef(false);

  const [authMode, setAuthMode] = useState<AuthMode>("otp");
  const [phoneNumber, setPhoneNumber] = useState("+375291234567");
  const [displayName, setDisplayName] = useState("Alex");
  const [challenge, setChallenge] = useState<LoginCodeChallenge | null>(null);
  const [verificationCode, setVerificationCode] = useState("");
  const [twoFactorChallengeId, setTwoFactorChallengeId] = useState<string | null>(null);
  const [twoFactorHint, setTwoFactorHint] = useState<string | null>(null);
  const [twoFactorPassword, setTwoFactorPassword] = useState("");
  const [trustSession, setTrustSession] = useState(true);
  const [qrToken, setQrToken] = useState("");
  const [qrStatus, setQrStatus] = useState<string | null>(null);
  const [qrExpiresAt, setQrExpiresAt] = useState<string | null>(null);
  const [qrDeviceSummary, setQrDeviceSummary] = useState<string | null>(null);
  const [autoPollingQr, setAutoPollingQr] = useState(false);
  const [availablePasskeys, setAvailablePasskeys] = useState<DevicePasskey[]>([]);
  const [selectedPasskeyId, setSelectedPasskeyId] = useState<string | null>(null);
  const [passkeysReloadNonce, setPasskeysReloadNonce] = useState(0);
  const [loadingPasskeys, setLoadingPasskeys] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const normalizedPhoneNumber = phoneNumber.trim();
  const normalizedQrToken = normalizeQrToken(qrToken);
  const qrStatusDescription = describeQrStatus(qrStatus);

  function handleAuthFlowResult(result: AuthFlowResult) {
    const nextSession = toAuthSession(result);
    if (nextSession) {
      setSession(nextSession);
      onAuthenticated?.(nextSession);
      return;
    }

    if (result.requiresTwoFactor && result.twoFactorChallengeId) {
      setTwoFactorChallengeId(result.twoFactorChallengeId);
      setTwoFactorHint(result.twoFactorHint);
      setTwoFactorPassword("");
      return;
    }

    throw new Error("Authentication flow returned an unsupported state");
  }

  function resetOtpFlow() {
    setChallenge(null);
    setVerificationCode("");
    setTwoFactorChallengeId(null);
    setTwoFactorHint(null);
    setTwoFactorPassword("");
  }

  function resetQrFlow() {
    setQrStatus(null);
    setQrExpiresAt(null);
    setQrDeviceSummary(null);
    setAutoPollingQr(false);
    setQrToken("");
  }

  function syncQrStatus(
    payload: Pick<QrLoginStatus, "appVersion" | "deviceName" | "expiresAt" | "platform" | "status">
  ) {
    setQrStatus(payload.status);
    setQrExpiresAt(payload.expiresAt);
    setQrDeviceSummary(buildQrDeviceSummary(payload));
    setAutoPollingQr(payload.status === "PENDING_APPROVAL" || payload.status === "APPROVED");
  }

  function handleSelectAuthMode(nextMode: AuthMode) {
    setAuthMode(nextMode);
    setError(null);

    if (nextMode === "otp") {
      resetQrFlow();
      return;
    }

    if (nextMode === "passkey") {
      resetOtpFlow();
      resetQrFlow();
      return;
    }

    resetOtpFlow();
  }

  function handleQrTokenChange(value: string) {
    setQrToken(value);
    setQrStatus(null);
    setQrExpiresAt(null);
    setQrDeviceSummary(null);
    setAutoPollingQr(false);
    setError(null);
  }

  function handleRefreshPasskeys() {
    setError(null);
    setPasskeysReloadNonce((current) => current + 1);
  }

  function handleSelectPasskey(credentialId: string) {
    setSelectedPasskeyId(credentialId);
    setError(null);
  }

  function toggleTrustSession() {
    setTrustSession((current) => !current);
  }

  useEffect(() => {
    if (authMode !== "passkey") {
      setLoadingPasskeys(false);
      return;
    }

    let cancelled = false;
    if (!normalizedPhoneNumber) {
      setAvailablePasskeys([]);
      setLoadingPasskeys(false);
      return;
    }

    setLoadingPasskeys(true);
    devicePasskeys
      .listForPhoneNumber(normalizedPhoneNumber)
      .then((passkeys) => {
        if (!cancelled) {
          setAvailablePasskeys(passkeys);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAvailablePasskeys([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingPasskeys(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [authMode, normalizedPhoneNumber, passkeysReloadNonce]);

  useEffect(() => {
    if (availablePasskeys.length === 0) {
      setSelectedPasskeyId(null);
      return;
    }

    setSelectedPasskeyId((current) =>
      current && availablePasskeys.some((passkey) => passkey.credentialId === current)
        ? current
        : availablePasskeys[0].credentialId
    );
  }, [availablePasskeys]);

  useEffect(() => {
    if (authMode !== "qr" || !autoPollingQr || !normalizedQrToken) {
      return;
    }

    const timer = setInterval(() => {
      void handlePollQrLogin(true);
    }, 2500);

    return () => {
      clearInterval(timer);
    };
  }, [authMode, autoPollingQr, normalizedQrToken]);

  async function handleRequestCode() {
    setSubmitting(true);
    setError(null);

    try {
      const nextChallenge = await api.requestLoginCode({
        phoneNumber,
        displayName,
        ...buildDeviceContext()
      });
      setChallenge(nextChallenge);
      setVerificationCode(nextChallenge.debugCode ?? "");
      setTwoFactorChallengeId(null);
      setTwoFactorHint(null);
      setTwoFactorPassword("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to request login code");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerifyCode() {
    if (!challenge) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const result = await api.verifyLoginCode({
        challengeId: challenge.challengeId,
        code: verificationCode
      });
      handleAuthFlowResult(result);
    } catch (verifyError) {
      setError(verifyError instanceof Error ? verifyError.message : "Unable to verify login code");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerifyTwoFactor() {
    if (!twoFactorChallengeId) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const result = await api.verifyTwoFactor({
        challengeId: twoFactorChallengeId,
        password: twoFactorPassword,
        trustSession
      });
      handleAuthFlowResult(result);
    } catch (verifyError) {
      setError(
        verifyError instanceof Error ? verifyError.message : "Unable to verify two-factor password"
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handleBindQrLogin() {
    if (!normalizedQrToken) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      setQrToken(normalizedQrToken);
      const result = await api.bindQrLogin({
        qrToken: normalizedQrToken,
        ...buildDeviceContext()
      });

      if (result.auth) {
        setAutoPollingQr(false);
        handleAuthFlowResult(result.auth);
        return;
      }

      syncQrStatus(result);
    } catch (bindError) {
      setAutoPollingQr(false);
      setError(bindError instanceof Error ? bindError.message : "Unable to bind QR login");
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePollQrLogin(silent = false) {
    if (!normalizedQrToken || qrPollingRef.current) {
      return;
    }

    qrPollingRef.current = true;
    if (!silent) {
      setSubmitting(true);
      setError(null);
    }

    try {
      setQrToken(normalizedQrToken);
      const result = await api.pollQrLogin({
        qrToken: normalizedQrToken
      });

      if (result.auth) {
        setAutoPollingQr(false);
        handleAuthFlowResult(result.auth);
        return;
      }

      syncQrStatus(result);
    } catch (pollError) {
      setAutoPollingQr(false);
      setError(pollError instanceof Error ? pollError.message : "Unable to poll QR login status");
    } finally {
      qrPollingRef.current = false;
      if (!silent) {
        setSubmitting(false);
      }
    }
  }

  async function handlePasskeyLogin() {
    if (!normalizedPhoneNumber) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const selectedPasskey =
        availablePasskeys.find((passkey) => passkey.credentialId === selectedPasskeyId) ??
        availablePasskeys[0] ??
        null;

      if (!selectedPasskey) {
        throw new Error("No device passkey is enrolled for this phone number on this device");
      }

      const options = await api.requestPasskeyLoginOptions({
        phoneNumber: normalizedPhoneNumber,
        ...buildDeviceContext()
      });

      const result = await api.verifyPasskeyLogin({
        challenge: options.challenge,
        challengeId: options.challengeId,
        credentialId: selectedPasskey.credentialId,
        signCount: 0,
        ...buildDeviceContext()
      });

      await devicePasskeys.touch(selectedPasskey.credentialId);
      handleAuthFlowResult(result);
    } catch (passkeyError) {
      setError(passkeyError instanceof Error ? passkeyError.message : "Unable to sign in with passkey");
    } finally {
      setSubmitting(false);
    }
  }

  return {
    authMode,
    autoPollingQr,
    availablePasskeys,
    challenge,
    displayName,
    error,
    handleBindQrLogin,
    handlePasskeyLogin,
    handlePollQrLogin,
    handleQrTokenChange,
    handleRefreshPasskeys,
    handleRequestCode,
    handleSelectAuthMode,
    handleSelectPasskey,
    handleVerifyCode,
    handleVerifyTwoFactor,
    loadingPasskeys,
    passkeyPrimaryDisabled:
      submitting || !normalizedPhoneNumber || loadingPasskeys || availablePasskeys.length === 0,
    phoneNumber,
    qrDeviceSummary,
    qrExpiresAt,
    qrStatusDescription,
    qrStatusVisible: Boolean(qrStatus || qrExpiresAt || qrDeviceSummary),
    qrToken,
    resetOtpFlow,
    selectedPasskeyId,
    setDisplayName,
    setPhoneNumber,
    setTwoFactorPassword,
    setVerificationCode,
    submitting,
    toggleTrustSession,
    trustSession,
    twoFactorChallengeId,
    twoFactorHint,
    twoFactorPassword,
    verificationCode
  };
}

export type AuthScreenController = ReturnType<typeof useAuthScreenController>;
