jest.mock("../../services/api", () => ({
  api: {
    addContact: jest.fn(),
    blockUser: jest.fn(),
    createDirectChat: jest.fn(),
    getBlockedUsers: jest.fn(),
    getBots: jest.fn(),
    getContacts: jest.fn(),
    importContacts: jest.fn(),
    removeContact: jest.fn(),
    reportUser: jest.fn(),
    searchUsers: jest.fn(),
    unblockUser: jest.fn()
  }
}));

jest.mock("../../services/deviceContacts", () => ({
  deviceContacts: {
    requestAndList: jest.fn()
  }
}));

jest.mock("@react-navigation/native", () => {
  const React = require("react");
  return {
    useFocusEffect: (callback: () => void | (() => void)) => {
      React.useEffect(() => callback(), [callback]);
    }
  };
});

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { deviceContacts } from "../../services/deviceContacts";
import type {
  BotSummary,
  ChatSummary,
  Contact,
  DeviceContactRecord,
  UserSearchResult
} from "../../types";
import { useContactsController } from "./useContactsController";

function createContact(overrides: Partial<Contact> = {}): Contact {
  return {
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    contactName: "Alex Doe",
    displayName: "Alex Doe",
    lastSeenAt: null,
    online: false,
    phoneNumber: "+375291234567",
    photoAccessExpiresAt: null,
    photoUrl: null,
    userId: "user-2",
    username: "alex",
    ...overrides
  };
}

function createBot(overrides: Partial<BotSummary> = {}): BotSummary {
  return {
    description: "Helper bot",
    displayName: "Helper Bot",
    photoAccessExpiresAt: null,
    photoUrl: null,
    supportsInline: true,
    userId: "bot-1",
    username: "helper_bot",
    webAppUrl: "https://mini.app",
    ...overrides
  };
}

function createSearchResult(overrides: Partial<UserSearchResult> = {}): UserSearchResult {
  return {
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    displayName: "Bob",
    lastSeenAt: null,
    online: false,
    phoneNumber: "+375441112233",
    photoAccessExpiresAt: null,
    photoUrl: null,
    userId: "user-search",
    username: "bob",
    ...overrides
  };
}

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "DIRECT",
    commentsEnabled: true,
    crossPostingEnabled: false,
    draftText: null,
    draftUpdatedAt: null,
    forumEnabled: false,
    joinRequiresApproval: false,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    markedUnread: false,
    memberCount: 2,
    mentionCount: 0,
    mutedUntil: null,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    peerDisplayName: "Alex Doe",
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: false,
    peerPhoneNumber: "+375291234567",
    peerUserId: "user-2",
    photoAccessExpiresAt: null,
    photoUrl: null,
    pinOrder: null,
    pinned: false,
    pinnedMessageId: null,
    publicUsername: null,
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Alex Doe",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("useContactsController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (api.getContacts as jest.Mock).mockResolvedValue([createContact()]);
    (api.getBots as jest.Mock).mockResolvedValue([createBot()]);
    (api.getBlockedUsers as jest.Mock).mockResolvedValue([]);
    (api.importContacts as jest.Mock).mockResolvedValue({
      importedCount: 1,
      matchedCount: 1,
      persistedMatches: true,
      unmatchedPhoneNumbers: [],
      matchedUsers: [createContact()]
    });
    (deviceContacts.requestAndList as jest.Mock).mockResolvedValue([
      {
        contactId: "device-1",
        displayName: "Device Person",
        phoneNumbers: ["+375331112233"],
        firstName: "Device",
        lastName: "Person",
        thumbnailUri: null
      } satisfies DeviceContactRecord
    ]);
  });

  it("loads contacts, syncs device contacts, and opens direct chats", async () => {
    const onOpenChat = jest.fn();
    (api.createDirectChat as jest.Mock).mockResolvedValue(createChat());

    const { result } = renderHook(() =>
      useContactsController({
        onOpenChat,
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(api.getContacts).toHaveBeenCalledWith("token-1");
      expect(result.current.contacts).toHaveLength(1);
      expect(result.current.bots).toHaveLength(1);
    });

    await act(async () => {
      await result.current.handleLoadDeviceContacts();
    });

    expect(result.current.deviceContactsList).toHaveLength(1);
    expect(api.importContacts).toHaveBeenCalledWith("token-1", {
      contacts: [
        {
          contactName: "Device Person",
          phoneNumber: "+375331112233"
        }
      ],
      persistMatches: true
    });
    expect(result.current.notice).toBe("Imported 1 phone numbers and matched 1 users.");
    expect(result.current.importSummary).toEqual({
      importedCount: 1,
      matchedCount: 1,
      persistedMatches: true,
      unmatchedPhoneNumbers: [],
      matchedUsers: [createContact()]
    });

    await act(async () => {
      await result.current.handleOpenDirect("user-2");
    });

    expect(api.createDirectChat).toHaveBeenCalledWith("token-1", "user-2");
    expect(onOpenChat).toHaveBeenCalledWith(expect.objectContaining({ chatId: "chat-1" }));
  });

  it("searches users after debounce", async () => {
    jest.useFakeTimers();
    (api.searchUsers as jest.Mock).mockResolvedValue([createSearchResult()]);

    const { result } = renderHook(() =>
      useContactsController({
        onOpenChat: jest.fn(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(api.getContacts).toHaveBeenCalledWith("token-1");
    });

    act(() => {
      result.current.setQuery("bo");
      jest.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(api.searchUsers).toHaveBeenCalledWith("token-1", "bo");
      expect(result.current.results).toHaveLength(1);
    });

    jest.useRealTimers();
  });
});
