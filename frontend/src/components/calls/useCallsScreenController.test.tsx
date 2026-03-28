jest.mock("../../services/api", () => ({
  api: {
    getRecentCalls: jest.fn()
  }
}));

jest.mock("../../services/localDatabase", () => ({
  localDatabase: {
    getRecentCalls: jest.fn(),
    replaceRecentCalls: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import type { CallHistoryEntry } from "../../types";
import { useCallsScreenController } from "./useCallsScreenController";

function createCall(overrides: Partial<CallHistoryEntry> = {}): CallHistoryEntry {
  return {
    answeredAt: null,
    callId: "call-1",
    chatId: "chat-1",
    chatType: "DIRECT",
    direction: "OUTGOING",
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

describe("useCallsScreenController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("hydrates cached calls and refreshes recent calls", async () => {
    (localDatabase.getRecentCalls as jest.Mock).mockResolvedValue([createCall({ callId: "cached-1", missed: true })]);
    (api.getRecentCalls as jest.Mock).mockResolvedValue([createCall({ callId: "remote-1" })]);
    (localDatabase.replaceRecentCalls as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useCallsScreenController({
        currentUserId: "user-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.refreshing).toBe(false);
      expect(api.getRecentCalls).toHaveBeenCalledWith("token-1", 60);
    });

    expect(localDatabase.replaceRecentCalls).toHaveBeenCalledWith("user-1", [createCall({ callId: "remote-1" })]);
    expect(result.current.missedCallsCount).toBe(0);
  });

  it("tracks join link input state", async () => {
    (localDatabase.getRecentCalls as jest.Mock).mockResolvedValue([]);
    (api.getRecentCalls as jest.Mock).mockResolvedValue([]);
    (localDatabase.replaceRecentCalls as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useCallsScreenController({
        currentUserId: "user-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.refreshing).toBe(false);
    });

    act(() => {
      result.current.handleCallLinkTokenChange(" alex://call/room-1 ");
    });

    expect(result.current.callLinkToken).toBe(" alex://call/room-1 ");
    expect(result.current.canJoinCallLink).toBe(true);
  });

  it("recognizes non-call parsed links and disables call-join submission for them", async () => {
    (localDatabase.getRecentCalls as jest.Mock).mockResolvedValue([]);
    (api.getRecentCalls as jest.Mock).mockResolvedValue([]);
    (localDatabase.replaceRecentCalls as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useCallsScreenController({
        currentUserId: "user-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.refreshing).toBe(false);
    });

    act(() => {
      result.current.handleCallLinkTokenChange("t.me/team");
    });

    expect(result.current.parsedLink).toEqual({
      type: "JOIN",
      token: "@team"
    });
    expect(result.current.canJoinCallLink).toBe(false);
  });
});
