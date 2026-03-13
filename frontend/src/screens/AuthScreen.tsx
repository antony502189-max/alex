import React, { useState } from "react";
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
import { useAppStore } from "../store/useAppStore";
import type { AuthFlowResult, AuthSession, LoginCodeChallenge } from "../types";

export function AuthScreen() {
  const setSession = useAppStore((state) => state.setSession);

  const [authMode, setAuthMode] = useState<"otp" | "qr">("otp");
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
    setQrToken("");
  }

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
    if (!qrToken.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const result = await api.bindQrLogin({
        qrToken: qrToken.trim(),
        deviceName: `${Platform.OS} device`,
        platform: Platform.OS,
        appVersion: "0.1.0"
      });
      if (result.auth) {
        handleAuthFlowResult(result.auth);
        return;
      }
      setQrStatus(result.status);
    } catch (bindError) {
      setError(bindError instanceof Error ? bindError.message : "Unable to bind QR login");
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePollQrLogin() {
    if (!qrToken.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const result = await api.pollQrLogin({
        qrToken: qrToken.trim()
      });
      if (result.auth) {
        handleAuthFlowResult(result.auth);
        return;
      }
      setQrStatus(result.status);
    } catch (pollError) {
      setError(pollError instanceof Error ? pollError.message : "Unable to poll QR login status");
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
              setAuthMode("qr");
              resetOtpFlow();
              setError(null);
            }}
            style={[styles.modeButton, authMode === "qr" && styles.modeButtonActive]}
          >
            <Text style={[styles.modeButtonText, authMode === "qr" && styles.modeButtonTextActive]}>
              QR token
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
                if (qrStatus) {
                  setQrStatus(null);
                }
              }}
              placeholder="QR login token"
              style={styles.input}
              value={qrToken}
            />
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
  }
});
