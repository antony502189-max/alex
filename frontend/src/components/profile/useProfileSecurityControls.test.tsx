jest.mock("../../services/api", () => ({
  api: {
    clearCurrentPushToken: jest.fn(),
    disableTwoFactor: jest.fn(),
    enableTwoFactor: jest.fn(),
    exportAccount: jest.fn(),
    getSecurityEvents: jest.fn(),
    getTwoFactorStatus: jest.fn(),
    requestPhoneChange: jest.fn(),
    scheduleAccountDeletion: jest.fn(),
    updateCurrentPushToken: jest.fn(),
    verifyPhoneChange: jest.fn()
  }
}));

jest.mock("../../services/notifications", () => ({
  registerForPushNotificationsAsync: jest.fn()
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { useAppStore } from "../../store/useAppStore";
import type { AuthFlowResult, AuthSecurityEvent, AuthSession } from "../../types";
import { useProfileSecurityControls } from "./useProfileSecurityControls";

function createSession(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    token: "token-1",
    refreshToken: "refresh-1",
    sessionId: "session-1",
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    username: "alex",
    accessTokenExpiresAt: null,
    refreshTokenExpiresAt: null,
    authMethod: "OTP",
    trustedSession: false,
    ...overrides
  };
}

function createAuthFlowResult(overrides: Partial<AuthFlowResult> = {}): AuthFlowResult {
  return {
    authenticated: true,
    requiresTwoFactor: false,
    token: "token-2",
    refreshToken: "refresh-2",
    sessionId: "session-2",
    userId: "user-1",
    phoneNumber: "+375299999999",
    displayName: "Alex",
    username: "alex",
    accessTokenExpiresAt: null,
    refreshTokenExpiresAt: null,
    authMethod: "OTP",
    trustedSession: true,
    twoFactorChallengeId: null,
    twoFactorHint: null,
    ...overrides
  };
}

function createSecurityEvent(overrides: Partial<AuthSecurityEvent> = {}): AuthSecurityEvent {
  return {
    eventId: "event-1",
    userId: "user-1",
    sessionId: "session-1",
    eventType: "LOGIN_APPROVED",
    severity: "INFO",
    ipAddress: "127.0.0.1",
    userAgent: "Expo",
    deviceName: "Pixel",
    platform: "Android",
    appVersion: "1.0.0",
    details: "Approved QR login",
    createdAt: "2026-03-27T10:00:00.000Z",
    ...overrides
  };
}

describe("useProfileSecurityControls", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useAppStore.setState({
      hydrated: true,
      hydrating: false,
      activeAccountId: "user-1",
      accountsById: {
        "user-1": {
          session: createSession(),
          featureProfile: null,
          chats: [],
          folders: [],
          messagesByChat: {},
          lastActivatedAt: new Date().toISOString()
        }
      },
      session: createSession(),
      featureProfile: null,
      chats: [],
      folders: [],
      messagesByChat: {}
    });
  });

  it("loads security state and enables two-factor for a trusted session", async () => {
    const setSession = jest.fn();
    const onError = jest.fn();
    const onNotice = jest.fn();

    (api.getTwoFactorStatus as jest.Mock).mockResolvedValue({
      enabled: false,
      hint: null,
      enabledAt: null
    });
    (api.getSecurityEvents as jest.Mock).mockResolvedValue([createSecurityEvent()]);
    (api.enableTwoFactor as jest.Mock).mockResolvedValue({
      enabled: true,
      hint: "device",
      enabledAt: "2026-03-27T12:00:00.000Z"
    });

    const { result } = renderHook(() =>
      useProfileSecurityControls({
        onError,
        onNotice,
        setSession,
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.securityEvents).toHaveLength(1);
      expect(result.current.twoFactorEnabled).toBe(false);
    });

    act(() => {
      result.current.setTwoFactorPassword("secret-123");
      result.current.setTwoFactorHint("device");
    });

    await act(async () => {
      await result.current.handleEnableTwoFactor();
    });

    expect(api.enableTwoFactor).toHaveBeenCalledWith("token-1", {
      hint: "device",
      password: "secret-123"
    });
    expect(setSession).toHaveBeenCalledWith(
      expect.objectContaining({
        trustedSession: true
      })
    );
    expect(result.current.twoFactorEnabled).toBe(true);
  });

  it("requests and verifies a phone number change", async () => {
    const setSession = jest.fn();
    const onError = jest.fn();
    const onNotice = jest.fn();

    (api.getTwoFactorStatus as jest.Mock).mockResolvedValue({
      enabled: true,
      hint: "device",
      enabledAt: "2026-03-27T12:00:00.000Z"
    });
    (api.getSecurityEvents as jest.Mock).mockResolvedValue([]);
    (api.requestPhoneChange as jest.Mock).mockResolvedValue({
      challengeId: "challenge-1",
      newPhoneNumber: "+375299999999",
      expiresAt: "2026-03-27T13:00:00.000Z",
      debugCode: "12345"
    });
    (api.verifyPhoneChange as jest.Mock).mockResolvedValue(createAuthFlowResult());

    const { result } = renderHook(() =>
      useProfileSecurityControls({
        onError,
        onNotice,
        setSession,
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.twoFactorEnabled).toBe(true);
    });

    act(() => {
      result.current.setNewPhoneNumber("+375299999999");
    });

    await act(async () => {
      await result.current.handleRequestPhoneChange();
    });

    expect(api.requestPhoneChange).toHaveBeenCalledWith("token-1", {
      newPhoneNumber: "+375299999999"
    });
    expect(result.current.phoneChangeChallenge).toEqual(
      expect.objectContaining({
        challengeId: "challenge-1"
      })
    );
    expect(result.current.phoneChangeCode).toBe("12345");

    act(() => {
      result.current.setPhoneChangeCode("12345");
    });

    await act(async () => {
      await result.current.handleVerifyPhoneChange();
    });

    expect(api.verifyPhoneChange).toHaveBeenCalledWith("token-1", {
      challengeId: "challenge-1",
      code: "12345"
    });
    expect(setSession).toHaveBeenCalledWith(
      expect.objectContaining({
        phoneNumber: "+375299999999",
        sessionId: "session-2"
      })
    );
    expect(result.current.phoneChangeChallenge).toBeNull();
    expect(result.current.newPhoneNumber).toBe("");
  });
});
