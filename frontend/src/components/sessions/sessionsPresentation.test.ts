import type { QrLoginChallenge, UserSession } from "../../types";
import {
  buildQrChallengeMetaLines,
  buildSessionMetaLines,
  formatSessionTrust,
  getPendingQrApprovalCount,
  isCurrentSession
} from "./sessionsPresentation";

function createSession(overrides: Partial<UserSession> = {}): UserSession {
  return {
    sessionId: "session-1",
    deviceName: "Pixel 9",
    platform: "Android",
    appVersion: "1.0.0",
    userAgent: "AlexMobile/1.0",
    ipAddress: "127.0.0.1",
    createdAt: "2026-03-26T10:00:00.000Z",
    lastActiveAt: "2026-03-27T12:00:00.000Z",
    notificationsEnabled: true,
    current: false,
    authMethod: "OTP",
    trustedSession: true,
    trustedAt: "2026-03-26T10:05:00.000Z",
    ...overrides
  };
}

function createQrChallenge(overrides: Partial<QrLoginChallenge> = {}): QrLoginChallenge {
  return {
    challengeId: "challenge-1",
    status: "PENDING_APPROVAL",
    deviceName: "MacBook Pro",
    platform: "macOS",
    appVersion: "2.0.0",
    ipAddress: "10.0.0.10",
    userAgent: "AlexDesktop/2.0",
    createdAt: "2026-03-27T12:10:00.000Z",
    expiresAt: "2026-03-27T12:20:00.000Z",
    boundAt: null,
    approvedAt: null,
    ...overrides
  };
}

describe("sessionsPresentation", () => {
  it("builds device meta lines and identifies the current session", () => {
    const session = createSession();

    expect(isCurrentSession(session, "session-1")).toBe(true);
    expect(formatSessionTrust(session)).toContain("trusted since");
    expect(buildSessionMetaLines(session)).toEqual(
      expect.arrayContaining([
        "Android | 1.0.0",
        "Auth: OTP",
        "Push notifications: enabled",
        "IP 127.0.0.1"
      ])
    );
  });

  it("counts pending qr requests and formats qr metadata", () => {
    const pending = createQrChallenge();
    const approved = createQrChallenge({
      challengeId: "challenge-2",
      status: "APPROVED",
      approvedAt: "2026-03-27T12:15:00.000Z"
    });

    expect(getPendingQrApprovalCount([pending, approved])).toBe(1);
    expect(buildQrChallengeMetaLines(pending)).toEqual(
      expect.arrayContaining([
        "Status: PENDING_APPROVAL",
        "Device: MacBook Pro",
        "macOS | 2.0.0",
        "IP 10.0.0.10"
      ])
    );
  });
});
