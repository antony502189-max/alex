import React from "react";
import { render } from "@testing-library/react-native";
import { CallsHistoryList } from "./CallsHistoryList";
import type { CallHistoryEntry } from "../../types";

function createCall(overrides: Partial<CallHistoryEntry> = {}): CallHistoryEntry {
  return {
    answeredAt: "2026-03-27T10:01:00.000Z",
    callId: "call-1",
    chatId: "chat-1",
    chatType: "DIRECT",
    direction: "INCOMING",
    endedAt: "2026-03-27T10:03:05.000Z",
    kind: "VOICE",
    missed: false,
    mode: "DIRECT",
    participantCount: 2,
    photoAccessExpiresAt: null,
    photoUrl: null,
    startedAt: "2026-03-27T10:00:00.000Z",
    status: "ENDED",
    title: "Kate",
    ...overrides
  };
}

describe("CallsHistoryList", () => {
  it("shows status badges for missed, declined, and canceled calls", () => {
    const screen = render(
      <CallsHistoryList
        calls={[
          createCall({ callId: "missed-1", missed: true }),
          createCall({ answeredAt: null, callId: "declined-1", status: "DECLINED" }),
          createCall({ answeredAt: null, callId: "canceled-1", direction: "OUTGOING" })
        ]}
        emptyStateDescription="No calls yet"
        emptyStateTitle="No calls yet"
        onCallBack={jest.fn()}
        onOpenChat={jest.fn()}
      />
    );

    expect(screen.getByText("Missed")).toBeTruthy();
    expect(screen.getByText("Declined")).toBeTruthy();
    expect(screen.getByText("Canceled")).toBeTruthy();
  });

  it("shows an offline-aware empty state when history is unavailable", () => {
    const screen = render(
      <CallsHistoryList
        calls={[]}
        emptyStateDescription="Recent calls could not be refreshed yet."
        emptyStateTitle="Call history unavailable"
        onCallBack={jest.fn()}
        onOpenChat={jest.fn()}
      />
    );

    expect(screen.getByText("Call history unavailable")).toBeTruthy();
    expect(screen.getByText("Recent calls could not be refreshed yet.")).toBeTruthy();
  });
});
