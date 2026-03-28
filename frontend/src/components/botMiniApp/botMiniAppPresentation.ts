import type { BotWebAppLaunch } from "../../types";

export function buildBotMiniAppSubtitle(launch: BotWebAppLaunch | null) {
  return launch ? `@${launch.botUsername}` : "Loading mini app";
}

export function buildBotMiniAppExpiryLabel(launch: BotWebAppLaunch) {
  return `Session expires ${new Date(launch.expiresAt).toLocaleTimeString()}`;
}
