jest.mock("../../services/api", () => ({
  api: {
    deleteMyPhoto: jest.fn(),
    getLanguagePreferences: jest.fn(),
    getMe: jest.fn(),
    getPrivacyExceptions: jest.fn(),
    searchUsers: jest.fn(),
    updateMe: jest.fn(),
    updateLanguagePreferences: jest.fn(),
    updatePrivacy: jest.fn(),
    updatePrivacyExceptions: jest.fn(),
    uploadMyPhoto: jest.fn()
  }
}));

jest.mock("../../services/imagePicker", () => ({
  pickSingleImage: jest.fn()
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { pickSingleImage } from "../../services/imagePicker";
import { api } from "../../services/api";
import { useAppStore } from "../../store/useAppStore";
import type { AuthSession, LanguagePreferences, PrivacyExceptions, UserProfile } from "../../types";
import { useProfileIdentitySettings } from "./useProfileIdentitySettings";

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
    trustedSession: true,
    ...overrides
  };
}

function createProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    username: "alex",
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    about: "Builder",
    photoUrl: null,
    photoAccessExpiresAt: null,
    phonePrivacy: "CONTACTS",
    lastSeenPrivacy: "EVERYBODY",
    storyPrivacy: "NOBODY",
    lastSeenAt: null,
    online: true,
    ...overrides
  };
}

function createPrivacyExceptions(
  overrides: Partial<PrivacyExceptions> = {}
): PrivacyExceptions {
  return {
    phoneAllowedUserIds: [],
    phoneDisallowedUserIds: [],
    lastSeenAllowedUserIds: [],
    lastSeenDisallowedUserIds: [],
    storyAllowedUserIds: [],
    storyDisallowedUserIds: [],
    ...overrides
  };
}

function createLanguagePreferences(
  overrides: Partial<LanguagePreferences> = {}
): LanguagePreferences {
  return {
    preferredLanguage: "en",
    translationTargetLanguage: "ru",
    ...overrides
  };
}

describe("useProfileIdentitySettings", () => {
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

  it("loads profile data and saves updated identity settings", async () => {
    const setSession = jest.fn();
    const onError = jest.fn();

    (api.getMe as jest.Mock).mockResolvedValue(createProfile());
    (api.getPrivacyExceptions as jest.Mock).mockResolvedValue(createPrivacyExceptions());
    (api.getLanguagePreferences as jest.Mock).mockResolvedValue(createLanguagePreferences());
    (api.updateMe as jest.Mock).mockResolvedValue(
      createProfile({
        displayName: "Alex Doe",
        username: "alexdoe"
      })
    );
    (api.updatePrivacy as jest.Mock).mockResolvedValue(
      createProfile({
        displayName: "Alex Doe",
        username: "alexdoe",
        phonePrivacy: "NOBODY",
        lastSeenPrivacy: "CONTACTS",
        storyPrivacy: "CONTACTS"
      })
    );
    (api.updatePrivacyExceptions as jest.Mock).mockResolvedValue(createPrivacyExceptions());
    (api.updateLanguagePreferences as jest.Mock).mockResolvedValue(
      createLanguagePreferences({
        preferredLanguage: "en",
        translationTargetLanguage: "ru"
      })
    );

    const { result } = renderHook(() =>
      useProfileIdentitySettings({
        onError,
        setSession,
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.displayName).toBe("Alex");
      expect(result.current.phonePrivacy).toBe("CONTACTS");
    });

    act(() => {
      result.current.setDisplayName(" Alex Doe ");
      result.current.setUsername(" alexdoe ");
      result.current.setAbout(" Updated bio ");
      result.current.setPhonePrivacy("NOBODY");
      result.current.setLastSeenPrivacy("CONTACTS");
      result.current.setStoryPrivacy("CONTACTS");
    });

    await act(async () => {
      await result.current.handleSave();
    });

    expect(api.updateMe).toHaveBeenCalledWith("token-1", {
      about: "Updated bio",
      displayName: "Alex Doe",
      username: "alexdoe"
    });
    expect(api.updatePrivacy).toHaveBeenCalledWith("token-1", {
      lastSeenPrivacy: "CONTACTS",
      phonePrivacy: "NOBODY",
      storyPrivacy: "CONTACTS"
    });
    expect(setSession).toHaveBeenCalledWith(
      expect.objectContaining({
        displayName: "Alex Doe",
        username: "alexdoe"
      })
    );
  });

  it("uploads and removes the profile photo", async () => {
    const setSession = jest.fn();
    const onError = jest.fn();

    (api.getMe as jest.Mock).mockResolvedValue(createProfile());
    (api.getPrivacyExceptions as jest.Mock).mockResolvedValue(createPrivacyExceptions());
    (api.getLanguagePreferences as jest.Mock).mockResolvedValue(createLanguagePreferences());
    (pickSingleImage as jest.Mock).mockResolvedValue({
      uri: "file:///avatar.jpg",
      name: "avatar.jpg",
      type: "image/jpeg"
    });
    (api.uploadMyPhoto as jest.Mock).mockResolvedValue(
      createProfile({
        photoUrl: "https://cdn.example/avatar.jpg"
      })
    );
    (api.deleteMyPhoto as jest.Mock).mockResolvedValue(
      createProfile({
        photoUrl: null
      })
    );

    const { result } = renderHook(() =>
      useProfileIdentitySettings({
        onError,
        setSession,
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleUploadPhoto();
    });

    expect(api.uploadMyPhoto).toHaveBeenCalledWith("token-1", {
      name: "avatar.jpg",
      type: "image/jpeg",
      uri: "file:///avatar.jpg"
    });
    expect(result.current.photoUrl).toBe("https://cdn.example/avatar.jpg");

    await act(async () => {
      await result.current.handleRemovePhoto();
    });

    expect(api.deleteMyPhoto).toHaveBeenCalledWith("token-1");
    expect(result.current.photoUrl).toBeNull();
  });
});
