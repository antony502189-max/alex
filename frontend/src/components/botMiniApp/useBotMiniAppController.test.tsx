jest.mock("../../services/api", () => ({
  api: {
    getBotWebAppLaunch: jest.fn()
  }
}));

import { Linking } from "react-native";
import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type { BotWebAppLaunch } from "../../types";
import { useBotMiniAppController } from "./useBotMiniAppController";

function createLaunch(overrides: Partial<BotWebAppLaunch> = {}): BotWebAppLaunch {
  return {
    botUserId: "bot-1",
    botUsername: "weatherbot",
    chatId: "chat-1",
    expiresAt: "2026-03-27T12:45:00.000Z",
    issuedAt: "2026-03-27T12:15:00.000Z",
    launchUrl: "https://example.com/mini-app",
    ...overrides
  };
}

describe("useBotMiniAppController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads signed launch sessions and tracks webview state", async () => {
    (api.getBotWebAppLaunch as jest.Mock).mockResolvedValue(createLaunch());

    const { result } = renderHook(() =>
      useBotMiniAppController({
        botUserId: "bot-1",
        chatId: "chat-1",
        startParameter: "start",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.launch?.botUsername).toBe("weatherbot");
    });

    act(() => {
      result.current.handleNavigationStateChange({ canGoBack: true });
    });

    expect(result.current.canGoBack).toBe(true);
  });

  it("opens the mini app externally when the URL is supported", async () => {
    const canOpenUrlSpy = jest.spyOn(Linking, "canOpenURL").mockResolvedValue(true);
    const openUrlSpy = jest.spyOn(Linking, "openURL").mockResolvedValue(undefined);
    (api.getBotWebAppLaunch as jest.Mock).mockResolvedValue(createLaunch());

    const { result } = renderHook(() =>
      useBotMiniAppController({
        botUserId: "bot-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleOpenExternally();
    });

    expect(canOpenUrlSpy).toHaveBeenCalledWith("https://example.com/mini-app");
    expect(openUrlSpy).toHaveBeenCalledWith("https://example.com/mini-app");
  });

  it("refreshes by requesting a new signed launch session", async () => {
    (api.getBotWebAppLaunch as jest.Mock)
      .mockResolvedValueOnce(createLaunch())
      .mockResolvedValueOnce(createLaunch({ launchUrl: "https://example.com/mini-app?refresh=1" }));

    const { result } = renderHook(() =>
      useBotMiniAppController({
        botUserId: "bot-1",
        chatId: "chat-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.launch?.launchUrl).toBe("https://example.com/mini-app");
    });

    const initialWebViewKey = result.current.webViewKey;

    await act(async () => {
      await result.current.handleRefresh();
    });

    expect(api.getBotWebAppLaunch).toHaveBeenCalledTimes(2);
    expect(result.current.launch?.launchUrl).toBe("https://example.com/mini-app?refresh=1");
    expect(result.current.refreshing).toBe(false);
    expect(result.current.webViewKey).toBe(initialWebViewKey + 1);
  });

  it("keeps the current launch alive when refresh fails after an existing session", async () => {
    (api.getBotWebAppLaunch as jest.Mock)
      .mockResolvedValueOnce(createLaunch())
      .mockRejectedValueOnce(new Error("network timeout"));

    const { result } = renderHook(() =>
      useBotMiniAppController({
        botUserId: "bot-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.launch?.botUsername).toBe("weatherbot");
    });

    const initialLaunch = result.current.launch;
    const initialWebViewKey = result.current.webViewKey;

    await act(async () => {
      await result.current.handleRefresh();
    });

    expect(result.current.launch).toBe(initialLaunch);
    expect(result.current.error).toBeNull();
    expect(result.current.refreshing).toBe(false);
    expect(result.current.runtimeNotice).toBe(
      "Unable to refresh signed session. Keeping the current mini app open."
    );
    expect(result.current.webViewKey).toBe(initialWebViewKey);
  });

  it("stores runtime notices for unsupported external open and webview failures", async () => {
    const canOpenUrlSpy = jest.spyOn(Linking, "canOpenURL").mockResolvedValue(false);
    (api.getBotWebAppLaunch as jest.Mock).mockResolvedValue(createLaunch());

    const { result } = renderHook(() =>
      useBotMiniAppController({
        botUserId: "bot-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleOpenExternally();
    });

    expect(canOpenUrlSpy).toHaveBeenCalledWith("https://example.com/mini-app");
    expect(result.current.runtimeNotice).toBe("Mini app URL is not supported on this device");

    act(() => {
      result.current.handleWebViewError("Mini app page failed to load");
    });

    expect(result.current.runtimeNotice).toBe("Mini app page failed to load");
    expect(result.current.error).toBeNull();
  });
});
