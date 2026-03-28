jest.mock("../../services/api", () => ({
  api: {
    createStory: jest.fn(),
    createStoryWithMedia: jest.fn(),
    getContacts: jest.fn()
  }
}));

jest.mock("../../services/imagePicker", () => ({
  pickSingleStoryMedia: jest.fn()
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { pickSingleStoryMedia } from "../../services/imagePicker";
import { api } from "../../services/api";
import type { Contact, Story } from "../../types";
import { useCreateStoryController } from "./useCreateStoryController";

function createContact(overrides: Partial<Contact> = {}): Contact {
  return {
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    contactName: "Kate",
    displayName: "Kate",
    lastSeenAt: null,
    online: false,
    phoneNumber: "+375291111111",
    photoAccessExpiresAt: null,
    photoUrl: null,
    userId: "user-1",
    username: "kate",
    ...overrides
  };
}

function createStory(overrides: Partial<Story> = {}): Story {
  return {
    audience: "DEFAULT",
    backgroundFrom: "#0f172a",
    backgroundTo: "#2563eb",
    createdAt: "2026-03-27T10:00:00.000Z",
    expired: false,
    expiresAt: "2026-03-28T10:00:00.000Z",
    media: null,
    own: true,
    ownerDisplayName: "Alex",
    ownerUserId: "user-1",
    ownerUsername: "alex",
    storyId: "story-1",
    text: "Hello",
    textColor: "#ffffff",
    viewed: false,
    viewsCount: 0,
    ...overrides
  };
}

describe("useCreateStoryController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads contacts for private audiences and toggles viewers", async () => {
    (api.getContacts as jest.Mock).mockResolvedValue([createContact()]);

    const { result } = renderHook(() =>
      useCreateStoryController({
        onCreated: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.setAudience("CUSTOM");
    });

    await waitFor(() => {
      expect(api.getContacts).toHaveBeenCalledWith("token-1");
      expect(result.current.contacts).toHaveLength(1);
    });

    act(() => {
      result.current.handleToggleViewer("user-1");
    });

    expect(result.current.selectedViewerIds).toEqual(["user-1"]);
  });

  it("creates a media story with selected contacts", async () => {
    const onCreated = jest.fn();
    const selectedStory = createStory({ storyId: "story-2" });

    (api.getContacts as jest.Mock).mockResolvedValue([createContact()]);
    (pickSingleStoryMedia as jest.Mock).mockResolvedValue({
      durationMs: 12000,
      name: "clip.mp4",
      type: "video/mp4",
      uri: "file://clip.mp4"
    });
    (api.createStoryWithMedia as jest.Mock).mockResolvedValue(selectedStory);

    const { result } = renderHook(() =>
      useCreateStoryController({
        onCreated,
        token: "token-1"
      })
    );

    act(() => {
      result.current.setAudience("CLOSE_FRIENDS");
    });

    await waitFor(() => {
      expect(result.current.contacts).toHaveLength(1);
    });

    act(() => {
      result.current.setText("Weekend");
      result.current.handleToggleViewer("user-1");
    });

    await act(async () => {
      await result.current.handlePickMedia();
    });

    await act(async () => {
      await result.current.handleCreate();
    });

    expect(api.createStoryWithMedia).toHaveBeenCalledWith("token-1", {
      allowedViewerUserIds: ["user-1"],
      audience: "CLOSE_FRIENDS",
      backgroundFrom: "#0f172a",
      backgroundTo: "#2563eb",
      durationMs: 12000,
      file: {
        durationMs: 12000,
        name: "clip.mp4",
        type: "video/mp4",
        uri: "file://clip.mp4"
      },
      text: "Weekend",
      textColor: "#ffffff"
    });
    expect(onCreated).toHaveBeenCalledWith(selectedStory);
  });
});
