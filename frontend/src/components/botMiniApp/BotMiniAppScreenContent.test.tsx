jest.mock("./BotMiniAppWebViewStage", () => {
  const React = require("react");
  const { Text } = require("react-native");

  return {
    BotMiniAppWebViewStage: ({ webViewKey }: { webViewKey: number }) =>
      React.createElement(Text, null, `Mini app webview stage ${webViewKey}`)
  };
});

import React, { createRef } from "react";
import { render } from "@testing-library/react-native";
import type { WebView } from "react-native-webview";
import { BotMiniAppScreenContent } from "./BotMiniAppScreenContent";
import type { BotMiniAppScreenController } from "./useBotMiniAppController";
import type { BotWebAppLaunch } from "../../types";

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

function createController(
  overrides: Partial<BotMiniAppScreenController> = {}
): BotMiniAppScreenController {
  return {
    canGoBack: false,
    error: null,
    handleNavigationStateChange: jest.fn(),
    handleOpenExternally: jest.fn(async () => undefined),
    handleRefresh: jest.fn(async () => undefined),
    handleWebViewError: jest.fn(),
    launch: createLaunch(),
    loadLaunch: jest.fn(async () => undefined),
    loading: false,
    refreshing: false,
    runtimeNotice: null,
    webViewKey: 1,
    ...overrides
  };
}

describe("BotMiniAppScreenContent", () => {
  it("keeps the mini app visible when there is a non-fatal runtime notice", () => {
    const screen = render(
      <BotMiniAppScreenContent
        controller={createController({
          runtimeNotice: "Unable to refresh signed session. Keeping the current mini app open."
        })}
        onClose={jest.fn()}
        title="Weather Bot"
        webViewRef={createRef<WebView>()}
      />
    );

    expect(
      screen.getByText("Unable to refresh signed session. Keeping the current mini app open.")
    ).toBeTruthy();
    expect(screen.getByText("Mini app webview stage 1")).toBeTruthy();
    expect(screen.queryByText("Mini app unavailable")).toBeNull();
  });

  it("shows the fatal error card when no signed session is available", () => {
    const screen = render(
      <BotMiniAppScreenContent
        controller={createController({
          error: "Mini app backend unavailable",
          launch: null
        })}
        onClose={jest.fn()}
        title="Weather Bot"
        webViewRef={createRef<WebView>()}
      />
    );

    expect(screen.getByText("Mini app unavailable")).toBeTruthy();
    expect(screen.getByText("Mini app backend unavailable")).toBeTruthy();
    expect(screen.queryByText("Mini app webview stage 1")).toBeNull();
  });
});
