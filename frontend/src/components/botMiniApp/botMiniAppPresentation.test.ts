import type { BotWebAppLaunch } from "../../types";
import {
  buildBotMiniAppExpiryLabel,
  buildBotMiniAppSubtitle
} from "./botMiniAppPresentation";

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

describe("botMiniAppPresentation", () => {
  it("builds subtitle and expiry labels", () => {
    expect(buildBotMiniAppSubtitle(null)).toBe("Loading mini app");
    expect(buildBotMiniAppSubtitle(createLaunch())).toBe("@weatherbot");
    expect(buildBotMiniAppExpiryLabel(createLaunch())).toContain("Session expires");
  });
});
