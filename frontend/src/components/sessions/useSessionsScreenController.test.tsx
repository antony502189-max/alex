jest.mock("../../services/api", () => ({
  api: {
    approveQrLogin: jest.fn(),
    declineQrLogin: jest.fn(),
    generateQrLogin: jest.fn(),
    getQrLoginChallenges: jest.fn(),
    getSessions: jest.fn(),
    revokeOtherSessions: jest.fn(),
    revokeSession: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type {
  GeneratedQrLogin,
  QrLoginChallenge,
  UserSession
} from "../../types";
import { useSessionsScreenController } from "./useSessionsScreenController";

function createUserSession(overrides: Partial<UserSession> = {}): UserSession {
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

function createGeneratedQrLogin(overrides: Partial<GeneratedQrLogin> = {}): GeneratedQrLogin {
  return {
    challengeId: "generated-1",
    qrToken: "qr-token-123",
    createdAt: "2026-03-27T12:12:00.000Z",
    expiresAt: "2026-03-27T12:22:00.000Z",
    ...overrides
  };
}

describe("useSessionsScreenController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads sessions and pending qr requests", async () => {
    (api.getSessions as jest.Mock).mockResolvedValue([
      createUserSession({
        sessionId: "session-1",
        current: true
      }),
      createUserSession({
        sessionId: "session-2",
        deviceName: "MacBook Pro",
        platform: "macOS"
      })
    ]);
    (api.getQrLoginChallenges as jest.Mock).mockResolvedValue([createQrChallenge()]);

    const { result } = renderHook(() =>
      useSessionsScreenController({
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.sessions).toHaveLength(2);
      expect(result.current.pendingQrApprovals).toBe(1);
    });

    expect(api.getSessions).toHaveBeenCalledWith("token-1");
    expect(api.getQrLoginChallenges).toHaveBeenCalledWith("token-1");
  });

  it("generates qr login tokens and approves pending qr challenges", async () => {
    (api.getSessions as jest.Mock).mockResolvedValue([createUserSession({ current: true })]);
    (api.getQrLoginChallenges as jest.Mock)
      .mockResolvedValueOnce([createQrChallenge()])
      .mockResolvedValueOnce([createQrChallenge()])
      .mockResolvedValueOnce([
        createQrChallenge({
          status: "APPROVED",
          approvedAt: "2026-03-27T12:15:00.000Z"
        })
      ]);
    (api.generateQrLogin as jest.Mock).mockResolvedValue(createGeneratedQrLogin());
    (api.approveQrLogin as jest.Mock).mockResolvedValue(
      createQrChallenge({
        status: "APPROVED",
        approvedAt: "2026-03-27T12:15:00.000Z"
      })
    );

    const { result } = renderHook(() =>
      useSessionsScreenController({
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleCreateQr();
    });

    expect(api.generateQrLogin).toHaveBeenCalledWith("token-1");
    await waitFor(() => {
      expect(result.current.qrChallenge?.qrToken).toBe("qr-token-123");
      expect(result.current.notice).toContain("QR login token generated");
    });

    await act(async () => {
      await result.current.handleApproveQr("challenge-1");
    });

    expect(api.approveQrLogin).toHaveBeenCalledWith("token-1", "challenge-1");
    await waitFor(() => {
      expect(result.current.notice).toBe("QR login approved. The new device can finish sign-in now.");
      expect(result.current.pendingQrApprovals).toBe(0);
    });
  });
});
