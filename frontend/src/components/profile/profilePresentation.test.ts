import type { AuthSecurityEvent, DevicePasskey } from "../../types";
import {
  formatAccountIdentity,
  formatAccountTitle,
  formatPasskeyUsage,
  formatSecurityEventDeviceMeta,
  formatSecurityEventNetworkMeta,
  formatSecurityEventTitle
} from "./profilePresentation";

const SECURITY_EVENT: AuthSecurityEvent = {
  appVersion: "1.0.0",
  createdAt: "2026-03-27T10:00:00.000Z",
  details: "Approved QR login",
  deviceName: "Pixel",
  eventId: "event-1",
  eventType: "LOGIN_APPROVED",
  ipAddress: "127.0.0.1",
  platform: "Android",
  sessionId: "session-1",
  severity: "INFO",
  userAgent: "Expo",
  userId: "user-1"
};

const PASSKEY: DevicePasskey = {
  createdAt: "2026-03-27T09:00:00.000Z",
  credentialId: "cred-1",
  label: "Phone key",
  lastUsedAt: null,
  phoneNumber: "+375291111111",
  publicKey: "public-key"
};

describe("profilePresentation", () => {
  it("formats account labels", () => {
    expect(formatAccountTitle("Alex", true)).toBe("Alex | active");
    expect(formatAccountIdentity("+375291111111", "alex")).toBe("+375291111111 | @alex");
  });

  it("formats security event lines", () => {
    expect(formatSecurityEventTitle(SECURITY_EVENT)).toBe("LOGIN_APPROVED | INFO");
    expect(formatSecurityEventDeviceMeta(SECURITY_EVENT)).toBe("Pixel | Android | 1.0.0");
    expect(formatSecurityEventNetworkMeta(SECURITY_EVENT)).toBe("127.0.0.1 | Expo");
  });

  it("formats passkey usage state", () => {
    expect(formatPasskeyUsage(PASSKEY)).toBe("Not used yet");
    expect(
      formatPasskeyUsage({
        ...PASSKEY,
        lastUsedAt: "2026-03-27T11:00:00.000Z"
      })
    ).toContain("Last used");
  });
});
