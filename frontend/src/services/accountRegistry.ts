import * as SecureStore from "expo-secure-store";
import {
  applyConsumerFeatureProfilePolicy,
  defaultFeatureProfile
} from "../config/featureFlags";
import {
  normalizeAppearanceSettings,
  normalizeChatListState,
  normalizeDataStorageSettings,
  normalizeDisclosureState,
  normalizeNotificationSettings
} from "../config/localSettings";
import type { AccountRegistry, FeatureProfile, LocalAccount } from "../types";

const STORAGE_KEY = "alex.account-registry.v1";

type PersistedRegistry = {
  activeAccountId: string | null;
  accounts: LocalAccount[];
};

function normalizeFeatureProfile(candidate: unknown): FeatureProfile | null {
  if (!candidate || typeof candidate !== "object") {
    return null;
  }

  const value = candidate as Partial<FeatureProfile>;
  if (typeof value.productProfile !== "string" || !value.productProfile.trim()) {
    return null;
  }

  const expectedBooleanKeys: Array<keyof Omit<FeatureProfile, "productProfile">> = [
    "stories",
    "bots",
    "calls",
    "directCalls",
    "groupCalls",
    "callJoinLinks",
    "callComments",
    "callReactions",
    "callModeration",
    "callScreenSharing",
    "callHandRaise",
    "callRecording",
    "secretChats",
    "adminCompliance",
    "lawfulDirectExport",
    "botApiFull",
    "business",
    "payments",
    "premium",
    "monetization",
    "translations"
  ];

  for (const key of expectedBooleanKeys) {
    if (typeof value[key] !== "boolean") {
      return null;
    }
  }

  return applyConsumerFeatureProfilePolicy({
    ...defaultFeatureProfile,
    ...value
  });
}

function normalizeAccount(candidate: unknown): LocalAccount | null {
  if (!candidate || typeof candidate !== "object") {
    return null;
  }

  const value = candidate as Partial<LocalAccount>;
  if (
    typeof value.accountId !== "string" ||
    !value.accountId.trim() ||
    !value.session ||
    typeof value.lastActivatedAt !== "string"
  ) {
    return null;
  }

  return {
    accountId: value.accountId,
    session: value.session,
    featureProfile: normalizeFeatureProfile(value.featureProfile),
    notificationSettings: normalizeNotificationSettings(value.notificationSettings),
    dataStorageSettings: normalizeDataStorageSettings(value.dataStorageSettings),
    appearanceSettings: normalizeAppearanceSettings(value.appearanceSettings),
    chatListState: normalizeChatListState(value.chatListState),
    disclosureState: normalizeDisclosureState(value.disclosureState),
    lastActivatedAt: value.lastActivatedAt
  };
}

async function readRawRegistry(): Promise<PersistedRegistry | null> {
  const value = await SecureStore.getItemAsync(STORAGE_KEY);
  if (!value) {
    return null;
  }

  try {
    const parsed = JSON.parse(value) as {
      activeAccountId?: unknown;
      accounts?: unknown[];
    };

    const accounts = Array.isArray(parsed.accounts)
      ? parsed.accounts
          .map((account) => normalizeAccount(account))
          .filter((account): account is LocalAccount => Boolean(account))
      : [];

    return {
      activeAccountId:
        typeof parsed.activeAccountId === "string" ? parsed.activeAccountId : null,
      accounts
    };
  } catch {
    await SecureStore.deleteItemAsync(STORAGE_KEY).catch(() => undefined);
    return null;
  }
}

export const accountRegistry = {
  async load(): Promise<AccountRegistry> {
    const registry = await readRawRegistry();
    if (!registry) {
      return {
        activeAccountId: null,
        accounts: []
      };
    }

    const activeAccountId =
      registry.activeAccountId &&
      registry.accounts.some((account) => account.accountId === registry.activeAccountId)
        ? registry.activeAccountId
        : registry.accounts[0]?.accountId ?? null;

    return {
      activeAccountId,
      accounts: [...registry.accounts].sort((left, right) =>
        right.lastActivatedAt.localeCompare(left.lastActivatedAt)
      )
    };
  },

  async save(registry: AccountRegistry) {
    await SecureStore.setItemAsync(
      STORAGE_KEY,
      JSON.stringify({
        activeAccountId: registry.activeAccountId,
        accounts: registry.accounts
      } satisfies PersistedRegistry)
    );
  },

  async clear() {
    await SecureStore.deleteItemAsync(STORAGE_KEY);
  }
};
