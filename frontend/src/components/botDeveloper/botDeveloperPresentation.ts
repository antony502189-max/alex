import type { DeveloperBot } from "../../types";

export type BotFormState = {
  displayName: string;
  username: string;
  description: string;
  about: string;
  webAppUrl: string;
  webhookUrl: string;
  webhookSecret: string;
  supportsInline: boolean;
};

export const EMPTY_BOT_FORM: BotFormState = {
  about: "",
  description: "",
  displayName: "",
  supportsInline: false,
  username: "",
  webAppUrl: "",
  webhookSecret: "",
  webhookUrl: ""
};

export function toBotFormState(bot: DeveloperBot): BotFormState {
  return {
    about: bot.about ?? "",
    description: bot.description ?? "",
    displayName: bot.displayName,
    supportsInline: bot.supportsInline,
    username: bot.username,
    webAppUrl: bot.webAppUrl ?? "",
    webhookSecret: "",
    webhookUrl: bot.webhookUrl ?? ""
  };
}

export function isBotFormBlank(form: BotFormState) {
  return form.displayName === "" && form.username === "";
}

export function canSaveBotForm(form: BotFormState) {
  return form.displayName.trim().length > 0 && form.username.trim().length > 0;
}

export function buildBotSavePayload(form: BotFormState) {
  return {
    about: form.about.trim() || undefined,
    description: form.description.trim() || undefined,
    displayName: form.displayName.trim(),
    supportsInline: form.supportsInline,
    username: form.username.trim(),
    webAppUrl: form.webAppUrl.trim() || undefined
  };
}

export function buildBotWebhookPayload(form: BotFormState) {
  return {
    secretToken: form.webhookSecret.trim() || undefined,
    webhookUrl: form.webhookUrl.trim()
  };
}

export function buildOwnedBotMeta(bot: DeveloperBot) {
  return `token ${bot.apiTokenPrefix}...${bot.supportsInline ? " | inline" : ""}`;
}

export function buildWebhookStatus(bot: DeveloperBot) {
  return bot.webhookEnabled ? bot.webhookUrl ?? "configured" : "not configured";
}

export function buildWebhookSecretStatus(bot: DeveloperBot) {
  return bot.hasWebhookSecret ? "configured" : "not configured";
}
