jest.mock("../services/localDatabase", () => ({
  localDatabase: {
    purgeAccountData: jest.fn(async () => undefined)
  }
}));

jest.mock("../services/secretChatLocalCleanup", () => ({
  secretChatLocalCleanup: {
    clearAllSecretState: jest.fn(async () => undefined)
  }
}));

import { useAppStore } from "./useAppStore";
import { defaultFeatureProfile } from "../config/featureFlags";
import type { AuthSession } from "../types";

function buildSession(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    token: "token",
    refreshToken: "refresh-token",
    sessionId: "session-1",
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    username: "alex",
    accessTokenExpiresAt: null,
    refreshTokenExpiresAt: null,
    authMethod: "OTP",
    trustedSession: true,
    ...overrides
  };
}

describe("useAppStore", () => {
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
  });

  it("keeps separate local account registry and switches active account", () => {
    const first = buildSession();
    const second = buildSession({
      token: "token-2",
      refreshToken: "refresh-token-2",
      sessionId: "session-2",
      userId: "user-2",
      phoneNumber: "+375292222222",
      displayName: "Nadia",
      username: "nadia"
    });

    useAppStore.getState().setSession(first);
    useAppStore.getState().setSession(second);

    expect(useAppStore.getState().activeAccountId).toBe("user-2");
    expect(Object.keys(useAppStore.getState().accountsById)).toHaveLength(2);
    expect(useAppStore.getState().session?.displayName).toBe("Nadia");

    useAppStore.getState().switchAccount("user-1");

    expect(useAppStore.getState().activeAccountId).toBe("user-1");
    expect(useAppStore.getState().session?.displayName).toBe("Alex");
  });

  it("removes the active account and falls back to another local account", () => {
    useAppStore.getState().setSession(buildSession());
    useAppStore.getState().setSession(
      buildSession({
        token: "token-2",
        refreshToken: "refresh-token-2",
        sessionId: "session-2",
        userId: "user-2",
        phoneNumber: "+375292222222",
        displayName: "Nadia",
        username: "nadia"
      })
    );

    useAppStore.getState().removeAccount("user-2");

    expect(useAppStore.getState().activeAccountId).toBe("user-1");
    expect(useAppStore.getState().session?.displayName).toBe("Alex");
    expect(Object.keys(useAppStore.getState().accountsById)).toEqual(["user-1"]);
  });

  it("coerces secret chat capability off when storing the active feature profile", () => {
    useAppStore.getState().setSession(buildSession());

    useAppStore.getState().setFeatureProfile({
      ...defaultFeatureProfile,
      secretChats: true
    });

    expect(useAppStore.getState().featureProfile?.secretChats).toBe(false);
    expect(
      useAppStore.getState().accountsById["user-1"]?.featureProfile?.secretChats
    ).toBe(false);
  });

  it("keeps local notification and appearance settings scoped to the active account", () => {
    const first = buildSession();
    const second = buildSession({
      token: "token-2",
      refreshToken: "refresh-token-2",
      sessionId: "session-2",
      userId: "user-2",
      phoneNumber: "+375292222222",
      displayName: "Nadia",
      username: "nadia"
    });

    useAppStore.getState().setSession(first);
    useAppStore.getState().updateNotificationSettings({
      includeMessagePreview: false,
      storyNotificationsEnabled: false
    });
    useAppStore.getState().updateAppearanceSettings({
      compactChatList: true
    });
    useAppStore.getState().updateChatListState({
      searchQuery: "design",
      selectedFilter: "UNREAD"
    });

    useAppStore.getState().setSession(second);

    expect(useAppStore.getState().notificationSettings.includeMessagePreview).toBe(true);
    expect(useAppStore.getState().appearanceSettings.compactChatList).toBe(false);
    expect(useAppStore.getState().chatListState.searchQuery).toBe("");

    useAppStore.getState().switchAccount("user-1");

    expect(useAppStore.getState().notificationSettings.includeMessagePreview).toBe(false);
    expect(useAppStore.getState().notificationSettings.storyNotificationsEnabled).toBe(false);
    expect(useAppStore.getState().appearanceSettings.compactChatList).toBe(true);
    expect(useAppStore.getState().chatListState.searchQuery).toBe("design");
    expect(useAppStore.getState().chatListState.selectedFilter).toBe("UNREAD");
  });
});
