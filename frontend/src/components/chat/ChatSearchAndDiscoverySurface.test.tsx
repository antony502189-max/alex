import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { ChatSearchAndDiscoverySurface } from "./ChatSearchAndDiscoverySurface";
import type { BotCommand, InlineBotResult } from "../../types";

function createBotCommand(overrides: Partial<BotCommand> = {}): BotCommand {
  return {
    command: "/start",
    description: "Start the bot",
    ...overrides
  };
}

function createInlineResult(overrides: Partial<InlineBotResult> = {}): InlineBotResult {
  return {
    botUserId: "bot-1",
    botUsername: "helper_bot",
    description: "Weather card",
    resultId: "inline-1",
    text: "Minsk weather",
    title: "Weather",
    ...overrides
  };
}

function renderSurface(overrides: Partial<React.ComponentProps<typeof ChatSearchAndDiscoverySurface>> = {}) {
  return render(
    <ChatSearchAndDiscoverySurface
      activeInlineBotUsername={null}
      botCommands={[createBotCommand()]}
      botCommandsError={null}
      error={null}
      hasMoreHistory={false}
      inlineBotResults={[]}
      inlineBotResultsError={null}
      jumpingToMessage={false}
      loadingBotCommands={false}
      loadingHistory={false}
      loadingInlineBotResults={false}
      loadingOlder={false}
      onChangeSearchQuery={jest.fn()}
      onClearSearch={jest.fn()}
      onInsertBotCommand={jest.fn()}
      onLoadOlder={jest.fn()}
      onOpenMiniApp={jest.fn()}
      onRetryBotCommands={jest.fn()}
      onRetryInlineBotResults={jest.fn()}
      onSendInlineResult={jest.fn()}
      restrictionLabel={null}
      restrictionReason={null}
      searchQuery=""
      searchResultsCount={0}
      searching={false}
      showBotCommandsPanel={true}
      showLoadOlderButton={false}
      showMiniAppAction={false}
      {...overrides}
    />
  );
}

describe("ChatSearchAndDiscoverySurface", () => {
  it("shows retry controls when bot command loading fails", () => {
    const onRetryBotCommands = jest.fn();
    const screen = renderSurface({
      botCommands: [],
      botCommandsError: "Bot commands offline",
      onRetryBotCommands
    });

    expect(screen.getByText("Bot commands offline")).toBeTruthy();

    fireEvent.press(screen.getByText("Retry commands"));

    expect(onRetryBotCommands).toHaveBeenCalledTimes(1);
  });

  it("shows retry controls when inline results fail to load", () => {
    const onRetryInlineBotResults = jest.fn();
    const screen = renderSurface({
      activeInlineBotUsername: "helper_bot",
      inlineBotResults: [],
      inlineBotResultsError: "Inline results timeout",
      onRetryInlineBotResults
    });

    expect(screen.getByText("Inline results timeout")).toBeTruthy();

    fireEvent.press(screen.getByText("Retry inline"));

    expect(onRetryInlineBotResults).toHaveBeenCalledTimes(1);
  });

  it("still sends inline result cards when discovery succeeds", () => {
    const onSendInlineResult = jest.fn();
    const result = createInlineResult();
    const screen = renderSurface({
      activeInlineBotUsername: "helper_bot",
      botCommands: [],
      inlineBotResults: [result],
      onSendInlineResult,
      showBotCommandsPanel: false
    });

    fireEvent.press(screen.getByText("Weather"));

    expect(onSendInlineResult).toHaveBeenCalledWith(result);
  });
});
