import type {
  AppearanceSettings,
  ChatListFilter,
  ChatListState,
  DataStorageSettings,
  DisclosureState,
  NotificationSettings
} from "../types";

export const defaultNotificationSettings: NotificationSettings = {
  privateChatsEnabled: true,
  groupChatsEnabled: true,
  channelChatsEnabled: true,
  storyNotificationsEnabled: true,
  includeMessagePreview: true,
  reactionNotificationsEnabled: true,
  inAppSoundsEnabled: true,
  vibrateEnabled: true
};

export const defaultDataStorageSettings: DataStorageSettings = {
  autoDownloadOnCellular: false,
  autoDownloadOnWifi: true,
  autoplayGifs: true,
  autoplayVideos: false,
  saveIncomingPhotosToGallery: false,
  useLessDataForCalls: true,
  keepDownloadedMediaDays: 30
};

export const defaultAppearanceSettings: AppearanceSettings = {
  compactChatList: false,
  showChatAvatars: true,
  showLinkPreviews: true,
  enterSendsMessage: false,
  largeEmojiEnabled: true,
  animatedStickerLoops: true
};

export const defaultChatListState: ChatListState = {
  searchQuery: "",
  selectedFilter: "ALL",
  selectedFolderId: null
};

export const defaultDisclosureState: DisclosureState = {
  privacyAcknowledgedAt: null
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object";
}

function readBoolean(value: unknown, fallback: boolean) {
  return typeof value === "boolean" ? value : fallback;
}

function readString(value: unknown, fallback: string) {
  return typeof value === "string" ? value : fallback;
}

function readNullableString(value: unknown, fallback: string | null) {
  return value == null ? fallback : typeof value === "string" ? value : fallback;
}

function readNumber(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function normalizeChatListFilter(value: unknown): ChatListFilter {
  return ["ALL", "UNREAD", "PEOPLE", "GROUPS", "CHANNELS", "BOTS"].includes(
    String(value)
  )
    ? (value as ChatListFilter)
    : "ALL";
}

export function normalizeNotificationSettings(candidate: unknown): NotificationSettings {
  if (!isRecord(candidate)) {
    return defaultNotificationSettings;
  }

  return {
    privateChatsEnabled: readBoolean(
      candidate.privateChatsEnabled,
      defaultNotificationSettings.privateChatsEnabled
    ),
    groupChatsEnabled: readBoolean(
      candidate.groupChatsEnabled,
      defaultNotificationSettings.groupChatsEnabled
    ),
    channelChatsEnabled: readBoolean(
      candidate.channelChatsEnabled,
      defaultNotificationSettings.channelChatsEnabled
    ),
    storyNotificationsEnabled: readBoolean(
      candidate.storyNotificationsEnabled,
      defaultNotificationSettings.storyNotificationsEnabled
    ),
    includeMessagePreview: readBoolean(
      candidate.includeMessagePreview,
      defaultNotificationSettings.includeMessagePreview
    ),
    reactionNotificationsEnabled: readBoolean(
      candidate.reactionNotificationsEnabled,
      defaultNotificationSettings.reactionNotificationsEnabled
    ),
    inAppSoundsEnabled: readBoolean(
      candidate.inAppSoundsEnabled,
      defaultNotificationSettings.inAppSoundsEnabled
    ),
    vibrateEnabled: readBoolean(
      candidate.vibrateEnabled,
      defaultNotificationSettings.vibrateEnabled
    )
  };
}

export function normalizeDataStorageSettings(candidate: unknown): DataStorageSettings {
  if (!isRecord(candidate)) {
    return defaultDataStorageSettings;
  }

  return {
    autoDownloadOnCellular: readBoolean(
      candidate.autoDownloadOnCellular,
      defaultDataStorageSettings.autoDownloadOnCellular
    ),
    autoDownloadOnWifi: readBoolean(
      candidate.autoDownloadOnWifi,
      defaultDataStorageSettings.autoDownloadOnWifi
    ),
    autoplayGifs: readBoolean(
      candidate.autoplayGifs,
      defaultDataStorageSettings.autoplayGifs
    ),
    autoplayVideos: readBoolean(
      candidate.autoplayVideos,
      defaultDataStorageSettings.autoplayVideos
    ),
    saveIncomingPhotosToGallery: readBoolean(
      candidate.saveIncomingPhotosToGallery,
      defaultDataStorageSettings.saveIncomingPhotosToGallery
    ),
    useLessDataForCalls: readBoolean(
      candidate.useLessDataForCalls,
      defaultDataStorageSettings.useLessDataForCalls
    ),
    keepDownloadedMediaDays: readNumber(
      candidate.keepDownloadedMediaDays,
      defaultDataStorageSettings.keepDownloadedMediaDays
    )
  };
}

export function normalizeAppearanceSettings(candidate: unknown): AppearanceSettings {
  if (!isRecord(candidate)) {
    return defaultAppearanceSettings;
  }

  return {
    compactChatList: readBoolean(
      candidate.compactChatList,
      defaultAppearanceSettings.compactChatList
    ),
    showChatAvatars: readBoolean(
      candidate.showChatAvatars,
      defaultAppearanceSettings.showChatAvatars
    ),
    showLinkPreviews: readBoolean(
      candidate.showLinkPreviews,
      defaultAppearanceSettings.showLinkPreviews
    ),
    enterSendsMessage: readBoolean(
      candidate.enterSendsMessage,
      defaultAppearanceSettings.enterSendsMessage
    ),
    largeEmojiEnabled: readBoolean(
      candidate.largeEmojiEnabled,
      defaultAppearanceSettings.largeEmojiEnabled
    ),
    animatedStickerLoops: readBoolean(
      candidate.animatedStickerLoops,
      defaultAppearanceSettings.animatedStickerLoops
    )
  };
}

export function normalizeChatListState(candidate: unknown): ChatListState {
  if (!isRecord(candidate)) {
    return defaultChatListState;
  }

  return {
    searchQuery: readString(candidate.searchQuery, defaultChatListState.searchQuery),
    selectedFilter: normalizeChatListFilter(candidate.selectedFilter),
    selectedFolderId: readNullableString(
      candidate.selectedFolderId,
      defaultChatListState.selectedFolderId
    )
  };
}

export function normalizeDisclosureState(candidate: unknown): DisclosureState {
  if (!isRecord(candidate)) {
    return defaultDisclosureState;
  }

  return {
    privacyAcknowledgedAt: readNullableString(
      candidate.privacyAcknowledgedAt,
      defaultDisclosureState.privacyAcknowledgedAt
    )
  };
}
