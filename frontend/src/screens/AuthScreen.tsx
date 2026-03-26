import React, { useEffect, useRef, useState } from "react";
import {
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { api } from "../services/api";
import { devicePasskeys } from "../services/devicePasskeys";
import { useAppStore } from "../store/useAppStore";
import type { AuthFlowResult, AuthSession, DevicePasskey, LoginCodeChallenge } from "../types";

export function AuthScreen() {
  const setSession = useAppStore((state) => state.setSession);
  const qrPollingRef = useRef(false);

  const [authMode, setAuthMode] = useState<"otp" | "passkey" | "qr">("otp");
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

  function toAuthSession(result: AuthFlowResult): AuthSession | null {
    if (
      !result.authenticated ||
      !result.token ||
      !result.sessionId ||
      !result.userId ||
      !result.phoneNumber ||
      !result.displayName
    ) {
      return null;
    }
    return {
      token: result.token,
      refreshToken: result.refreshToken,
      sessionId: result.sessionId,
      userId: result.userId,
      phoneNumber: result.phoneNumber,
      displayName: result.displayName,
      username: result.username,
      accessTokenExpiresAt: result.accessTokenExpiresAt,
      refreshTokenExpiresAt: result.refreshTokenExpiresAt,
      authMethod: result.authMethod,
      trustedSession: Boolean(result.trustedSession)
    };
  }

  function handleAuthFlowResult(result: AuthFlowResult) {
    const nextSession = toAuthSession(result);
    if (nextSession) {
      setSession(nextSession);
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

  function normalizeQrToken(value: string) {
    const trimmed = value.trim();
    if (!trimmed) {
      return "";
    }

    const paramMatch = trimmed.match(/[?&](?:qrToken|token)=([^&\s]+)/i);
    if (paramMatch?.[1]) {
      return decodeURIComponent(paramMatch[1]);
    }

    const deepLinkMatch = trimmed.match(/^alex:\/\/(?:qr|login)\/([^/?#\s]+)/i);
    if (deepLinkMatch?.[1]) {
      return decodeURIComponent(deepLinkMatch[1]);
    }

    return trimmed;
  }

  function syncQrStatus(payload: {
    status: string;
    expiresAt: string;
    deviceName: string | null;
    platform: string | null;
    appVersion: string | null;
  }) {
    setQrStatus(payload.status);
    setQrExpiresAt(payload.expiresAt);
    const summary = [payload.deviceName, payload.platform, payload.appVersion]
      .filter(Boolean)
      .join(" | ");
    setQrDeviceSummary(summary || null);
    setAutoPollingQr(payload.status === "PENDING_APPROVAL" || payload.status === "APPROVED");
  }

  useEffect(() => {
    let cancelled = false;
    const normalizedPhoneNumber = phoneNumber.trim();
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
  }, [passkeysReloadNonce, phoneNumber]);

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
    if (authMode !== "qr" || !autoPollingQr || !normalizeQrToken(qrToken)) {
      return;
    }

    const timer = setInterval(() => {
      void handlePollQrLogin(true);
    }, 2500);

    return () => {
      clearInterval(timer);
    };
  }, [authMode, autoPollingQr, qrToken]);

  async function handleRequestCode() {
    setSubmitting(true);
    setError(null);

    try {
      const nextChallenge = await api.requestLoginCode({
        phoneNumber,
        displayName,
        deviceName: `${Platform.OS} device`,
        platform: Platform.OS,
        appVersion: "0.1.0"
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
    const normalizedQrToken = normalizeQrToken(qrToken);
    if (!normalizedQrToken) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      setQrToken(normalizedQrToken);
      const result = await api.bindQrLogin({
        qrToken: normalizedQrToken,
        deviceName: `${Platform.OS} device`,
        platform: Platform.OS,
        appVersion: "0.1.0"
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
    const normalizedQrToken = normalizeQrToken(qrToken);
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
    const normalizedPhoneNumber = phoneNumber.trim();
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
        deviceName: `${Platform.OS} device`,
        platform: Platform.OS,
        appVersion: "0.1.0"
      });

      const result = await api.verifyPasskeyLogin({
        challengeId: options.challengeId,
        challenge: options.challenge,
        credentialId: selectedPasskey.credentialId,
        signCount: 0,
        deviceName: `${Platform.OS} device`,
        platform: Platform.OS,
        appVersion: "0.1.0"
      });
      await devicePasskeys.touch(selectedPasskey.credentialId);
      handleAuthFlowResult(result);
    } catch (passkeyError) {
      setError(passkeyError instanceof Error ? passkeyError.message : "Unable to sign in with passkey");
    } finally {
      setSubmitting(false);
    }
  }

  function describeQrStatus(status: string | null) {
    if (!status) {
      return null;
    }
    switch (status) {
      case "PENDING_APPROVAL":
        return "QR login request is waiting for approval from a trusted device.";
      case "AWAITING_SCAN":
        return "QR token exists but no device metadata has been bound yet.";
      case "DECLINED":
        return "QR login request was declined.";
      case "APPROVED":
        return "QR login was approved. Check approval once more to complete sign-in.";
      case "EXPIRED":
        return "QR login token has expired.";
      case "AUTHENTICATED":
        return "QR login completed.";
      default:
        return `QR status: ${status}`;
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.authCard}>
        <Text style={styles.title}>Alex MVP</Text>
        <Text style={styles.subtitle}>
          Phone login now uses a request-code and verify-code flow. In local dev the backend can expose the debug OTP.
        </Text>
        <View style={styles.modeRow}>
          <Pressable
            onPress={() => {
              setAuthMode("otp");
              resetQrFlow();
              setError(null);
            }}
            style={[styles.modeButton, authMode === "otp" && styles.modeButtonActive]}
          >
            <Text style={[styles.modeButtonText, authMode === "otp" && styles.modeButtonTextActive]}>
              OTP
            </Text>
          </Pressable>
          <Pressable
            onPress={() => {
              setAuthMode("passkey");
              resetOtpFlow();
              resetQrFlow();
              setError(null);
            }}
            style={[styles.modeButton, authMode === "passkey" && styles.modeButtonActive]}
          >
            <Text style={[styles.modeButtonText, authMode === "passkey" && styles.modeButtonTextActive]}>
              Passkey
            </Text>
          </Pressable>
          <Pressable
            onPress={() => {
              setAuthMode("qr");
              resetOtpFlow();
              setError(null);
            }}
            style={[styles.modeButton, authMode === "qr" && styles.modeButtonActive]}
          >
            <Text style={[styles.modeButtonText, authMode === "qr" && styles.modeButtonTextActive]}>
              QR
            </Text>
          </Pressable>
        </View>
        {authMode === "otp" && !challenge && !twoFactorChallengeId ? (
          <>
            <TextInput
              autoCapitalize="none"
              keyboardType="phone-pad"
              onChangeText={setPhoneNumber}
              placeholder="+375291234567"
              style={styles.input}
              value={phoneNumber}
            />
            <TextInput
              onChangeText={setDisplayName}
              placeholder="Display name"
              style={styles.input}
              value={displayName}
            />
          </>
        ) : null}
        {authMode === "passkey" ? (
          <>
            <Text style={styles.infoText}>
              Sign in with a device passkey already registered on this phone from the profile security screen.
            </Text>
            <TextInput
              autoCapitalize="none"
              keyboardType="phone-pad"
              onChangeText={setPhoneNumber}
              placeholder="+375291234567"
              style={styles.input}
              value={phoneNumber}
            />
            {loadingPasskeys ? (
              <Text style={styles.infoText}>Checking device passkeys...</Text>
            ) : availablePasskeys.length > 0 ? (
              <View style={styles.passkeyList}>
                {availablePasskeys.map((passkey) => (
                  <Pressable
                    key={passkey.credentialId}
                    onPress={() => {
                      setSelectedPasskeyId(passkey.credentialId);
                      setError(null);
                    }}
                    style={[
                      styles.passkeyCard,
                      selectedPasskeyId === passkey.credentialId && styles.passkeyCardActive
                    ]}
                  >
                    <Text style={styles.passkeyTitle}>
                      {passkey.label ?? "Unnamed device passkey"}
                    </Text>
                    {selectedPasskeyId === passkey.credentialId ? (
                      <Text style={styles.passkeySelectedLabel}>Selected for sign-in</Text>
                    ) : null}
                    <Text style={styles.passkeyMeta}>
                      Added {new Date(passkey.createdAt).toLocaleString()}
                    </Text>
                    <Text style={styles.passkeyMeta}>
                      {passkey.lastUsedAt
                        ? `Last used ${new Date(passkey.lastUsedAt).toLocaleString()}`
                        : "Not used yet"}
                    </Text>
                  </Pressable>
                ))}
              </View>
            ) : (
              <Text style={styles.debugText}>
                No local device passkeys found for this phone number.
              </Text>
            )}
            <View style={styles.actionsRow}>
              <Pressable
                disabled={loadingPasskeys}
                onPress={() => setPasskeysReloadNonce((current) => current + 1)}
                style={[styles.secondaryButton, loadingPasskeys && styles.buttonDisabled]}
              >
                <Text style={styles.secondaryButtonText}>
                  {loadingPasskeys ? "Refreshing..." : "Refresh passkeys"}
                </Text>
              </Pressable>
            </View>
          </>
        ) : null}
        {authMode === "otp" && twoFactorChallengeId ? (
          <>
            <Text style={styles.infoText}>Two-factor password is required to finish sign-in.</Text>
            {twoFactorHint ? (
              <Text style={styles.debugText}>Password hint: {twoFactorHint}</Text>
            ) : null}
            <TextInput
              onChangeText={setTwoFactorPassword}
              placeholder="Two-factor password"
              secureTextEntry
              style={styles.input}
              value={twoFactorPassword}
            />
            <Pressable
              onPress={() => setTrustSession((current) => !current)}
              style={[styles.secondaryButton, styles.toggleButton]}
            >
              <Text style={styles.secondaryButtonText}>
                {trustSession ? "Remember this device" : "Do not trust this device"}
              </Text>
            </Pressable>
          </>
        ) : null}
        {authMode === "otp" && challenge ? (
          <>
            <Text style={styles.infoText}>
              Code requested for {challenge.phoneNumber}. Expires at {new Date(challenge.expiresAt).toLocaleTimeString()}.
            </Text>
            <TextInput
              autoCapitalize="none"
              keyboardType="number-pad"
              onChangeText={setVerificationCode}
              placeholder="123456"
              style={styles.input}
              value={verificationCode}
            />
            {challenge.debugCode ? (
              <Text style={styles.debugText}>Debug code: {challenge.debugCode}</Text>
            ) : null}
          </>
        ) : null}
        {authMode === "qr" ? (
          <>
            <Text style={styles.infoText}>
              Paste a QR login token generated from an active session, then bind and poll until approval is granted.
            </Text>
            <TextInput
              autoCapitalize="none"
              autoCorrect={false}
              onChangeText={(value) => {
                setQrToken(value);
                setQrStatus(null);
                setQrExpiresAt(null);
                setQrDeviceSummary(null);
                setAutoPollingQr(false);
                setError(null);
              }}
              placeholder="QR login token"
              style={styles.input}
              value={qrToken}
            />
            {qrStatus || qrExpiresAt || qrDeviceSummary ? (
              <View style={styles.statusCard}>
                <Text style={styles.statusCardTitle}>QR request state</Text>
                {describeQrStatus(qrStatus) ? (
                  <Text style={styles.statusCardText}>{describeQrStatus(qrStatus)}</Text>
                ) : null}
                {qrDeviceSummary ? (
                  <Text style={styles.statusCardMeta}>Device: {qrDeviceSummary}</Text>
                ) : null}
                {qrExpiresAt ? (
                  <Text style={styles.statusCardMeta}>
                    Expires: {new Date(qrExpiresAt).toLocaleString()}
                  </Text>
                ) : null}
                {autoPollingQr ? (
                  <Text style={styles.statusCardMeta}>
                    This screen is checking approval automatically.
                  </Text>
                ) : null}
              </View>
            ) : null}
            {describeQrStatus(qrStatus) ? (
              <Text style={styles.infoText}>{describeQrStatus(qrStatus)}</Text>
            ) : null}
          </>
        ) : null}
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {authMode === "otp" && twoFactorChallengeId ? (
          <View style={styles.actionsRow}>
            <Pressable
              disabled={submitting}
              onPress={() => {
                resetOtpFlow();
                setError(null);
              }}
              style={[styles.secondaryButton, submitting && styles.buttonDisabled]}
            >
              <Text style={styles.secondaryButtonText}>Restart</Text>
            </Pressable>
            <Pressable
              disabled={submitting || !twoFactorPassword.trim()}
              onPress={handleVerifyTwoFactor}
              style={[styles.primaryButton, (submitting || !twoFactorPassword.trim()) && styles.buttonDisabled]}
            >
              <Text style={styles.primaryButtonText}>
                {submitting ? "Verifying..." : "Verify password"}
              </Text>
            </Pressable>
          </View>
        ) : null}
        {authMode === "otp" && challenge ? (
          <View style={styles.actionsRow}>
            <Pressable
              disabled={submitting}
              onPress={() => {
                resetOtpFlow();
                setError(null);
              }}
              style={[styles.secondaryButton, submitting && styles.buttonDisabled]}
            >
              <Text style={styles.secondaryButtonText}>Back</Text>
            </Pressable>
            <Pressable
              disabled={submitting || !verificationCode.trim()}
              onPress={handleVerifyCode}
              style={[styles.primaryButton, (submitting || !verificationCode.trim()) && styles.buttonDisabled]}
            >
              <Text style={styles.primaryButtonText}>
                {submitting ? "Verifying..." : "Verify code"}
              </Text>
            </Pressable>
          </View>
        ) : null}
        {authMode === "qr" ? (
          <View style={styles.actionsRow}>
            <Pressable
              disabled={submitting || !qrToken.trim()}
              onPress={handleBindQrLogin}
              style={[styles.secondaryButton, (submitting || !qrToken.trim()) && styles.buttonDisabled]}
            >
              <Text style={styles.secondaryButtonText}>
                {submitting ? "Working..." : "Bind device"}
              </Text>
            </Pressable>
            <Pressable
              disabled={submitting || !qrToken.trim()}
              onPress={handlePollQrLogin}
              style={[styles.primaryButton, (submitting || !qrToken.trim()) && styles.buttonDisabled]}
            >
              <Text style={styles.primaryButtonText}>
                {submitting ? "Checking..." : "Check approval"}
              </Text>
            </Pressable>
          </View>
        ) : null}
        {authMode === "otp" && !challenge && !twoFactorChallengeId ? (
          <Pressable
            disabled={submitting}
            onPress={handleRequestCode}
            style={[styles.primaryButton, submitting && styles.buttonDisabled]}
          >
            <Text style={styles.primaryButtonText}>
              {submitting ? "Requesting..." : "Request code"}
            </Text>
          </Pressable>
        ) : null}
        {authMode === "passkey" ? (
          <Pressable
            disabled={submitting || !phoneNumber.trim() || loadingPasskeys || availablePasskeys.length === 0}
            onPress={() => void handlePasskeyLogin()}
            style={[
              styles.primaryButton,
              (submitting || !phoneNumber.trim() || loadingPasskeys || availablePasskeys.length === 0) &&
                styles.buttonDisabled
            ]}
          >
            <Text style={styles.primaryButtonText}>
              {submitting ? "Signing in..." : "Use device passkey"}
            </Text>
          </Pressable>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc",
    padding: 20
  },
  authCard: {
    marginTop: 80,
    borderRadius: 20,
    backgroundColor: "#ffffff",
    padding: 20,
    gap: 12
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    fontSize: 14,
    color: "#475569"
  },
  modeRow: {
    flexDirection: "row",
    gap: 10
  },
  modeButton: {
    flex: 1,
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingVertical: 10,
    alignItems: "center"
  },
  modeButtonActive: {
    backgroundColor: "#0f172a"
  },
  modeButtonText: {
    color: "#0f172a",
    fontWeight: "700"
  },
  modeButtonTextActive: {
    color: "#ffffff"
  },
  infoText: {
    color: "#0f766e",
    fontSize: 13
  },
  debugText: {
    color: "#7c2d12",
    fontSize: 13,
    fontWeight: "600"
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff"
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "600"
  },
  secondaryButton: {
    flex: 1,
    borderRadius: 14,
    backgroundColor: "#e2e8f0",
    paddingVertical: 14,
    alignItems: "center"
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontSize: 16,
    fontWeight: "600"
  },
  actionsRow: {
    flexDirection: "row",
    gap: 12
  },
  toggleButton: {
    alignItems: "flex-start"
  },
  buttonDisabled: {
    opacity: 0.6
  },
  errorText: {
    color: "#b91c1c",
    fontSize: 14
  },
  passkeyList: {
    gap: 8
  },
  passkeyCard: {
    borderRadius: 14,
    backgroundColor: "#eff6ff",
    padding: 12,
    gap: 4
  },
  passkeyCardActive: {
    borderWidth: 2,
    borderColor: "#2563eb"
  },
  passkeyTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  passkeySelectedLabel: {
    color: "#1d4ed8",
    fontSize: 12,
    fontWeight: "700"
  },
  passkeyMeta: {
    color: "#475569",
    fontSize: 12
  },
  statusCard: {
    borderRadius: 14,
    backgroundColor: "#ecfeff",
    padding: 12,
    gap: 4
  },
  statusCardTitle: {
    color: "#155e75",
    fontWeight: "700"
  },
  statusCardText: {
    color: "#0f766e",
    fontSize: 13
  },
  statusCardMeta: {
    color: "#0c4a6e",
    fontSize: 12
  }
});
