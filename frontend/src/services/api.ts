import { API_BASE_URL } from "../config/env";
import * as FileSystem from "expo-file-system/legacy";
import { useAppStore } from "../store/useAppStore";
import type {
  AccountDeletionJob,
  AccountExportJob,
  AttachmentAccess,
  AttachmentUploadSession,
  AuthSecurityEvent,
  AuthFlowResult,
  AuthSession,
  BlockedUser,
  BotCommand,
  BotSummary,
  BotWebAppLaunch,
  CallHistoryEntry,
  CallInboxEvent,
  CallJoinLink,
  CallRtcConfig,
  CallSession,
  ChatAnalytics,
  ChatBan,
  ChatFolder,
  ChatInviteLink,
  ChatJoinRequest,
  ChatMember,
  ChatMessage,
  ChatReadEvent,
  ChatReportReceipt,
  ChatSummary,
  ClearHistoryResult,
  Contact,
  DeveloperBot,
  FeatureProfile,
  ForumTopic,
  GeneratedQrLogin,
  GlobalSearchResponse,
  ImportContactsResult,
  ImportedPhoneContact,
  InlineBotResult,
  IssuedBotToken,
  JoinChatResult,
  LanguagePreferences,
  LeaveChatResult,
  LoginCodeChallenge,
  MessageAttachment,
  MessageContactCard,
  MessageLiveLocation,
  MessageLocation,
  MessageReportReceipt,
  MessageTextEntity,
  PasskeyCredential,
  PasskeyLoginOptions,
  PasskeyRegistrationOptions,
  PhoneChangeChallenge,
  PinMessageEvent,
  PinnedMessageHistoryEntry,
  PrivacyExceptions,
  PublicChatDiscovery,
  QrLoginChallenge,
  QrLoginStatus,
  ScheduledMessage,
  SearchMessagesResponse,
  SecretAttachmentUpload,
  SecretChatAttachment,
  SecretChatMessage,
  SecretChatReadEvent,
  SecretChatScreenshotEvent,
  SecretChatSummary,
  StickerPack,
  Story,
  StoryFeedItem,
  StoryViewer,
  SyncEvent,
  TwoFactorStatus,
  TypingEvent,
  UserPresenceStatus,
  UserProfile,
  UserReportReceipt,
  UserSearchResult,
  UserSession
} from "../types";

type LoginPayload = {
  phoneNumber: string;
  displayName?: string;
  deviceName?: string;
  platform?: string;
  appVersion?: string;
};

export type SendMessagePayload = {
  chatId?: string;
  recipientUserId?: string;
  topicId?: string;
  replyToMessageId?: string;
  text?: string;
  caption?: string;
  messageType?: "TEXT" | "LOCATION" | "LIVE_LOCATION" | "CONTACT_CARD";
  silent?: boolean;
  entities?: MessageTextEntity[];
  location?: MessageLocation;
  liveLocation?: MessageLiveLocation;
  contactCard?: MessageContactCard;
  attachmentIds?: string[];
  stickerId?: string;
  clientMessageId?: string;
};

export type ScheduleMessagePayload = SendMessagePayload & {
  scheduledAt: string;
};

export type DeferredMessagePayload = SendMessagePayload & {
  scheduledAt?: string;
  deliveryMode?: "SCHEDULED" | "WHEN_ONLINE";
};

export type CreatePollPayload = {
  chatId?: string;
  recipientUserId?: string;
  topicId?: string;
  replyToMessageId?: string;
  question: string;
  options: string[];
  multipleChoice: boolean;
  clientMessageId?: string;
};

export type ForwardMessagePayload = {
  sourceMessageId: string;
  chatId?: string;
  recipientUserId?: string;
  topicId?: string;
  replyToMessageId?: string;
  clientMessageId?: string;
};

export type SendInlineBotResultPayload = {
  chatId?: string;
  recipientUserId?: string;
  topicId?: string;
  replyToMessageId?: string;
  botUsername: string;
  resultId: string;
  query?: string;
  clientMessageId?: string;
};

export type OutboxOperation =
  | {
      kind: "SEND_MESSAGE";
      request: SendMessagePayload;
    }
  | {
      kind: "CREATE_POLL_MESSAGE";
      request: CreatePollPayload;
    }
  | {
      kind: "FORWARD_MESSAGE";
      request: ForwardMessagePayload;
    }
  | {
      kind: "SEND_INLINE_BOT_RESULT";
      request: SendInlineBotResultPayload;
    }
  | {
      kind: "SCHEDULE_MESSAGE";
      request: ScheduleMessagePayload;
    };

type CreateGroupPayload = {
  title: string;
  about?: string;
  autoDeleteSeconds?: number;
  forumEnabled?: boolean;
  joinRequiresApproval?: boolean;
  memberIds: string[];
};

type CreateChannelPayload = {
  title: string;
  about?: string;
  autoDeleteSeconds?: number;
  joinRequiresApproval?: boolean;
  subscriberIds: string[];
};

const UNAUTHORIZED_STATUS = 401;
const RESUMABLE_ATTACHMENT_UPLOAD_THRESHOLD_BYTES = 4 * 1024 * 1024;

type UploadAttachmentFile = {
  uri: string;
  name: string;
  type?: string;
  kind?: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
  durationMs?: number;
  width?: number;
  height?: number;
  waveform?: number[];
};

type UploadAttachmentOptions = {
  existingSessionId?: string | null;
  onSessionId?: (sessionId: string) => void;
  onProgress?: (progress: {
    transferredBytes: number;
    totalBytes: number;
  }) => void;
};

let refreshSessionPromise: Promise<AuthSession | null> | null = null;

class ApiError extends Error {
  readonly status: number;

  readonly body: string;

