import type { AuthFlowResult, DevicePasskey } from "../../types";
import {
  buildQrDeviceSummary,
  describeQrStatus,
  formatPasskeyCreatedAt,
  formatPasskeyLastUsedAt,
  normalizeQrToken,
  toAuthSession
} from "./authPresentation";

function createAuthFlowResult(overrides: Partial<AuthFlowResult> = {}): AuthFlowResult {
  return {
    accessTokenExpiresAt: null,
    authMethod: "OTP",
    authenticated: true,
    displayName: "Alex",
    phoneNumber: "+375291234567",
    refreshToken: "refresh-1",
    refreshTokenExpiresAt: null,
    requiresTwoFactor: false,
    sessionId: "session-1",
    token: "token-1",
    trustedSession: true,
    twoFactorChallengeId: null,
    twoFactorHint: null,
    userId: "user-1",
    username: "alex",
    ...overrides
  };
}

function createPasskey(overrides: Partial<DevicePasskey> = {}): DevicePasskey {
  return {
    credentialId: "passkey-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    label: "MacBook",
    lastUsedAt: "2026-03-27T11:00:00.000Z",
    phoneNumber: "+375291234567",
    publicKey: "public-key",
    ...overrides
  };
}

describe("authPresentation", () => {
  it("converts a completed auth flow result into a session", () => {
    expect(toAuthSession(createAuthFlowResult())).toEqual(
      expect.objectContaining({
        trustedSession: true,
        token: "token-1",
        userId: "user-1"
      })
    );
  });

  it("normalizes qr deep links and query params", () => {
    expect(normalizeQrToken("alex://qr/token-123")).toBe("token-123");
    expect(normalizeQrToken("https://example.com?qrToken=token-456")).toBe("token-456");
  });

  it("derives qr and passkey presentation helpers", () => {
    expect(
      buildQrDeviceSummary({
        appVersion: "0.1.0",
        deviceName: "Pixel",
        platform: "android"
      })
    ).toBe("Pixel | android | 0.1.0");
    expect(describeQrStatus("APPROVED")).toContain("approved");
    expect(formatPasskeyCreatedAt(createPasskey())).toContain("Added");
    expect(formatPasskeyLastUsedAt(createPasskey({ lastUsedAt: null }))).toBe("Not used yet");
  });
});
