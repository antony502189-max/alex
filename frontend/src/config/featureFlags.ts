export type ClientFeatureFlags = {
  stories: boolean;
  bots: boolean;
  calls: boolean;
  secretChats: boolean;
};

const env =
  (globalThis as { process?: { env?: Record<string, string | undefined> } })
    .process?.env ?? {};

function readBooleanFlag(value: string | undefined, defaultValue: boolean) {
  if (typeof value !== "string") {
    return defaultValue;
  }

  const normalized = value.trim().toLowerCase();
  if (!normalized) {
    return defaultValue;
  }

  if (["1", "true", "yes", "on", "enabled"].includes(normalized)) {
    return true;
  }

  if (["0", "false", "no", "off", "disabled"].includes(normalized)) {
    return false;
  }

  return defaultValue;
}

export const defaultClientFeatureFlags: ClientFeatureFlags = {
  stories: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_STORIES, true),
  bots: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_BOTS, true),
  calls: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_CALLS, true),
  secretChats: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_SECRET_CHATS, true)
};
