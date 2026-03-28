jest.mock("../services/api", () => ({
  api: {
    getMe: jest.fn(),
    getTwoFactorStatus: jest.fn(),
    getSecurityEvents: jest.fn(),
    updateMe: jest.fn(),
    updatePrivacy: jest.fn()
  }
}));

jest.mock("../services/devicePasskeys", () => ({
  devicePasskeys: {
    listForPhoneNumber: jest.fn(async () => [])
  }
}));

import React from "react";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { ProfileScreen } from "./ProfileScreen";
import { api } from "../services/api";
import { useAppStore } from "../store/useAppStore";

describe("ProfileScreen", () => {
  beforeEach(() => {
    const now = new Date().toISOString();
    useAppStore.setState({
      hydrated: true,
      hydrating: false,
      activeAccountId: "user-1",
      accountsById: {
        "user-1": {
          session: {
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
            trustedSession: true
          },
          featureProfile: null,
          chats: [],
          folders: [],
          messagesByChat: {},
          lastActivatedAt: now
        },
        "user-2": {
          session: {
            token: "token-2",
            refreshToken: "refresh-2",
            sessionId: "session-2",
            userId: "user-2",
            phoneNumber: "+375292222222",
            displayName: "Nadia",
            username: "nadia",
            accessTokenExpiresAt: null,
            refreshTokenExpiresAt: null,
            authMethod: "OTP",
            trustedSession: true
          },
          featureProfile: null,
          chats: [],
          folders: [],
          messagesByChat: {},
          lastActivatedAt: now
        }
      },
      session: {
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
        trustedSession: true
      },
      featureProfile: null,
      chats: [],
      folders: [],
      messagesByChat: {}
    });

    (api.getMe as jest.Mock).mockResolvedValue({
      displayName: "Alex",
      username: "alex",
      about: "Builder",
      photoUrl: null,
      phonePrivacy: "CONTACTS",
      lastSeenPrivacy: "CONTACTS",
      storyPrivacy: "CONTACTS"
    });
    (api.getTwoFactorStatus as jest.Mock).mockResolvedValue({
      enabled: false,
      hint: null,
      enabledAt: null
    });
    (api.getSecurityEvents as jest.Mock).mockResolvedValue([]);
  });

  it("shows linked local accounts and lets the user open add-account flow", async () => {
    const onAddAccount = jest.fn();
    const screen = render(
      <ProfileScreen
        onAddAccount={onAddAccount}
        onClose={jest.fn()}
        onOpenSessions={jest.fn()}
        token="token-1"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Accounts on this device")).toBeTruthy();
      expect(screen.getByText("Nadia")).toBeTruthy();
    }, { timeout: 10000 });

    fireEvent.press(screen.getByText("Add account"));

    expect(onAddAccount).toHaveBeenCalled();
  }, 15000);
});
