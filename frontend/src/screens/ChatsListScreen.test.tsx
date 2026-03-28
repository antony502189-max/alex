jest.mock("../services/api", () => ({
  api: {
    getChats: jest.fn(),
    getFolders: jest.fn(),
    revokeSession: jest.fn()
  }
}));

jest.mock("../services/localDatabase", () => ({
  localDatabase: {
    getChats: jest.fn(async () => []),
    getFolders: jest.fn(async () => []),
    replaceChats: jest.fn(async () => undefined),
    replaceFolders: jest.fn(async () => undefined)
  }
}));

jest.mock("react-native/Libraries/Lists/FlatList", () => {
  const React = require("react");
  type MockFlatListProps = {
    data?: unknown[];
    renderItem: (payload: {
      item: unknown;
      index: number;
      separators: {
        highlight: () => void;
        unhighlight: () => void;
        updateProps: () => void;
      };
    }) => ReactNode;
    ListEmptyComponent?: ReactNode;
    keyExtractor?: (item: unknown, index: number) => string;
  };

  const MockFlatList = ({
    data = [],
    renderItem,
    ListEmptyComponent,
    keyExtractor
  }: MockFlatListProps) =>
    React.createElement(
      React.Fragment,
      null,
      data.length > 0
        ? data.map((item, index) =>
            React.createElement(
              React.Fragment,
              {
                key: keyExtractor ? keyExtractor(item, index) : String(index)
              },
              renderItem({
                item,
                index,
                separators: {
                  highlight: jest.fn(),
                  unhighlight: jest.fn(),
                  updateProps: jest.fn()
                }
              })
            )
          )
        : ListEmptyComponent ?? null
    );

  return {
    __esModule: true,
    default: MockFlatList
  };
});

import React, { type ReactNode } from "react";
import { render, waitFor } from "@testing-library/react-native";
import { ChatsListScreen } from "./ChatsListScreen";
import { api } from "../services/api";
import { useAppStore } from "../store/useAppStore";

describe("ChatsListScreen", () => {
  beforeEach(() => {
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
            phoneNumber: "+375291234567",
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
        phoneNumber: "+375291234567",
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

    (api.getChats as jest.Mock).mockResolvedValue([
      {
        chatId: "chat-1",
        title: "Design team",
        chatType: "GROUP",
        lastMessageAt: new Date().toISOString(),
        pinned: false,
        pinOrder: null,
        unreadCount: 0,
        mentionCount: 0,
        replyCount: 0,
        markedUnread: false,
        archived: false,
        mutedUntil: null,
        lastReadAt: null,
        photoUrl: null,
        about: "Core design",
        publicUsername: null,
        memberCount: 4,
        forumEnabled: false,
        topicCount: 0,
        peerDisplayName: null,
        peerPhoneNumber: null,
        peerOnline: false,
        peerLastSeenAt: null,
        peerIsBot: false,
        peerBotSupportsInline: false,
        peerUserId: null,
        autoDeleteSeconds: null,
        draftText: null
      }
    ]);
    (api.getFolders as jest.Mock).mockResolvedValue([]);
  });

  it("loads chats for the active account", async () => {
    const screen = render(
      <ChatsListScreen
        onCreateChannel={jest.fn()}
        onOpenJoinByLink={jest.fn()}
        onOpenGlobalSearch={jest.fn()}
        onCreateDirect={jest.fn()}
        onCreateGroup={jest.fn()}
        onOpenCalls={jest.fn()}
        onOpenArchived={jest.fn()}
        onOpenChat={jest.fn()}
        onOpenStories={jest.fn()}
        onCreateStory={jest.fn()}
        onOpenContacts={jest.fn()}
        onOpenFolders={jest.fn()}
        onOpenProfile={jest.fn()}
        onOpenSavedMessages={jest.fn()}
      />
    );

    await waitFor(() => {
      expect(api.getChats).toHaveBeenCalledWith("token-1");
      expect(useAppStore.getState().chats[0]?.title).toBe("Design team");
      expect(screen.getByText("Design team")).toBeTruthy();
    });
  });
});
