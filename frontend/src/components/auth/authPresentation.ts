import type {
  AuthFlowResult,
  AuthSession,
  DevicePasskey,
  QrLoginStatus
} from "../../types";

export type AuthMode = "otp" | "passkey" | "qr";

type QrStatusPayload = Pick<
  QrLoginStatus,
  "appVersion" | "deviceName" | "expiresAt" | "platform" | "status"
>;

export const AUTH_SCREEN_TITLE = "Alex MVP";
export const AUTH_SCREEN_SUBTITLE =
  "Phone login now uses a request-code and verify-code flow. In local dev the backend can expose the debug OTP.";

export function toAuthSession(result: AuthFlowResult): AuthSession | null {
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

export function normalizeQrToken(value: string) {
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

export function buildQrDeviceSummary(
  payload: Pick<QrStatusPayload, "appVersion" | "deviceName" | "platform">
) {
  const summary = [payload.deviceName, payload.platform, payload.appVersion]
    .filter(Boolean)
    .join(" | ");
  return summary || null;
}

export function describeQrStatus(status: string | null) {
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

export function formatPasskeyCreatedAt(passkey: DevicePasskey) {
  return `Added ${new Date(passkey.createdAt).toLocaleString()}`;
}

export function formatPasskeyLastUsedAt(passkey: DevicePasskey) {
  return passkey.lastUsedAt
    ? `Last used ${new Date(passkey.lastUsedAt).toLocaleString()}`
    : "Not used yet";
}