  constructor(status: number, body: string) {
    super(body || `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

type RequestOptions = {
  allowTokenRefresh?: boolean;
  contentType?: string | null;
};

function getLatestSessionToken(fallbackToken?: string) {
  return useAppStore.getState().session?.token ?? fallbackToken;
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const raw = await response.text();
  if (!raw) {
    return undefined as T;
  }

  return JSON.parse(raw) as T;
}

async function parseResponseWithMetadata<T>(
  response: Response
): Promise<{ data: T; response: Response }> {
  const data = await parseResponse<T>(response);
  return { data, response };
}

async function refreshAccessToken(expiredToken: string): Promise<AuthSession | null> {
  const store = useAppStore.getState();
  const currentSession = store.session;
  if (!currentSession) {
    return null;
  }
  if (currentSession.token !== expiredToken) {
    return currentSession;
  }
  if (!currentSession.refreshToken) {
    store.logout();
    return null;
  }

  if (!refreshSessionPromise) {
    const sessionBeforeRefresh = currentSession;
    refreshSessionPromise = request<AuthSession>(
      "/auth/refresh",
      {
        method: "POST",
        body: JSON.stringify({ refreshToken: sessionBeforeRefresh.refreshToken })
      },
      undefined,
      { allowTokenRefresh: false }
    )
      .then((nextSession) => {
        const latestSession = useAppStore.getState().session;
        if (!latestSession || latestSession.sessionId !== sessionBeforeRefresh.sessionId) {
          return null;
        }
        useAppStore.getState().setSession(nextSession);
        return nextSession;
      })
      .catch((error: unknown) => {
        if (
          error instanceof ApiError &&
          (error.status === UNAUTHORIZED_STATUS || error.status === 403)
        ) {
          const latestSession = useAppStore.getState().session;
          if (latestSession && latestSession.sessionId === sessionBeforeRefresh.sessionId) {
            useAppStore.getState().logout();
          }
        }
        throw error;
      })
      .finally(() => {
        refreshSessionPromise = null;
      });
  }

  return refreshSessionPromise;
}

async function resolveRetryToken(token: string): Promise<string | null> {
  const currentSession = useAppStore.getState().session;
  if (!currentSession) {
    return null;
  }
  if (currentSession.token !== token) {
    return currentSession.token;
  }
  const refreshedSession = await refreshAccessToken(token);
  return refreshedSession?.token ?? null;
}

async function performAuthorizedRequest<T>(
  path: string,
  createInit: (token?: string) => RequestInit,
  token?: string,
  options: RequestOptions = {}
): Promise<T> {
  const allowTokenRefresh = options.allowTokenRefresh ?? true;
  let response = await fetch(`${API_BASE_URL}${path}`, createInit(token));

  if (response.status === UNAUTHORIZED_STATUS && token && allowTokenRefresh) {
    const retryToken = await resolveRetryToken(token);
    if (retryToken) {
      response = await fetch(`${API_BASE_URL}${path}`, createInit(retryToken));
    }
  }

  return parseResponse<T>(response);
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  token?: string,
  options: RequestOptions = {}
): Promise<T> {
  return performAuthorizedRequest<T>(
    path,
    (activeToken) => {
      const headers = new Headers(init.headers);
      if (options.contentType !== null && !headers.has("Content-Type")) {
        headers.set("Content-Type", options.contentType ?? "application/json");
      }

      if (activeToken) {
        headers.set("Authorization", `Bearer ${activeToken}`);
      } else {
        headers.delete("Authorization");
      }

      return {
        ...init,
        headers
      };
    },
    token,
    options
  );
}

async function requestWithMetadata<T>(
  path: string,
  init: RequestInit = {},
  token?: string,
  options: RequestOptions = {}
): Promise<{ data: T; response: Response }> {
  const allowTokenRefresh = options.allowTokenRefresh ?? true;
  const createInit = (activeToken?: string) => {
    const headers = new Headers(init.headers);
    if (options.contentType !== null && !headers.has("Content-Type")) {
      headers.set("Content-Type", options.contentType ?? "application/json");
    }

    if (activeToken) {
      headers.set("Authorization", `Bearer ${activeToken}`);
    } else {
      headers.delete("Authorization");
    }

    return {
      ...init,
      headers
    };
  };

  let response = await fetch(`${API_BASE_URL}${path}`, createInit(token));
  if (response.status === UNAUTHORIZED_STATUS && token && allowTokenRefresh) {
    const retryToken = await resolveRetryToken(token);
    if (retryToken) {
      response = await fetch(`${API_BASE_URL}${path}`, createInit(retryToken));
    }
  }

  return parseResponseWithMetadata<T>(response);
}

function parseOptionalNumberHeader(headers: Headers, name: string) {
  const raw = headers.get(name);
  if (raw == null) {
    return null;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseBooleanHeader(headers: Headers, name: string) {
  return headers.get(name)?.trim().toLowerCase() === "true";
}

async function uploadMultipart<T>(
  path: string,
  buildFormData: () => FormData,
  token: string
): Promise<T> {
  return performAuthorizedRequest<T>(
    path,
    (activeToken) => ({
      method: "POST",
      headers: activeToken
        ? {
            Authorization: `Bearer ${activeToken}`
          }
        : undefined,
      body: buildFormData()
    }),
    token
  );
}

async function parseUploadTaskResult<T>(
  result: FileSystem.FileSystemUploadResult | undefined | null
): Promise<T> {
  if (!result) {
    throw new Error("Upload was cancelled");
  }

  if (result.status < 200 || result.status >= 300) {
    throw new ApiError(result.status, result.body);
  }

  if (!result.body) {
    return undefined as T;
  }

  return JSON.parse(result.body) as T;
}

function buildAttachmentUploadParameters(
  chatId: string,
  file: UploadAttachmentFile
) {
  const parameters: Record<string, string> = {
    chatId
  };
  if (file.kind) {
    parameters.kind = file.kind;
  }
  if (typeof file.durationMs === "number") {
    parameters.durationMs = String(Math.max(1, Math.round(file.durationMs)));
  }
  if (typeof file.width === "number" && Number.isFinite(file.width) && file.width > 0) {
    parameters.width = String(Math.round(file.width));
  }
  if (typeof file.height === "number" && Number.isFinite(file.height) && file.height > 0) {
    parameters.height = String(Math.round(file.height));
  }
  const normalizedWaveform = normalizeWaveformSamples(file.waveform);
  if (normalizedWaveform && normalizedWaveform.length > 0) {
    parameters.waveform = normalizedWaveform.join(",");
  }
  return parameters;
}

async function uploadAttachmentMultipartWithProgress(
  token: string,
  chatId: string,
  file: UploadAttachmentFile,
  options: UploadAttachmentOptions
): Promise<MessageAttachment> {
  const activeToken = getLatestSessionToken(token) ?? token;
  const uploadTask = FileSystem.createUploadTask(
    `${API_BASE_URL}/attachments/upload`,
    file.uri,
    {
      uploadType: FileSystem.FileSystemUploadType.MULTIPART,
      fieldName: "file",
      mimeType: file.type ?? "application/octet-stream",
      parameters: buildAttachmentUploadParameters(chatId, file),
      headers: activeToken
        ? {
            Authorization: `Bearer ${activeToken}`
          }
        : undefined,
      httpMethod: "POST"
    },
    (progressEvent) => {
      options.onProgress?.({
        transferredBytes: progressEvent.totalBytesSent,
        totalBytes: progressEvent.totalBytesExpectedToSend
      });
    }
  );

  try {
    return await parseUploadTaskResult<MessageAttachment>(await uploadTask.uploadAsync());
  } catch (error) {
    if (error instanceof ApiError && error.status === UNAUTHORIZED_STATUS) {
      const retryToken = await resolveRetryToken(activeToken);
      if (retryToken) {
        const retryTask = FileSystem.createUploadTask(
          `${API_BASE_URL}/attachments/upload`,
          file.uri,
          {
            uploadType: FileSystem.FileSystemUploadType.MULTIPART,
            fieldName: "file",
            mimeType: file.type ?? "application/octet-stream",
            parameters: buildAttachmentUploadParameters(chatId, file),
            headers: {
              Authorization: `Bearer ${retryToken}`
            },
            httpMethod: "POST"
          },
          (progressEvent) => {
            options.onProgress?.({
              transferredBytes: progressEvent.totalBytesSent,
              totalBytes: progressEvent.totalBytesExpectedToSend
            });
          }
        );
        return parseUploadTaskResult<MessageAttachment>(await retryTask.uploadAsync());
      }
    }
    throw error;
  }
}

function normalizeWaveformSamples(waveform?: number[]) {
  if (!Array.isArray(waveform) || waveform.length === 0) {
    return undefined;
  }

  return waveform
    .slice(0, 96)
    .map((sample) => Math.max(0, Math.min(100, Math.round(sample))));
}

async function uploadAttachmentResumable(
  token: string,
  chatId: string,
  file: UploadAttachmentFile,
  totalSizeBytes: number,
  options: UploadAttachmentOptions = {}
): Promise<MessageAttachment> {
  const normalizedWaveform = normalizeWaveformSamples(file.waveform);
  const existingSession = options.existingSessionId
    ? await request<AttachmentUploadSession>(
        `/attachments/upload-sessions/${options.existingSessionId}`,
        { method: "GET" },
        token
      ).catch(() => null)
    : null;

  let session: AttachmentUploadSession;
  if (existingSession && existingSession.status === "ACTIVE") {
    session = existingSession;
  } else {
    session = await request<AttachmentUploadSession>(
      "/attachments/upload-sessions",
      {
        method: "POST",
        body: JSON.stringify({
          chatId,
          originalFileName: file.name,
          contentType: file.type ?? "application/octet-stream",
          kind: file.kind,
          totalSizeBytes,
          durationMs: typeof file.durationMs === "number" ? Math.max(1, Math.round(file.durationMs)) : undefined,
          width: typeof file.width === "number" && Number.isFinite(file.width) && file.width > 0
            ? Math.round(file.width)
            : undefined,
          height: typeof file.height === "number" && Number.isFinite(file.height) && file.height > 0
            ? Math.round(file.height)
            : undefined,
          waveform: normalizedWaveform
        })
      },
      token
    );
  }

  options.onSessionId?.(session.uploadSessionId);
  let uploadedBytes = session.uploadedBytes;
  options.onProgress?.({
    transferredBytes: uploadedBytes,
    totalBytes: totalSizeBytes
  });

  while (uploadedBytes < totalSizeBytes) {
    const chunkLength = Math.min(session.chunkSizeBytes, totalSizeBytes - uploadedBytes);
    const base64Chunk = await FileSystem.readAsStringAsync(file.uri, {
      encoding: FileSystem.EncodingType.Base64,
      position: uploadedBytes,
      length: chunkLength
    });

    const nextSession: AttachmentUploadSession = await request<AttachmentUploadSession>(
      `/attachments/upload-sessions/${session.uploadSessionId}/chunks`,
      {
        method: "POST",
        body: JSON.stringify({
          offset: uploadedBytes,
          base64Chunk
        })
      },
      token
    );

    uploadedBytes = nextSession.uploadedBytes;
    session = nextSession;
    options.onProgress?.({
      transferredBytes: uploadedBytes,
      totalBytes: totalSizeBytes
    });
  }

  return request<MessageAttachment>(
    `/attachments/upload-sessions/${session.uploadSessionId}/complete`,
    { method: "POST" },
    token
  );
}

export const api = {
  getFeatureProfile(token: string) {
    return request<FeatureProfile>("/features/profile", { method: "GET" }, token);
  },

  async getSyncEvents(
    token: string,
    cursor?: number | null,
    limit = 100,
    includeLegacy = false
  ) {
    const params = new URLSearchParams();
    if (typeof cursor === "number" && Number.isFinite(cursor)) {
      params.set("cursor", String(cursor));
    }
    params.set("limit", String(limit));
    params.set("includeLegacy", String(includeLegacy));

    const { data, response } = await requestWithMetadata<SyncEvent[]>(
      `/sync/events?${params.toString()}`,
      { method: "GET" },
      token
    );

    return {
      events: data,
      eventContract: response.headers.get("X-Sync-Event-Contract"),
      hasMore: parseBooleanHeader(response.headers, "X-Sync-Has-More"),
      includeLegacy: parseBooleanHeader(response.headers, "X-Sync-Include-Legacy"),
      limit: parseOptionalNumberHeader(response.headers, "X-Sync-Limit") ?? limit,
      nextCursor: parseOptionalNumberHeader(response.headers, "X-Sync-Next-Cursor"),
      resetCursor: parseOptionalNumberHeader(response.headers, "X-Sync-Reset-Cursor"),
      retentionSeconds: parseOptionalNumberHeader(response.headers, "X-Sync-Retention-Seconds"),
      staleCursor: parseBooleanHeader(response.headers, "X-Sync-Cursor-Stale")
    };
  },

  requestLoginCode(payload: LoginPayload) {
    return request<LoginCodeChallenge>("/auth/request-code", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  verifyLoginCode(payload: { challengeId: string; code: string }) {
    return request<AuthFlowResult>("/auth/verify-code", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  verifyTwoFactor(payload: { challengeId: string; password: string; trustSession?: boolean }) {
    return request<AuthFlowResult>("/auth/2fa/verify", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  generateQrLogin(token: string) {
    return request<GeneratedQrLogin>("/auth/qr/generate", { method: "POST" }, token);
  },

  getQrLoginChallenges(token: string) {
    return request<QrLoginChallenge[]>("/auth/qr/pending", { method: "GET" }, token);
  },

  approveQrLogin(token: string, challengeId: string) {
    return request<QrLoginChallenge>(`/auth/qr/${challengeId}/approve`, { method: "POST" }, token);
  },

  declineQrLogin(token: string, challengeId: string) {
    return request<QrLoginChallenge>(`/auth/qr/${challengeId}/decline`, { method: "POST" }, token);
  },

  bindQrLogin(payload: { qrToken: string; deviceName?: string; platform?: string; appVersion?: string }) {
    return request<QrLoginStatus>("/auth/qr/bind", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  pollQrLogin(payload: { qrToken: string }) {
    return request<QrLoginStatus>("/auth/qr/poll", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  refreshSession(payload: { refreshToken: string }) {
    return request<AuthSession>("/auth/refresh", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  login(payload: LoginPayload) {
    return request<AuthFlowResult>("/auth/login", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  requestPasskeyRegistrationOptions(token: string) {
    return request<PasskeyRegistrationOptions>(
      "/auth/passkeys/register/options",
      {
        method: "POST"
      },
      token
    );
  },

  verifyPasskeyRegistration(
    token: string,
    payload: {
      challengeId: string;
      challenge: string;
      credentialId: string;
      publicKey: string;
      transports?: string;
      label?: string;
      signCount?: number | null;
    }
  ) {
    return request<PasskeyCredential>(
      "/auth/passkeys/register/verify",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  requestPasskeyLoginOptions(payload: {
    phoneNumber: string;
    deviceName?: string;
    platform?: string;
    appVersion?: string;
  }) {
    return request<PasskeyLoginOptions>("/auth/passkeys/login/options", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  verifyPasskeyLogin(payload: {
    challengeId: string;
    challenge: string;
    credentialId: string;
    signCount?: number | null;
    deviceName?: string;
    platform?: string;
    appVersion?: string;
  }) {
    return request<AuthFlowResult>("/auth/passkeys/login/verify", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  requestPhoneChange(token: string, payload: { newPhoneNumber: string }) {
    return request<PhoneChangeChallenge>(
      "/auth/change-phone/request-code",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  verifyPhoneChange(token: string, payload: { challengeId: string; code: string }) {
    return request<AuthFlowResult>(
      "/auth/change-phone/verify",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getTwoFactorStatus(token: string) {
    return request<TwoFactorStatus>("/auth/2fa/status", { method: "GET" }, token);
  },

  enableTwoFactor(
    token: string,
    payload: { password: string; hint?: string }
  ) {
    return request<TwoFactorStatus>(
      "/auth/2fa/enable",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  disableTwoFactor(token: string, payload: { password: string }) {
    return request<TwoFactorStatus>(
      "/auth/2fa/disable",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getSessions(token: string) {
    return request<UserSession[]>("/auth/sessions", { method: "GET" }, token);
  },

  revokeSession(token: string, sessionId: string) {
    return request<void>(`/auth/sessions/${sessionId}`, { method: "DELETE" }, token);
  },

  revokeOtherSessions(token: string) {
    return request<void>("/auth/sessions/others", { method: "DELETE" }, token);
  },

  updateCurrentPushToken(
    token: string,
    payload: { provider: "EXPO"; pushToken: string }
  ) {
    return request<UserSession>(
      "/auth/sessions/current/push-token",
      {
        method: "PUT",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  clearCurrentPushToken(token: string) {
    return request<UserSession>(
      "/auth/sessions/current/push-token",
      { method: "DELETE" },
      token
    );
  },

  getSecurityEvents(token: string) {
    return request<AuthSecurityEvent[]>("/auth/security/events", { method: "GET" }, token);
  },

  exportAccount(
    token: string,
    payload?: {
      format?: "JSON";
      includeAttachmentsMetadata?: boolean;
      fromInclusive?: string;
      toExclusive?: string;
    }
  ) {
    return request<AccountExportJob>(
      "/account/export",
      {
        method: "POST",
        body: JSON.stringify(payload ?? {})
      },
      token
    );
  },

  scheduleAccountDeletion(
    token: string,
    payload?: {
      reason?: string;
      delayDays?: number;
    }
  ) {
    return request<AccountDeletionJob>(
      "/account/delete",
      {
        method: "POST",
        body: JSON.stringify(payload ?? {})
      },
      token
    );
  },

  getChats(token: string) {
    return request<ChatSummary[]>("/chats", { method: "GET" }, token);
  },

  getStickerPacks(token: string) {
    return request<StickerPack[]>("/stickers/packs", { method: "GET" }, token);
  },

  getStoriesFeed(token: string) {
    return request<StoryFeedItem[]>("/stories/feed", { method: "GET" }, token);
  },

  getStoryArchive(token: string) {
    return request<Story[]>("/stories/archive", { method: "GET" }, token);
  },

  getBots(token: string) {
    return request<BotSummary[]>("/bots", { method: "GET" }, token);
  },

  getDeveloperBots(token: string) {
    return request<DeveloperBot[]>("/developer/bots", { method: "GET" }, token);
  },

  createDeveloperBot(
    token: string,
    payload: {
      displayName: string;
      username: string;
      description?: string;
      about?: string;
      supportsInline: boolean;
      webAppUrl?: string;
    }
  ) {
    return request<IssuedBotToken>(
      "/developer/bots",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  updateDeveloperBot(
    token: string,
    botUserId: string,
    payload: {
      displayName?: string;
      username?: string;
      description?: string;
      about?: string;
      supportsInline?: boolean;
      webAppUrl?: string;
    }
  ) {
    return request<DeveloperBot>(
      `/developer/bots/${botUserId}`,
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  rotateDeveloperBotToken(token: string, botUserId: string) {
    return request<IssuedBotToken>(
      `/developer/bots/${botUserId}/token`,
      { method: "POST" },
      token
    );
  },

  updateDeveloperBotWebhook(
    token: string,
    botUserId: string,
    payload: {
      webhookUrl: string;
      secretToken?: string;
    }
  ) {
    return request<DeveloperBot>(
      `/developer/bots/${botUserId}/webhook`,
      {
        method: "PUT",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  clearDeveloperBotWebhook(token: string, botUserId: string) {
    return request<DeveloperBot>(
      `/developer/bots/${botUserId}/webhook`,
      { method: "DELETE" },
      token
    );
  },

  getBotCommands(token: string, botUserId: string) {
    return request<BotCommand[]>(`/bots/${botUserId}/commands`, { method: "GET" }, token);
  },

  getInlineBotResults(token: string, botUsername: string, query?: string) {
    const encodedUsername = encodeURIComponent(botUsername);
    const encodedQuery = encodeURIComponent(query ?? "");
    return request<InlineBotResult[]>(
      `/bots/inline/${encodedUsername}?query=${encodedQuery}`,
      { method: "GET" },
      token
    );
  },

  getBotWebAppLaunch(
    token: string,
    botUserId: string,
    options?: { chatId?: string | null; startParameter?: string | null }
  ) {
    const query = new URLSearchParams();
    if (options?.chatId) {
      query.set("chatId", options.chatId);
    }
    if (options?.startParameter) {
      query.set("startParameter", options.startParameter);
    }
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return request<BotWebAppLaunch>(`/bots/${botUserId}/web-app-launch${suffix}`, { method: "GET" }, token);
  },

  createStory(
    token: string,
    payload: {
      text?: string | null;
      backgroundFrom: string;
      backgroundTo: string;
      textColor: string;
      audience?: "DEFAULT" | "EVERYBODY" | "CONTACTS" | "NOBODY" | "CLOSE_FRIENDS" | "CUSTOM";
      allowedViewerUserIds?: string[];
    }
  ) {
    return request<Story>("/stories", {
      method: "POST",
      body: JSON.stringify(payload)
    }, token);
  },

  async createStoryWithMedia(
    token: string,
    payload: {
      text?: string | null;
      backgroundFrom: string;
      backgroundTo: string;
      textColor: string;
      audience?: "DEFAULT" | "EVERYBODY" | "CONTACTS" | "NOBODY" | "CLOSE_FRIENDS" | "CUSTOM";
      allowedViewerUserIds?: string[];
      durationMs?: number | null;
      file: {
        uri: string;
        name: string;
        type?: string;
      };
    }
  ) {
    return uploadMultipart<Story>(
      "/stories",
      () => {
        const formData = new FormData();
        if (payload.text?.trim()) {
          formData.append("text", payload.text.trim());
        }
        formData.append("backgroundFrom", payload.backgroundFrom);
        formData.append("backgroundTo", payload.backgroundTo);
        formData.append("textColor", payload.textColor);
        if (payload.audience) {
          formData.append("audience", payload.audience);
        }
        for (const viewerUserId of payload.allowedViewerUserIds ?? []) {
          formData.append("allowedViewerUserIds", viewerUserId);
        }
        if (typeof payload.durationMs === "number" && Number.isFinite(payload.durationMs)) {
          formData.append("durationMs", String(Math.max(1, Math.round(payload.durationMs))));
        }
        formData.append("file", {
          uri: payload.file.uri,
          name: payload.file.name,
          type: payload.file.type ?? "application/octet-stream"
        } as unknown as Blob);
        return formData;
      },
      token
    );
  },

  markStoryViewed(token: string, storyId: string) {
    return request<Story>(`/stories/${storyId}/view`, { method: "POST" }, token);
  },

  deleteStory(token: string, storyId: string) {
    return request<void>(`/stories/${storyId}`, { method: "DELETE" }, token);
  },

  getStoryViewers(token: string, storyId: string) {
    return request<StoryViewer[]>(`/stories/${storyId}/viewers`, { method: "GET" }, token);
  },

  getArchivedChats(token: string) {
    return request<ChatSummary[]>("/chats?archived=true", { method: "GET" }, token);
  },

  createDirectChat(token: string, peerUserId: string) {
    return request<ChatSummary>(
      "/chats/direct",
      {
        method: "POST",
        body: JSON.stringify({ peerUserId })
      },
      token
    );
  },

  createGroupChat(token: string, payload: CreateGroupPayload) {
    return request<ChatSummary>(
      "/chats/group",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  createChannel(token: string, payload: CreateChannelPayload) {
    return request<ChatSummary>(
      "/chats/channel",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  createSavedMessages(token: string) {
    return request<ChatSummary>("/chats/saved", { method: "POST" }, token);
  },

  getActiveCalls(token: string) {
    return request<CallSession[]>("/calls/active", { method: "GET" }, token);
  },

  getRecentCalls(token: string, limit = 50) {
    return request<CallHistoryEntry[]>(`/calls/recent?limit=${limit}`, { method: "GET" }, token);
  },

  getCallRtcConfig(token: string) {
    return request<CallRtcConfig>("/calls/rtc-config", { method: "GET" }, token);
  },

  getCallLinks(token: string, chatId: string) {
    return request<CallJoinLink[]>(`/calls/links?chatId=${encodeURIComponent(chatId)}`, { method: "GET" }, token);
  },

  createCallLink(
    token: string,
    payload: { chatId: string; kind: "VOICE" | "VIDEO"; label?: string | null; expiresAt?: string | null }
  ) {
    return request<CallJoinLink>(
      "/calls/links",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  joinCallLink(token: string, rawToken: string) {
    const normalizedToken = rawToken.replace(/^alex:\/\/call\//, "").trim();
    return request<CallSession>(
      `/calls/links/${encodeURIComponent(normalizedToken)}/join`,
      { method: "POST" },
      token
    );
  },

  startCall(token: string, payload: { chatId: string; kind: "VOICE" | "VIDEO" }) {
    return request<CallSession>(
      "/calls",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  acceptCall(token: string, callId: string) {
    return request<CallSession>(`/calls/${callId}/accept`, { method: "POST" }, token);
  },

  declineCall(token: string, callId: string) {
    return request<CallSession>(`/calls/${callId}/decline`, { method: "POST" }, token);
  },

  leaveCall(token: string, callId: string) {
    return request<CallSession>(`/calls/${callId}/leave`, { method: "POST" }, token);
  },

  moderateCallParticipant(
    token: string,
    callId: string,
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      videoPublishingAllowed?: boolean;
      screenShareAllowed?: boolean;
      removeParticipant?: boolean;
    }
  ) {
    return request<CallSession>(
      `/calls/${callId}/participants/${userId}/moderation`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  startScreenShare(token: string, callId: string) {
    return request<CallSession>(`/calls/${callId}/screen-share/start`, { method: "POST" }, token);
  },

  stopScreenShare(token: string, callId: string) {
    return request<CallSession>(`/calls/${callId}/screen-share/stop`, { method: "POST" }, token);
  },

  sendCallSignal(
    token: string,
    callId: string,
    payload: { toUserId: string; signalType: string; payload: string }
  ) {
    return request<CallInboxEvent["signal"]>(
      `/calls/${callId}/signal`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getSecretChats(token: string) {
    return request<SecretChatSummary[]>("/secret-chats", { method: "GET" }, token);
  },

  createSecretChat(
    token: string,
    payload: { recipientUserId: string; initiatorPublicKey: string; autoDeleteSeconds?: number | null }
  ) {
    return request<SecretChatSummary>(
      "/secret-chats",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  sendInlineBotResult(token: string, payload: SendInlineBotResultPayload) {
    return request<ChatMessage>(
      "/messages/inline-bot-result",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  acceptSecretChat(
    token: string,
    secretChatId: string,
    payload: { recipientPublicKey: string; sharedKeyFingerprint: string }
  ) {
    return request<SecretChatSummary>(
      `/secret-chats/${secretChatId}/accept`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  declineSecretChat(token: string, secretChatId: string) {
    return request<SecretChatSummary>(
      `/secret-chats/${secretChatId}/decline`,
      { method: "POST" },
      token
    );
  },

  closeSecretChat(token: string, secretChatId: string) {
    return request<SecretChatSummary>(
      `/secret-chats/${secretChatId}/close`,
      { method: "POST" },
      token
    );
  },

  updateSecretChatTimer(token: string, secretChatId: string, autoDeleteSeconds: number | null) {
    return request<SecretChatSummary>(
      `/secret-chats/${secretChatId}/timer`,
      {
        method: "PATCH",
        body: JSON.stringify({ autoDeleteSeconds })
      },
      token
    );
  },

  getSecretChatMessages(token: string, secretChatId: string, limit = 50, before?: string | null) {
    const query = before
      ? `/secret-chats/${secretChatId}/messages?limit=${limit}&before=${encodeURIComponent(before)}`
      : `/secret-chats/${secretChatId}/messages?limit=${limit}`;
    return request<SecretChatMessage[]>(query, { method: "GET" }, token);
  },

  sendSecretChatMessage(
    token: string,
    secretChatId: string,
    payload: { ciphertext: string; nonce: string; attachmentIds?: string[] }
  ) {
    return request<SecretChatMessage>(
      `/secret-chats/${secretChatId}/messages`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  async uploadSecretChatAttachment(
    token: string,
    secretChatId: string,
    file: {
      uri: string;
      name: string;
      type?: string;
      kind?: "FILE" | "IMAGE" | "VOICE" | "VIDEO";
    }
  ) {
    return uploadMultipart<SecretAttachmentUpload>(
      `/secret-chats/${secretChatId}/attachments/upload`,
      () => {
        const formData = new FormData();
        if (file.kind) {
          formData.append("kind", file.kind);
        }
        formData.append("file", {
          uri: file.uri,
          name: file.name,
          type: file.type ?? "application/octet-stream"
        } as unknown as Blob);
        return formData;
      },
      token
    );
  },

  getSecretChatAttachmentAccess(token: string, attachmentId: string) {
    return request<AttachmentAccess>(
      `/secret-chats/attachments/${attachmentId}/access`,
      { method: "GET" },
      token
    );
  },

  removeSecretChatAttachment(token: string, attachmentId: string) {
    return request<void>(
      `/secret-chats/attachments/${attachmentId}`,
      { method: "DELETE" },
      token
    );
  },

  markSecretChatRead(token: string, secretChatId: string) {
    return request<SecretChatReadEvent>(
      `/secret-chats/${secretChatId}/read`,
      { method: "POST" },
      token
    );
  },

  reportSecretChatScreenshot(token: string, secretChatId: string) {
    return request<SecretChatScreenshotEvent>(
      `/secret-chats/${secretChatId}/screenshot`,
      { method: "POST" },
      token
    );
  },

  updateChatProfile(
    token: string,
    chatId: string,
    payload: {
      title?: string;
      about?: string | null;
      autoDeleteSeconds?: number | null;
      slowModeSeconds?: number | null;
      forumEnabled?: boolean | null;
      joinRequiresApproval?: boolean | null;
      commentsEnabled?: boolean | null;
      reactionsEnabled?: boolean | null;
      crossPostingEnabled?: boolean | null;
      linkedDiscussionChatId?: string | null;
    }
  ) {
    return request<ChatSummary>(
      `/chats/${chatId}/profile`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getChatAnalytics(token: string, chatId: string) {
    return request<ChatAnalytics>(`/chats/${chatId}/analytics`, { method: "GET" }, token);
  },

  async uploadChatPhoto(
    token: string,
    chatId: string,
    file: { uri: string; name: string; type?: string }
  ) {
    return uploadMultipart<ChatSummary>(
      `/chats/${chatId}/photo`,
      () => {
        const formData = new FormData();
        formData.append("file", {
          uri: file.uri,
          name: file.name,
          type: file.type ?? "image/jpeg"
        } as unknown as Blob);
        return formData;
      },
      token
    );
  },

  deleteChatPhoto(token: string, chatId: string) {
    return request<ChatSummary>(`/chats/${chatId}/photo`, { method: "DELETE" }, token);
  },

  getChatInviteLinks(token: string, chatId: string) {
    return request<ChatInviteLink[]>(`/chats/${chatId}/invite-links`, { method: "GET" }, token);
  },

  createChatInviteLink(
    token: string,
    chatId: string,
    payload: { label?: string; usageLimit?: number; expiresAt?: string | null }
  ) {
    return request<ChatInviteLink>(
      `/chats/${chatId}/invite-links`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  revokeChatInviteLink(token: string, chatId: string, inviteLinkId: string) {
    return request<ChatInviteLink>(
      `/chats/${chatId}/invite-links/${inviteLinkId}/revoke`,
      { method: "POST" },
      token
    );
  },

  joinChatByLink(token: string, inviteToken: string) {
    return request<JoinChatResult>(
      "/chats/join-by-link",
      {
        method: "POST",
        body: JSON.stringify({ token: inviteToken })
      },
      token
    );
  },

  joinChatByUsername(token: string, username: string) {
    return request<JoinChatResult>(
      "/chats/join-by-username",
      {
        method: "POST",
        body: JSON.stringify({ token: username })
      },
      token
    );
  },

  updateChatPublicUsername(token: string, chatId: string, publicUsername: string | null) {
    return request<ChatSummary>(
      `/chats/${chatId}/public-username`,
      {
        method: "POST",
        body: JSON.stringify({ publicUsername })
      },
      token
    );
  },

  getFolders(token: string) {
    return request<ChatFolder[]>("/folders", { method: "GET" }, token);
  },

  createFolder(
    token: string,
    payload: { title: string; position?: number; chatIds: string[] }
  ) {
    return request<ChatFolder>(
      "/folders",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  updateFolder(
    token: string,
    folderId: string,
    payload: { title: string; position?: number; chatIds: string[] }
  ) {
    return request<ChatFolder>(
      `/folders/${folderId}`,
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  deleteFolder(token: string, folderId: string) {
    return request<ChatFolder[]>(
      `/folders/${folderId}`,
      { method: "DELETE" },
      token
    );
  },

  setChatArchived(token: string, chatId: string, archived: boolean) {
    return request<ChatSummary>(
      `/chats/${chatId}/archive`,
      {
        method: "POST",
        body: JSON.stringify({ archived })
      },
      token
    );
  },

  muteChat(token: string, chatId: string, mutedUntil: string | null) {
    return request<ChatSummary>(
      `/chats/${chatId}/mute`,
      {
        method: "POST",
        body: JSON.stringify({ mutedUntil })
      },
      token
    );
  },

  leaveChat(token: string, chatId: string) {
    return request<LeaveChatResult>(
      `/chats/${chatId}/leave`,
      {
        method: "POST"
      },
      token
    );
  },

  clearHistory(
    token: string,
    chatId: string,
    payload?: { topicId?: string | null; upToMessageId?: string | null }
  ) {
    return request<ClearHistoryResult>(
      `/chats/${chatId}/clear-history`,
      {
        method: "POST",
        body: JSON.stringify(payload ?? {})
      },
      token
    );
  },

  markChatUnread(token: string, chatId: string, unread = true) {
    return request<ChatSummary>(
      `/chats/${chatId}/mark-unread`,
      {
        method: "POST",
        body: JSON.stringify({ unread })
      },
      token
    );
  },

  reportChat(
    token: string,
    chatId: string,
    payload: { category?: string; details?: string }
  ) {
    return request<ChatReportReceipt>(
      `/chats/${chatId}/report`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  pinChatToList(token: string, chatId: string) {
    return request<ChatSummary>(
      `/chats/${chatId}/list-pin`,
      {
        method: "PUT"
      },
      token
    );
  },

  unpinChatFromList(token: string, chatId: string) {
    return request<ChatSummary>(
      `/chats/${chatId}/list-pin`,
      {
        method: "DELETE"
      },
      token
    );
  },

  saveDraft(token: string, chatId: string, text: string) {
    return request<ChatSummary>(
      `/chats/${chatId}/draft`,
      {
        method: "PUT",
        body: JSON.stringify({ text })
      },
      token
    );
  },

  clearDraft(token: string, chatId: string) {
    return request<ChatSummary>(
      `/chats/${chatId}/draft`,
      { method: "DELETE" },
      token
    );
  },

  getChatMembers(token: string, chatId: string) {
    return request<ChatMember[]>(`/chats/${chatId}/members`, { method: "GET" }, token);
  },

  getChatJoinRequests(token: string, chatId: string) {
    return request<ChatJoinRequest[]>(`/chats/${chatId}/join-requests`, { method: "GET" }, token);
  },

  approveChatJoinRequest(token: string, chatId: string, userId: string) {
    return request<ChatMember>(
      `/chats/${chatId}/join-requests/${userId}/approve`,
      { method: "POST" },
      token
    );
  },

  declineChatJoinRequest(token: string, chatId: string, userId: string) {
    return request<void>(
      `/chats/${chatId}/join-requests/${userId}/decline`,
      { method: "POST" },
      token
    );
  },

  getChatRestrictions(token: string, chatId: string) {
    return request<ChatMember[]>(`/chats/${chatId}/restrictions`, { method: "GET" }, token);
  },

  getChatBans(token: string, chatId: string) {
    return request<ChatBan[]>(`/chats/${chatId}/bans`, { method: "GET" }, token);
  },

  banChatMember(
    token: string,
    chatId: string,
    userId: string,
    payload: {
      bannedUntil?: string | null;
      reason?: string | null;
    }
  ) {
    return request<ChatBan>(
      `/chats/${chatId}/bans/${userId}`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  unbanChatMember(token: string, chatId: string, userId: string) {
    return request<void>(`/chats/${chatId}/bans/${userId}`, { method: "DELETE" }, token);
  },

  updateChatMemberRestriction(
    token: string,
    chatId: string,
    userId: string,
    payload: {
      canSendMessages?: boolean;
      restrictedUntil?: string | null;
      restrictionReason?: string | null;
    }
  ) {
    return request<ChatMember>(
      `/chats/${chatId}/restrictions/${userId}`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getForumTopics(token: string, chatId: string) {
    return request<ForumTopic[]>(`/chats/${chatId}/topics`, { method: "GET" }, token);
  },

  createForumTopic(
    token: string,
    chatId: string,
    payload: { title: string; iconEmoji?: string | null }
  ) {
    return request<ForumTopic>(
      `/chats/${chatId}/topics`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  updateForumTopic(
    token: string,
    chatId: string,
    topicId: string,
    payload: { title?: string; iconEmoji?: string | null; closed?: boolean; hidden?: boolean }
  ) {
    return request<ForumTopic>(
      `/chats/${chatId}/topics/${topicId}`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  addChatMembers(token: string, chatId: string, userIds: string[]) {
    return request<ChatMember[]>(
      `/chats/${chatId}/members`,
      {
        method: "POST",
        body: JSON.stringify({ userIds })
      },
      token
    );
  },

  updateMemberRole(token: string, chatId: string, userId: string, role: "ADMIN" | "MEMBER") {
    return request<{ chatId: string; userId: string; role: string }>(
      `/chats/${chatId}/members/${userId}/role`,
      {
        method: "POST",
        body: JSON.stringify({ role })
      },
      token
    );
  },

  updateChatMemberPermissions(
    token: string,
    chatId: string,
    userId: string,
    payload: {
      canManageMembers?: boolean;
      canManageInviteLinks?: boolean;
      canManageMessages?: boolean;
      canPinMessages?: boolean;
      canApproveJoinRequests?: boolean;
      canPostMessages?: boolean;
      anonymousAdmin?: boolean;
    }
  ) {
    return request<ChatMember>(
      `/chats/${chatId}/permissions/${userId}`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  removeChatMember(token: string, chatId: string, userId: string) {
    return request<{ chatId: string; userId: string; role: string }>(
      `/chats/${chatId}/members/${userId}`,
      { method: "DELETE" },
      token
    );
  },

  markRead(token: string, chatId: string, messageId: string) {
    return request<ChatReadEvent>(
      `/chats/${chatId}/read`,
      {
        method: "POST",
        body: JSON.stringify({ messageId })
      },
      token
    );
  },

  sendTyping(token: string, chatId: string, typing: boolean) {
    return request<TypingEvent>(
      `/chats/${chatId}/typing`,
      {
        method: "POST",
        body: JSON.stringify({ typing })
      },
      token
    );
  },

  pinMessage(token: string, chatId: string, messageId: string) {
    return request<PinMessageEvent>(
      `/chats/${chatId}/pin`,
      {
        method: "POST",
        body: JSON.stringify({ messageId })
      },
      token
    );
  },

  getPinnedMessages(token: string, chatId: string, limit = 20) {
    return request<PinnedMessageHistoryEntry[]>(
      `/chats/${chatId}/pins?limit=${limit}`,
      { method: "GET" },
      token
    );
  },

  searchUsers(token: string, query: string) {
    const encodedQuery = encodeURIComponent(query);
    return request<UserSearchResult[]>(
      `/users/search?query=${encodedQuery}`,
      { method: "GET" },
      token
    );
  },

  searchGlobal(token: string, query: string, limit = 10) {
    const encodedQuery = encodeURIComponent(query);
    return request<GlobalSearchResponse>(
      `/search/global?query=${encodedQuery}&limit=${limit}`,
      { method: "GET" },
      token
    );
  },

  searchPublicChats(token: string, query: string, limit = 10) {
    const encodedQuery = encodeURIComponent(query);
    return request<PublicChatDiscovery[]>(
      `/search/public?query=${encodedQuery}&limit=${limit}`,
      { method: "GET" },
      token
    );
  },

  getBlockedUsers(token: string) {
    return request<BlockedUser[]>("/users/block", { method: "GET" }, token);
  },

  blockUser(token: string, blockedUserId: string) {
    return request<BlockedUser[]>(
      "/users/block",
      {
        method: "POST",
        body: JSON.stringify({ blockedUserId })
      },
      token
    );
  },

  unblockUser(token: string, blockedUserId: string) {
    return request<BlockedUser[]>(
      `/users/block/${blockedUserId}`,
      { method: "DELETE" },
      token
    );
  },

  reportUser(
    token: string,
    payload: { reportedUserId: string; category: string; details?: string }
  ) {
    return request<UserReportReceipt>(
      "/users/report",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getMe(token: string) {
    return request<UserProfile>("/users/me", { method: "GET" }, token);
  },

  getUsersPresence(token: string, userIds: string[]) {
    const params = new URLSearchParams();
    for (const userId of userIds) {
      params.append("userId", userId);
    }
    return request<UserPresenceStatus[]>(
      `/users/presence${params.toString() ? `?${params.toString()}` : ""}`,
      { method: "GET" },
      token
    );
  },

  async uploadMyPhoto(
    token: string,
    file: { uri: string; name: string; type?: string }
  ) {
    return uploadMultipart<UserProfile>(
      "/users/me/photo",
      () => {
        const formData = new FormData();
        formData.append("file", {
          uri: file.uri,
          name: file.name,
          type: file.type ?? "image/jpeg"
        } as unknown as Blob);
        return formData;
      },
      token
    );
  },

  deleteMyPhoto(token: string) {
    return request<UserProfile>("/users/me/photo", { method: "DELETE" }, token);
  },

  updateMe(
    token: string,
    payload: { displayName?: string; username?: string; about?: string }
  ) {
    return request<UserProfile>(
      "/users/me",
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  updatePrivacy(
    token: string,
    payload: {
      phonePrivacy: "EVERYBODY" | "CONTACTS" | "NOBODY";
      lastSeenPrivacy: "EVERYBODY" | "CONTACTS" | "NOBODY";
      storyPrivacy: "EVERYBODY" | "CONTACTS" | "NOBODY";
    }
  ) {
    return request<UserProfile>(
      "/users/me/privacy",
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getPrivacyExceptions(token: string) {
    return request<PrivacyExceptions>(
      "/users/me/privacy/exceptions",
      { method: "GET" },
      token
    );
  },

  updatePrivacyExceptions(token: string, payload: PrivacyExceptions) {
    return request<PrivacyExceptions>(
      "/users/me/privacy/exceptions",
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getLanguagePreferences(token: string) {
    return request<LanguagePreferences>(
      "/users/me/language-preferences",
      { method: "GET" },
      token
    );
  },

  updateLanguagePreferences(token: string, payload: LanguagePreferences) {
    return request<LanguagePreferences>(
      "/users/me/language-preferences",
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  getContacts(token: string) {
    return request<Contact[]>("/users/contacts", { method: "GET" }, token);
  },

  importContacts(
    token: string,
    payload: { contacts: ImportedPhoneContact[]; persistMatches?: boolean }
  ) {
    return request<ImportContactsResult>(
      "/users/contacts/import",
      {
        method: "POST",
        body: JSON.stringify({
          contacts: payload.contacts,
          persistMatches: payload.persistMatches ?? true
        })
      },
      token
    );
  },

  addContact(
    token: string,
    payload: { contactUserId: string; contactName?: string }
  ) {
    return request<Contact[]>(
      "/users/contacts",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  removeContact(token: string, contactUserId: string) {
    return request<Contact[]>(
      `/users/contacts/${contactUserId}`,
      { method: "DELETE" },
      token
    );
  },

  getMessages(
    token: string,
    chatId: string,
    limit = 50,
    topicId?: string | null,
    threadRootMessageId?: string | null
  ) {
    const topicQuery = topicId ? `&topicId=${encodeURIComponent(topicId)}` : "";
    const threadQuery = threadRootMessageId
      ? `&threadRootMessageId=${encodeURIComponent(threadRootMessageId)}`
      : "";
    return request<ChatMessage[]>(
      `/messages/chat/${chatId}?limit=${limit}${topicQuery}${threadQuery}`,
      { method: "GET" },
      token
    );
  },

  getMessagesBefore(
    token: string,
    chatId: string,
    before: string,
    limit = 50,
    topicId?: string | null,
    threadRootMessageId?: string | null
  ) {
    const encodedBefore = encodeURIComponent(before);
    const topicQuery = topicId ? `&topicId=${encodeURIComponent(topicId)}` : "";
    const threadQuery = threadRootMessageId
      ? `&threadRootMessageId=${encodeURIComponent(threadRootMessageId)}`
      : "";
    return request<ChatMessage[]>(
      `/messages/chat/${chatId}?before=${encodedBefore}&limit=${limit}${topicQuery}${threadQuery}`,
      { method: "GET" },
      token
    );
  },

  searchMessages(
    token: string,
    chatId: string,
    query: string,
    limit = 20,
    topicId?: string | null,
    threadRootMessageId?: string | null
  ) {
    const encodedQuery = encodeURIComponent(query);
    const topicQuery = topicId ? `&topicId=${encodeURIComponent(topicId)}` : "";
    const threadQuery = threadRootMessageId
      ? `&threadRootMessageId=${encodeURIComponent(threadRootMessageId)}`
      : "";
    return request<SearchMessagesResponse>(
      `/messages/chat/${chatId}/search?query=${encodedQuery}&limit=${limit}${topicQuery}${threadQuery}`,
      { method: "GET" },
      token
    );
  },

  getMessage(token: string, messageId: string) {
    return request<ChatMessage>(`/messages/${messageId}`, { method: "GET" }, token);
  },

  getScheduledMessages(
    token: string,
    chatId: string,
    topicId?: string | null,
    threadRootMessageId?: string | null
  ) {
    const params = new URLSearchParams();
    if (topicId) {
      params.set("topicId", topicId);
    }
    if (threadRootMessageId) {
      params.set("threadRootMessageId", threadRootMessageId);
    }
    const query = params.toString();
    return request<ScheduledMessage[]>(
      `/messages/chat/${chatId}/scheduled${query ? `?${query}` : ""}`,
      { method: "GET" },
      token
    );
  },

  scheduleMessage(token: string, payload: ScheduleMessagePayload) {
    return request<ScheduledMessage>(
      "/messages/scheduled",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  sendWhenOnlineMessage(token: string, payload: SendMessagePayload) {
    return request<ScheduledMessage>(
      "/messages/send-when-online",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  cancelScheduledMessage(token: string, scheduledMessageId: string) {
    return request<void>(
      `/messages/scheduled/${scheduledMessageId}`,
      { method: "DELETE" },
      token
    );
  },

  async uploadAttachment(
    token: string,
    chatId: string,
    file: UploadAttachmentFile,
    options: UploadAttachmentOptions = {}
  ) {
    const info = await FileSystem.getInfoAsync(file.uri);
    const fileSizeBytes =
      info.exists && !info.isDirectory && typeof info.size === "number"
        ? info.size
        : null;

    if (
      file.uri.startsWith("file://") &&
      typeof fileSizeBytes === "number" &&
      fileSizeBytes >= RESUMABLE_ATTACHMENT_UPLOAD_THRESHOLD_BYTES
    ) {
      return uploadAttachmentResumable(token, chatId, file, fileSizeBytes, options);
    }

    if (
      file.uri.startsWith("file://") &&
      typeof fileSizeBytes === "number" &&
      options.onProgress
    ) {
      return uploadAttachmentMultipartWithProgress(token, chatId, file, options);
    }

    return uploadMultipart<MessageAttachment>(
      "/attachments/upload",
      () => {
        const formData = new FormData();
        for (const [key, value] of Object.entries(buildAttachmentUploadParameters(chatId, file))) {
          formData.append(key, value);
        }
        formData.append("file", {
          uri: file.uri,
          name: file.name,
          type: file.type ?? "application/octet-stream"
        } as unknown as Blob);
        return formData;
      },
      token
    );
  },

  createAttachmentUploadSession(
    token: string,
    payload: {
      chatId: string;
      originalFileName: string;
      contentType?: string;
      kind?: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
      totalSizeBytes: number;
      durationMs?: number;
      width?: number;
      height?: number;
      waveform?: number[];
    }
  ) {
    return request<AttachmentUploadSession>(
      "/attachments/upload-sessions",
      {
        method: "POST",
        body: JSON.stringify({
          ...payload,
          contentType: payload.contentType ?? "application/octet-stream",
          waveform: normalizeWaveformSamples(payload.waveform)
        })
      },
      token
    );
  },

  getAttachmentUploadSession(token: string, sessionId: string) {
    return request<AttachmentUploadSession>(
      `/attachments/upload-sessions/${sessionId}`,
      { method: "GET" },
      token
    );
  },

  uploadAttachmentChunk(
    token: string,
    sessionId: string,
    payload: { offset: number; base64Chunk: string }
  ) {
    return request<AttachmentUploadSession>(
      `/attachments/upload-sessions/${sessionId}/chunks`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  completeAttachmentUploadSession(token: string, sessionId: string) {
    return request<MessageAttachment>(
      `/attachments/upload-sessions/${sessionId}/complete`,
      { method: "POST" },
      token
    );
  },

  abortAttachmentUploadSession(token: string, sessionId: string) {
    return request<void>(
      `/attachments/upload-sessions/${sessionId}`,
      { method: "DELETE" },
      token
    );
  },

  getAttachmentAccess(token: string, attachmentId: string) {
    return request<AttachmentAccess>(
      `/attachments/${attachmentId}/access`,
      { method: "GET" },
      token
    );
  },

  getAttachmentAlbum(token: string, albumId: string) {
    return request<MessageAttachment[]>(
      `/attachments/albums/${albumId}`,
      { method: "GET" },
      token
    );
  },

  getRecentGifs(token: string) {
    return request<MessageAttachment[]>("/attachments/recent-gifs", { method: "GET" }, token);
  },

  trimAttachment(
    token: string,
    attachmentId: string,
    payload: { startMs: number; endMs: number }
  ) {
    return request<MessageAttachment>(
      `/attachments/${attachmentId}/trim`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  async openAttachment(token: string, attachment: MessageAttachment) {
    const access = await api.getAttachmentAccess(token, attachment.attachmentId);
    const downloadUrl = access.downloadUrl.startsWith("http")
      ? access.downloadUrl
      : `${API_BASE_URL}${access.downloadUrl.replace(/^\/api/, "")}`;
    const activeToken = getLatestSessionToken(token);
    const baseDirectory =
      FileSystem.cacheDirectory ?? FileSystem.documentDirectory ?? null;

    if (!baseDirectory) {
      throw new Error("No writable filesystem is available");
    }

    const targetUri = `${baseDirectory}${attachment.attachmentId}-${attachment.originalFileName}`;
    const result = await FileSystem.downloadAsync(
      downloadUrl,
      targetUri,
      access.requiresAuthorization && activeToken
        ? {
            headers: {
              Authorization: `Bearer ${activeToken}`
            }
          }
        : undefined
    );

    return result.uri;
  },

  editMessage(token: string, messageId: string, payload: { text: string; entities?: MessageTextEntity[] }) {
    return request<ChatMessage>(
      `/messages/${messageId}`,
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  deleteMessage(token: string, messageId: string) {
    return request<ChatMessage>(`/messages/${messageId}`, { method: "DELETE" }, token);
  },

  updateLiveLocation(
    token: string,
    messageId: string,
    payload: {
      latitude: number;
      longitude: number;
      title?: string | null;
      address?: string | null;
    }
  ) {
    return request<ChatMessage>(
      `/messages/${messageId}/live-location`,
      {
        method: "PATCH",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  stopLiveLocation(token: string, messageId: string) {
    return request<ChatMessage>(
      `/messages/${messageId}/live-location/stop`,
      { method: "POST" },
      token
    );
  },

  toggleReaction(token: string, messageId: string, emoji: string) {
    return request<ChatMessage>(
      `/messages/${messageId}/reactions`,
      {
        method: "POST",
        body: JSON.stringify({ emoji })
      },
      token
    );
  },

  reportMessage(
    token: string,
    messageId: string,
    payload: { category?: string; details?: string }
  ) {
    return request<MessageReportReceipt>(
      `/messages/${messageId}/report`,
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  forwardMessage(
    token: string,
    payload: ForwardMessagePayload
  ) {
    return request<ChatMessage>(
      "/messages/forward",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  sendMessage(token: string, payload: SendMessagePayload) {
    return request<ChatMessage>(
      "/messages",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  createPollMessage(token: string, payload: CreatePollPayload) {
    return request<ChatMessage>(
      "/messages/poll",
      {
        method: "POST",
        body: JSON.stringify(payload)
      },
      token
    );
  },

  votePoll(token: string, messageId: string, optionIds: string[]) {
    return request<ChatMessage>(
      `/messages/${messageId}/poll/vote`,
      {
        method: "POST",
        body: JSON.stringify({ optionIds })
      },
      token
    );
  },

  closePoll(token: string, messageId: string) {
    return request<ChatMessage>(
      `/messages/${messageId}/poll/close`,
      { method: "POST" },
      token
    );
  }
};
