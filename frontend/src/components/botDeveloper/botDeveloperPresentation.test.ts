import type { DeveloperBot } from "../../types";
import {
  buildBotSavePayload,
  buildBotWebhookPayload,
  buildOwnedBotMeta,
  buildWebhookSecretStatus,
  buildWebhookStatus,
  canSaveBotForm,
  EMPTY_BOT_FORM,
  isBotFormBlank,
  toBotFormState
} from "./botDeveloperPresentation";

function createDeveloperBot(overrides: Partial<DeveloperBot> = {}): DeveloperBot {
  return {
    about: "About bot",
    apiTokenPrefix: "12345",
    botUserId: "bot-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    description: "Weather helper",
    displayName: "Weather Bot",
    hasWebhookSecret: true,
    lastWebhookDeliveryAt: "2026-03-27T11:00:00.000Z",
    lastWebhookError: null,
    ownerUserId: "user-1",
    photoAccessExpiresAt: null,
    photoUrl: null,
    supportsInline: true,
    tokenRotatedAt: "2026-03-27T10:05:00.000Z",
    updatedAt: "2026-03-27T11:00:00.000Z",
    username: "weatherbot",
    webAppUrl: "https://example.com/app",
    webhookEnabled: true,
    webhookUrl: "https://example.com/webhook",
    ...overrides
  };
}

describe("botDeveloperPresentation", () => {
  it("maps bot details into editable form state", () => {
    const bot = createDeveloperBot();
    const form = toBotFormState(bot);

    expect(form.username).toBe("weatherbot");
    expect(form.webhookSecret).toBe("");
    expect(canSaveBotForm(form)).toBe(true);
    expect(isBotFormBlank(EMPTY_BOT_FORM)).toBe(true);
  });

  it("builds save and webhook payloads and status labels", () => {
    const payload = buildBotSavePayload({
      ...EMPTY_BOT_FORM,
      about: " About ",
      description: " Desc ",
      displayName: " Weather Bot ",
      supportsInline: true,
      username: " weatherbot ",
      webAppUrl: " https://example.com/app "
    });

    expect(payload).toEqual({
      about: "About",
      description: "Desc",
      displayName: "Weather Bot",
      supportsInline: true,
      username: "weatherbot",
      webAppUrl: "https://example.com/app"
    });

    expect(buildBotWebhookPayload({
      ...EMPTY_BOT_FORM,
      webhookSecret: " secret ",
      webhookUrl: " https://example.com/webhook "
    })).toEqual({
      secretToken: "secret",
      webhookUrl: "https://example.com/webhook"
    });

    expect(buildOwnedBotMeta(createDeveloperBot())).toBe("token 12345... | inline");
    expect(buildWebhookStatus(createDeveloperBot())).toBe("https://example.com/webhook");
    expect(buildWebhookSecretStatus(createDeveloperBot())).toBe("configured");
  });
});
