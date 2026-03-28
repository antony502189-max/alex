import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type { ActiveInlineBotQuery } from "./chatScreenUtils";
import { useChatBotDiscovery } from "./useChatBotDiscovery";

jest.mock("../../services/api", () => ({
  api: {
    getBotCommands: jest.fn(),
    getInlineBotResults: jest.fn()
  }
}));

const mockedApi = api as jest.Mocked<typeof api>;

describe("useChatBotDiscovery", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockedApi.getBotCommands.mockReset();
    mockedApi.getInlineBotResults.mockReset();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it("loads bot commands for direct bot chats", async () => {
    mockedApi.getBotCommands.mockResolvedValue([
      { command: "/start", description: "Start the bot" }
    ]);
    mockedApi.getInlineBotResults.mockResolvedValue([]);

    const { result } = renderHook(() =>
      useChatBotDiscovery({
        activeInlineQuery: null,
        chatType: "DIRECT",
        peerIsBot: true,
        peerUserId: "bot-1",
        token: "token-1"
      })
    );

    await waitFor(() =>
      expect(result.current.botCommands).toEqual([
        { command: "/start", description: "Start the bot" }
      ])
    );
    expect(mockedApi.getBotCommands).toHaveBeenCalledWith("token-1", "bot-1");
  });

  it("exposes bot command retry state when command loading fails", async () => {
    mockedApi.getBotCommands
      .mockRejectedValueOnce(new Error("Bot commands offline"))
      .mockResolvedValueOnce([{ command: "/start", description: "Start the bot" }]);

    const { result } = renderHook(() =>
      useChatBotDiscovery({
        activeInlineQuery: null,
        chatType: "DIRECT",
        peerIsBot: true,
        peerUserId: "bot-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loadingBotCommands).toBe(false);
      expect(result.current.botCommandsError).toBe("Bot commands offline");
    });

    await act(async () => {
      result.current.retryBotCommands();
    });

    await waitFor(() =>
      expect(result.current.botCommands).toEqual([
        { command: "/start", description: "Start the bot" }
      ])
    );
    expect(result.current.botCommandsError).toBeNull();
    expect(mockedApi.getBotCommands).toHaveBeenCalledTimes(2);
  });

  it("debounces inline bot results and clears them when the query is removed", async () => {
    mockedApi.getBotCommands.mockResolvedValue([]);
    mockedApi.getInlineBotResults.mockResolvedValue([
      {
        botUserId: "bot-1",
        botUsername: "helper_bot",
        description: "Weather card",
        resultId: "inline-1",
        text: "Minsk weather",
        title: "Weather"
      }
    ]);

    const { result, rerender } = renderHook(
      ({ activeInlineQuery }: { activeInlineQuery: ActiveInlineBotQuery | null }) =>
        useChatBotDiscovery({
          activeInlineQuery,
          chatType: "DIRECT",
          peerIsBot: true,
          peerUserId: "bot-1",
          token: "token-1"
        }),
      {
        initialProps: {
          activeInlineQuery: {
            botUsername: "helper_bot",
            query: "weather Minsk"
          }
        }
      }
    );

    act(() => {
      jest.advanceTimersByTime(250);
    });

    await waitFor(() =>
      expect(mockedApi.getInlineBotResults).toHaveBeenCalledWith(
        "token-1",
        "helper_bot",
        "weather Minsk"
      )
    );
    await waitFor(() =>
      expect(result.current.inlineBotResults).toEqual([
        {
          botUserId: "bot-1",
          botUsername: "helper_bot",
          description: "Weather card",
          resultId: "inline-1",
          text: "Minsk weather",
          title: "Weather"
        }
      ])
    );

    rerender({ activeInlineQuery: null });

    expect(result.current.inlineBotResults).toEqual([]);
    expect(result.current.loadingInlineBotResults).toBe(false);
  });

  it("keeps inline errors retryable instead of silently collapsing to empty results", async () => {
    mockedApi.getBotCommands.mockResolvedValue([]);
    mockedApi.getInlineBotResults
      .mockRejectedValueOnce(new Error("Inline results timeout"))
      .mockResolvedValueOnce([
        {
          botUserId: "bot-1",
          botUsername: "helper_bot",
          description: "Weather card",
          resultId: "inline-1",
          text: "Minsk weather",
          title: "Weather"
        }
      ]);
    const activeInlineQuery = {
      botUsername: "helper_bot",
      query: "weather Minsk"
    } as const;

    const { result } = renderHook(() =>
      useChatBotDiscovery({
        activeInlineQuery,
        chatType: "DIRECT",
        peerIsBot: true,
        peerUserId: "bot-1",
        token: "token-1"
      })
    );

    act(() => {
      jest.advanceTimersByTime(250);
    });

    await waitFor(() => {
      expect(result.current.loadingInlineBotResults).toBe(false);
      expect(result.current.inlineBotResultsError).toBe("Inline results timeout");
    });

    await act(async () => {
      result.current.retryInlineBotResults();
    });

    act(() => {
      jest.advanceTimersByTime(250);
    });

    await waitFor(() =>
      expect(result.current.inlineBotResults).toEqual([
        {
          botUserId: "bot-1",
          botUsername: "helper_bot",
          description: "Weather card",
          resultId: "inline-1",
          text: "Minsk weather",
          title: "Weather"
        }
      ])
    );
    expect(result.current.inlineBotResultsError).toBeNull();
    expect(mockedApi.getInlineBotResults).toHaveBeenCalledTimes(2);
  });
});
