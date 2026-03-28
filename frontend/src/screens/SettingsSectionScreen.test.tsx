jest.mock("../services/api", () => ({
  api: {
    getMe: jest.fn(),
    getPrivacyExceptions: jest.fn(),
    getLanguagePreferences: jest.fn(),
    getTwoFactorStatus: jest.fn(),
    getSecurityEvents: jest.fn(),
    getBlockedUsers: jest.fn(),
    unblockUser: jest.fn()
  }
}));

jest.mock("../services/devicePasskeys", () => ({
  devicePasskeys: {
    listForPhoneNumber: jest.fn(async () => [])
  }
}));

import React from "react";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { SettingsSectionScreen } from "./SettingsSectionScreen";
import { api } from "../services/api";
import { useAppStore } from "../store/useAppStore";

describe("SettingsSectionScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
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
          lastActivatedAt: new Date().toISOString()
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
      }
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
    (api.getPrivacyExceptions as jest.Mock).mockResolvedValue({
      phoneAllowedUserIds: [],
      phoneDisallowedUserIds: [],
      lastSeenAllowedUserIds: [],
      lastSeenDisallowedUserIds: [],
      storyAllowedUserIds: [],
      storyDisallowedUserIds: []
    });
    (api.getLanguagePreferences as jest.Mock).mockResolvedValue({
      preferredLanguage: "en",
      translationTargetLanguage: "ru"
    });
    (api.getTwoFactorStatus as jest.Mock).mockResolvedValue({
      enabled: false,
      hint: null,
      enabledAt: null
    });
    (api.getSecurityEvents as jest.Mock).mockResolvedValue([]);
    (api.getBlockedUsers as jest.Mock).mockResolvedValue([]);
    (api.unblockUser as jest.Mock).mockResolvedValue([]);
  });

  it("updates account-scoped notification settings from the notifications section", async () => {
    const screen = render(
      <SettingsSectionScreen
        onClose={jest.fn()}
        section="NOTIFICATIONS"
        token="token-1"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Notifications")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("Message previews"));

    expect(useAppStore.getState().notificationSettings.includeMessagePreview).toBe(false);
  });

  it("acknowledges the privacy disclosure from the help section", async () => {
    const screen = render(
      <SettingsSectionScreen
        onClose={jest.fn()}
        section="HELP"
        token="token-1"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Privacy contract")).toBeTruthy();
    });

    fireEvent.press(screen.getByText("Acknowledge"));

    expect(useAppStore.getState().disclosureState.privacyAcknowledgedAt).not.toBeNull();
  });
});
