import React from "react";
import { render } from "@testing-library/react-native";
import { JoinChatDiscoveriesList } from "./JoinChatDiscoveriesList";
import type { PublicChatDiscovery } from "../../types";

function createDiscovery(overrides: Partial<PublicChatDiscovery> = {}): PublicChatDiscovery {
  return {
    about: null,
    chatId: "chat-1",
    chatType: "GROUP",
    forumEnabled: false,
    joinRequiresApproval: true,
    joined: false,
    memberCount: 12,
    photoAccessExpiresAt: null,
    photoUrl: null,
    publicUsername: "team",
    title: "Team",
    ...overrides
  };
}

describe("JoinChatDiscoveriesList", () => {
  it("shows request-access labels for approval-only public chats", () => {
    const screen = render(
      <JoinChatDiscoveriesList
        discoveries={[createDiscovery()]}
        joining={false}
        onJoinDiscovery={jest.fn()}
      />
    );

    expect(screen.getByText("Request access")).toBeTruthy();
    expect(
      screen.getByText(
        "Matching public chats and channels you can open, join immediately, or request access to."
      )
    ).toBeTruthy();
  });
});
