jest.mock("../../services/api", () => ({
  api: {
    bindQrLogin: jest.fn(),
    pollQrLogin: jest.fn(),
    requestLoginCode: jest.fn(),
    requestPasskeyLoginOptions: jest.fn(),
    verifyLoginCode: jest.fn(),
    verifyPasskeyLogin: jest.fn(),
    verifyTwoFactor: jest.fn()
  }
}));

jest.mock("../../services/devicePasskeys", () => ({
  devicePasskeys: {
    listForPhoneNumber: jest.fn(async () => []),
    touch: jest.fn(async () => undefined)
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { devicePasskeys } from "../../services/devicePasskeys";
import { useAppStore } from "../../store/useAppStore";
import { useAuthScreenController } from "./useAuthScreenController";

describe("useAuthScreenController", () => {
  beforeEach(() => {
    useAppStore.setState({
      activeAccountId: null,
      accountsById: {},
      chats: [],
      folders: [],
      hydrated: true,
      hydrating: false,
      messagesByChat: {},
      session: null
    });
    jest.clearAllMocks();
  });

  it("binds qr auth after token normalization", async () => {
    (api.bindQrLogin as jest.Mock).mockResolvedValue({
      appVersion: "0.1.0",
      auth: null,
      deviceName: "Pixel",
      expiresAt: "2026-03-27T12:00:00.000Z",
      platform: "android",
      status: "PENDING_APPROVAL"
    });

    const { result } = renderHook(() => useAuthScreenController());

    act(() => {
      result.current.handleSelectAuthMode("qr");
      result.current.handleQrTokenChange("alex://qr/token-123");
    });

    await act(async () => {
      await result.current.handleBindQrLogin();
    });

    expect(api.bindQrLogin).toHaveBeenCalledWith(
      expect.objectContaining({
        qrToken: "token-123"
      })
    );
    expect(result.current.qrStatusDescription).toContain("waiting for approval");
  });

  it("signs in with a selected device passkey", async () => {
    (devicePasskeys.listForPhoneNumber as jest.Mock).mockResolvedValue([
      {
        credentialId: "passkey-1",
        createdAt: "2026-03-27T10:00:00.000Z",
        label: "Work laptop",
        lastUsedAt: null,
        phoneNumber: "+375291234567",
        publicKey: "public-key"
      }
    ]);
    (api.requestPasskeyLoginOptions as jest.Mock).mockResolvedValue({
      challenge: "challenge",
      challengeId: "challenge-1",
      expiresAt: "2026-03-27T12:00:00.000Z",
      phoneNumber: "+375291234567",
      userId: "user-1"
    });
    (api.verifyPasskeyLogin as jest.Mock).mockResolvedValue({
      accessTokenExpiresAt: null,
      authMethod: "PASSKEY",
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
      username: "alex"
    });

    const { result } = renderHook(() => useAuthScreenController());

    act(() => {
      result.current.handleSelectAuthMode("passkey");
    });

    await waitFor(() => {
      expect(result.current.availablePasskeys).toHaveLength(1);
    });

    await act(async () => {
      await result.current.handlePasskeyLogin();
    });

    expect(api.requestPasskeyLoginOptions).toHaveBeenCalledWith(
      expect.objectContaining({
        phoneNumber: "+375291234567"
      })
    );
    expect(devicePasskeys.touch).toHaveBeenCalledWith("passkey-1");
    expect(useAppStore.getState().session?.authMethod).toBe("PASSKEY");
  });
});
