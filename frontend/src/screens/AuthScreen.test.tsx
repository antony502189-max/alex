jest.mock("../services/api", () => ({
  api: {
    requestLoginCode: jest.fn(),
    verifyLoginCode: jest.fn(),
    verifyTwoFactor: jest.fn(),
    bindQrLogin: jest.fn(),
    pollQrLogin: jest.fn(),
    requestPasskeyLoginOptions: jest.fn(),
    verifyPasskeyLogin: jest.fn()
  }
}));

jest.mock("../services/devicePasskeys", () => ({
  devicePasskeys: {
    listForPhoneNumber: jest.fn(async () => []),
    touch: jest.fn(async () => undefined)
  }
}));

import React from "react";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { AuthScreen } from "./AuthScreen";
import { api } from "../services/api";
import { useAppStore } from "../store/useAppStore";

describe("AuthScreen", () => {
  beforeEach(() => {
    useAppStore.setState({
      hydrated: true,
      hydrating: false,
      activeAccountId: null,
      accountsById: {},
      session: null,
      chats: [],
      folders: [],
      messagesByChat: {}
    });
    jest.clearAllMocks();
  });

  it("renders add-account mode and calls onCancel", () => {
    const onCancel = jest.fn();
    const screen = render(<AuthScreen mode="ADD_ACCOUNT" onCancel={onCancel} />);

    fireEvent.press(screen.getAllByText("Back")[0]);

    expect(onCancel).toHaveBeenCalled();
  });

  it("authenticates an OTP flow and stores the session", async () => {
    (api.requestLoginCode as jest.Mock).mockResolvedValue({
      challengeId: "challenge-1",
      phoneNumber: "+375291234567",
      expiresAt: new Date().toISOString(),
      codeLength: 6,
      debugCode: "123456"
    });
    (api.verifyLoginCode as jest.Mock).mockResolvedValue({
      authenticated: true,
      requiresTwoFactor: false,
      token: "token-1",
      refreshToken: "refresh-1",
      sessionId: "session-1",
      userId: "user-1",
      phoneNumber: "+375291234567",
      displayName: "Alex",
      username: "alex",
      accessTokenExpiresAt: null,
      refreshTokenExpiresAt: null,
      authMethod: "OTP",
      trustedSession: true,
      twoFactorChallengeId: null,
      twoFactorHint: null
    });

    const screen = render(<AuthScreen />);

    await waitFor(() => {
      expect(screen.queryByText("Checking device passkeys...")).toBeNull();
    });

    fireEvent.press(screen.getByText("Request code"));

    await waitFor(() => {
      expect(api.requestLoginCode).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByText("Verify code")).toBeTruthy();
      expect(screen.getByDisplayValue("123456")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("Verify code"));

    await waitFor(() => {
      expect(useAppStore.getState().session?.userId).toBe("user-1");
    });
  });
});
