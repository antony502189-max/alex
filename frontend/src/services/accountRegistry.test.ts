import { accountRegistry } from "./accountRegistry";
import { defaultFeatureProfile } from "../config/featureFlags";
import type { AuthSession } from "../types";

function createSession(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
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
    ...overrides
  };
}

describe("accountRegistry", () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await accountRegistry.clear();
  });

  it("coerces persisted secret chat capability off for the consumer profile", async () => {
    await accountRegistry.save({
      activeAccountId: "user-1",
      accounts: [
        {
          accountId: "user-1",
          session: createSession(),
          featureProfile: {
            ...defaultFeatureProfile,
            secretChats: true
          },
          lastActivatedAt: "2026-03-28T10:00:00.000Z"
        }
      ]
    });

    const registry = await accountRegistry.load();

    expect(registry.accounts[0]?.featureProfile?.secretChats).toBe(false);
  });

  it("restores persisted local consumer settings for an account", async () => {
    await accountRegistry.save({
      activeAccountId: "user-1",
      accounts: [
        {
          accountId: "user-1",
          session: createSession(),
          featureProfile: null,
          notificationSettings: {
            privateChatsEnabled: true,
            groupChatsEnabled: true,
            channelChatsEnabled: true,
            storyNotificationsEnabled: false,
            includeMessagePreview: false,
            reactionNotificationsEnabled: true,
            inAppSoundsEnabled: true,
            vibrateEnabled: false
          },
          dataStorageSettings: {
            autoDownloadOnCellular: false,
            autoDownloadOnWifi: true,
            autoplayGifs: true,
            autoplayVideos: false,
            saveIncomingPhotosToGallery: false,
            useLessDataForCalls: true,
            keepDownloadedMediaDays: 14
          },
          appearanceSettings: {
            compactChatList: true,
            showChatAvatars: true,
            showLinkPreviews: true,
            enterSendsMessage: false,
            largeEmojiEnabled: true,
            animatedStickerLoops: false
          },
          chatListState: {
            searchQuery: "team",
            selectedFilter: "UNREAD",
            selectedFolderId: null
          },
          disclosureState: {
            privacyAcknowledgedAt: "2026-03-28T12:00:00.000Z"
          },
          lastActivatedAt: "2026-03-28T10:00:00.000Z"
        }
      ]
    });

    const registry = await accountRegistry.load();

    expect(registry.accounts[0]?.notificationSettings?.includeMessagePreview).toBe(false);
    expect(registry.accounts[0]?.appearanceSettings?.compactChatList).toBe(true);
    expect(registry.accounts[0]?.chatListState?.selectedFilter).toBe("UNREAD");
    expect(registry.accounts[0]?.disclosureState?.privacyAcknowledgedAt).toBe(
      "2026-03-28T12:00:00.000Z"
    );
  });
});
