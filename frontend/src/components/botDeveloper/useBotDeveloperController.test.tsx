jest.mock("../../services/api", () => ({
  api: {
    clearDeveloperBotWebhook: jest.fn(),
    createDeveloperBot: jest.fn(),
    getDeveloperBots: jest.fn(),
    rotateDeveloperBotToken: jest.fn(),
    updateDeveloperBot: jest.fn(),
    updateDeveloperBotWebhook: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type { DeveloperBot } from "../../types";
import { useBotDeveloperController } from "./useBotDeveloperController";

function createDeveloperBot(overrides: Partial<DeveloperBot> = {}): DeveloperBot {
  return {
    about: "About bot",
    apiTokenPrefix: "12345",
    botUserId: "bot-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    description: "Weather helper",
    displayName: "Weather Bot",
    hasWebhookSecret: false,
    lastWebhookDeliveryAt: null,
    lastWebhookError: null,
    ownerUserId: "user-1",
    photoAccessExpiresAt: null,
    photoUrl: null,
    supportsInline: false,
    tokenRotatedAt: "2026-03-27T10:05:00.000Z",
    updatedAt: "2026-03-27T11:00:00.000Z",
    username: "weatherbot",
    webAppUrl: null,
    webhookEnabled: false,
    webhookUrl: null,
    ...overrides
  };
}

describe("useBotDeveloperController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads owned bots and selects a bot for editing", async () => {
    (api.getDeveloperBots as jest.Mock).mockResolvedValue([createDeveloperBot()]);

    const { result } = renderHook(() =>
      useBotDeveloperController({
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.bots).toHaveLength(1);
    });

    act(() => {
      result.current.handleSelectBot(result.current.bots[0]);
    });

    expect(result.current.selectedBot?.username).toBe("weatherbot");
    expect(result.current.form.displayName).toBe("Weather Bot");
  });

  it("creates and rotates bot tokens and updates webhook state", async () => {
    const createdBot = createDeveloperBot({ botUserId: "bot-2", username: "newweatherbot" });
    const rotatedBot = createDeveloperBot({
      botUserId: "bot-2",
      apiTokenPrefix: "67890",
      username: "newweatherbot"
    });
    const webhookUpdatedBot = createDeveloperBot({
      botUserId: "bot-2",
      hasWebhookSecret: true,
      username: "newweatherbot",
      webhookEnabled: true,
      webhookUrl: "https://example.com/webhook"
    });

    (api.getDeveloperBots as jest.Mock).mockResolvedValue([]);
    (api.createDeveloperBot as jest.Mock).mockResolvedValue({
      apiToken: "full-token-123",
      bot: createdBot
    });
    (api.rotateDeveloperBotToken as jest.Mock).mockResolvedValue({
      apiToken: "rotated-token-456",
      bot: rotatedBot
    });
    (api.updateDeveloperBotWebhook as jest.Mock).mockResolvedValue(webhookUpdatedBot);
    (api.clearDeveloperBotWebhook as jest.Mock).mockResolvedValue(
      createDeveloperBot({
        botUserId: "bot-2",
        username: "newweatherbot",
        webhookEnabled: false,
        webhookUrl: null
      })
    );

    const { result } = renderHook(() =>
      useBotDeveloperController({
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    act(() => {
      result.current.updateForm("displayName", "New Weather Bot");
      result.current.updateForm("username", "newweatherbot");
      result.current.updateForm("supportsInline", true);
    });

    await act(async () => {
      await result.current.handleSave();
    });

    expect(api.createDeveloperBot).toHaveBeenCalledWith("token-1", {
      about: undefined,
      description: undefined,
      displayName: "New Weather Bot",
      supportsInline: true,
      username: "newweatherbot",
      webAppUrl: undefined
    });
    expect(result.current.issuedToken).toBe("full-token-123");

    await act(async () => {
      await result.current.handleRotateToken();
    });

    expect(api.rotateDeveloperBotToken).toHaveBeenCalledWith("token-1", "bot-2");
    expect(result.current.issuedToken).toBe("rotated-token-456");

    act(() => {
      result.current.updateForm("webhookUrl", "https://example.com/webhook");
      result.current.updateForm("webhookSecret", "secret-1");
    });

    await act(async () => {
      await result.current.handleSaveWebhook();
    });

    expect(api.updateDeveloperBotWebhook).toHaveBeenCalledWith("token-1", "bot-2", {
      secretToken: "secret-1",
      webhookUrl: "https://example.com/webhook"
    });

    await act(async () => {
      await result.current.handleClearWebhook();
    });

    expect(api.clearDeveloperBotWebhook).toHaveBeenCalledWith("token-1", "bot-2");
  });
});
