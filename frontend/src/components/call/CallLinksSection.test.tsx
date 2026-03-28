import React from "react";
import { Share } from "react-native";
import { fireEvent, render } from "@testing-library/react-native";
import { CallLinksSection } from "./CallLinksSection";
import type { CallJoinLink, CallSession } from "../../types";

function createCall(overrides: Partial<CallSession> = {}): CallSession {
  return {
    answeredAt: "2026-03-27T10:00:00.000Z",
    callId: "call-1",
    chatId: "chat-1",
    createdByUserId: "user-1",
    endedAt: null,
    kind: "VIDEO",
    mode: "GROUP",
    participants: [],
    startedAt: "2026-03-27T09:59:30.000Z",
    status: "ACTIVE",
    viewerCanManageLinks: true,
    viewerCanModerate: true,
    ...overrides
  };
}

function createCallLink(overrides: Partial<CallJoinLink> = {}): CallJoinLink {
  return {
    chatId: "chat-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    createdByUserId: "user-1",
    expiresAt: "2099-03-28T10:00:00.000Z",
    kind: "VIDEO",
    label: "Standup",
    lastUsedAt: null,
    linkId: "link-1",
    revoked: false,
    shareUrl: "https://alex.example/call/link-1",
    token: "token-1",
    usageCount: 4,
    ...overrides
  };
}

describe("CallLinksSection", () => {
  it("shares active call links", () => {
    const shareSpy = jest.spyOn(Share, "share").mockResolvedValue({
      action: "sharedAction"
    });

    const screen = render(
      <CallLinksSection
        call={createCall()}
        callJoinLinksEnabled={true}
        callLinks={[createCallLink()]}
        onCreateCallLink={jest.fn()}
      />
    );

    fireEvent.press(screen.getByText("Share link"));

    expect(shareSpy).toHaveBeenCalledWith({
      message: "https://alex.example/call/link-1",
      url: "https://alex.example/call/link-1"
    });

    shareSpy.mockRestore();
  });

  it("does not show share action for revoked links", () => {
    const screen = render(
      <CallLinksSection
        call={createCall()}
        callJoinLinksEnabled={true}
        callLinks={[createCallLink({ revoked: true })]}
        onCreateCallLink={jest.fn()}
      />
    );

    expect(screen.getByText("Revoked")).toBeTruthy();
    expect(screen.queryByText("Share link")).toBeNull();
  });

  it("shows expired state and hides share action for expired links", () => {
    const screen = render(
      <CallLinksSection
        call={createCall()}
        callJoinLinksEnabled={true}
        callLinks={[createCallLink({ expiresAt: "2000-03-01T10:00:00.000Z" })]}
        onCreateCallLink={jest.fn()}
      />
    );

    expect(screen.getByText("Expired")).toBeTruthy();
    expect(screen.queryByText("Share link")).toBeNull();
  });
});
