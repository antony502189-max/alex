import type { FeatureProfile } from "../types";

export type ClientFeatureFlags = Pick<
  FeatureProfile,
  "stories" | "bots" | "calls"
>;

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

export const defaultFeatureProfile: FeatureProfile = {
  productProfile: "core-consumer-mvp",
  stories: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_STORIES, false),
  bots: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_BOTS, false),
  calls: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_CALLS, true),
  directCalls: readBooleanFlag(env.EXPO_PUBLIC_FEATURE_CALLS, true),
  groupCalls: false,
  callJoinLinks: false,
  callComments: false,
  callReactions: false,
  callModeration: false,
  callScreenSharing: false,
  callHandRaise: false,
  callRecording: false,
  secretChats: false,
  adminCompliance: false,
  lawfulDirectExport: false,
  botApiFull: false,
  business: false,
  payments: false,
  premium: false,
  monetization: false,
  translations: false
};

export const defaultClientFeatureFlags: ClientFeatureFlags = {
  stories: defaultFeatureProfile.stories,
  bots: defaultFeatureProfile.bots,
  calls: defaultFeatureProfile.calls
};

export function applyConsumerFeatureProfilePolicy(
  profile: FeatureProfile | null | undefined
): FeatureProfile | null {
  if (!profile) {
    return null;
  }

  if (!profile.secretChats) {
    return profile;
  }

  return {
    ...profile,
    secretChats: false
  };
}
