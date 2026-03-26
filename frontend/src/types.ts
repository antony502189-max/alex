export type AuthSession = {
  token: string;
  refreshToken: string | null;
  sessionId: string;
  userId: string;
  phoneNumber: string;
  displayName: string;
  username: string | null;
  accessTokenExpiresAt: string | null;
  refreshTokenExpiresAt: string | null;
  authMethod: string | null;
  trustedSession: boolean;
};

export type AuthFlowResult = {
  authenticated: boolean;
  requiresTwoFactor: boolean;
  token: string | null;
  refreshToken: string | null;
  sessionId: string | null;
  userId: string | null;
  phoneNumber: string | null;
  displayName: string | null;
  username: string | null;
  accessTokenExpiresAt: string | null;
  refreshTokenExpiresAt: string | null;
  authMethod: string | null;
  trustedSession: boolean | null;
  twoFactorChallengeId: string | null;
  twoFactorHint: string | null;
};

export type LoginCodeChallenge = {
  challengeId: string;
  phoneNumber: string;
  expiresAt: string;
  codeLength: number;
  debugCode: string | null;
};

export type GeneratedQrLogin = {
  challengeId: string;
  qrToken: string;
  createdAt: string;
  expiresAt: string;
};

export type QrLoginChallenge = {
  challengeId: string;
  status: string;
  deviceName: string | null;
  platform: string | null;
  appVersion: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
  expiresAt: string;
  boundAt: string | null;
  approvedAt: string | null;
};

export type QrLoginStatus = {
  status: string;
  expiresAt: string;
  deviceName: string | null;
  platform: string | null;
  appVersion: string | null;
  auth: AuthFlowResult | null;
};

export type UserSession = {
  sessionId: string;
  deviceName: string;
  platform: string | null;
  appVersion: string | null;
  userAgent: string | null;
  ipAddress: string | null;
  createdAt: string;
  lastActiveAt: string;
  notificationsEnabled: boolean;
  current: boolean;
  authMethod: string | null;
  trustedSession: boolean;
  trustedAt: string | null;
};

export type AuthSecurityEvent = {
  eventId: string;
  userId: string;
  sessionId: string;
  eventType: string;
  severity: string;
  ipAddress: string | null;
  userAgent: string | null;
  deviceName: string | null;
  platform: string | null;
  appVersion: string | null;
  details: string | null;
  createdAt: string;
};

export type TwoFactorStatus = {
  enabled: boolean;
  hint: string | null;
  enabledAt: string | null;
};

export type PasskeyCredential = {
  credentialId: string;
  externalCredentialId: string;
  label: string | null;
  transports: string | null;
  createdAt: string;
  lastUsedAt: string | null;
};

export type DevicePasskey = {
  credentialId: string;
  publicKey: string;
  phoneNumber: string;
  label: string | null;
  createdAt: string;
  lastUsedAt: string | null;
};

export type PasskeyRegistrationOptions = {
  challengeId: string;
  challenge: string;
  userId: string;
  userName: string;
  displayName: string;
  expiresAt: string;
};

export type PasskeyLoginOptions = {
  challengeId: string;
  challenge: string;
  userId: string;
  phoneNumber: string;
  expiresAt: string;
};

export type PhoneChangeChallenge = {
  challengeId: string;
  newPhoneNumber: string;
  expiresAt: string;
  debugCode: string | null;
};

export type AccountExportJob = {
  jobId: string;
  status: string;
  format: string;
  includeAttachmentsMetadata: boolean;
  messageCount: number;
  artifactChecksum: string | null;
  artifactLocation: string | null;
  createdAt: string;
  completedAt: string | null;
};

export type AccountDeletionJob = {
  jobId: string;
  triggerType: string;
  status: string;
  reason: string | null;
  scheduledFor: string | null;
  createdAt: string;
  executedAt: string | null;
};

export type ChatSummary = {
  chatId: string;
  chatType: "DIRECT" | "GROUP" | "CHANNEL" | "SAVED";
  title: string;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  peerUserId: string | null;
  peerPhoneNumber: string | null;
  peerDisplayName: string | null;
  peerOnline: boolean;
  peerLastSeenAt: string | null;
  peerIsBot: boolean;
  peerBotSupportsInline: boolean;
  peerBotWebAppUrl: string | null;
  publicUsername: string | null;
  about: string | null;
  autoDeleteSeconds: number | null;
  slowModeSeconds: number | null;
  forumEnabled: boolean;
  topicCount: number;
  linkedDiscussionChatId: string | null;
  linkedDiscussionChatTitle: string | null;
  lastMessageAt: string;
  memberCount: number;
  lastReadAt: string | null;
  unreadCount: number;
  mentionCount: number;
  replyCount: number;
  archived: boolean;
  draftText: string | null;
  draftUpdatedAt: string | null;
  mutedUntil: string | null;
  pinned: boolean;
  pinOrder: number | null;
  pinnedMessageId: string | null;
  joinRequiresApproval: boolean;
  commentsEnabled: boolean;
  reactionsEnabled: boolean;
  crossPostingEnabled: boolean;
  markedUnread: boolean;
};

export type ChatAnalytics = {
  chatId: string;
  chatType: "GROUP" | "CHANNEL";
  memberCount: number;
  adminCount: number;
  restrictedCount: number;
  bannedCount: number;
  pendingJoinRequestCount: number;
  activeInviteLinkCount: number;
  messagesLast24h: number;
  reactionsLast24h: number;
  commentsLast24h: number;
  lastMessageAt: string | null;
};

export type LeaveChatResult = {
  chatId: string;
  userId: string;
  status: string;
  leftAt: string;
};

export type ClearHistoryResult = {
  chatId: string;
  topicId: string | null;
  upToMessageId: string | null;
  clearedMessageCount: number;
  clearedAt: string;
};

export type ChatReportReceipt = {
  reportId: string;
  chatId: string;
  category: string;
  createdAt: string;
};

export type ChatMessage = {
  chatId: string;
  messageId: string;
  clientMessageId: string | null;
  senderId: string | null;
  displaySenderName: string | null;
  displaySenderPhotoUrl: string | null;
  displaySenderPhotoAccessExpiresAt: string | null;
  anonymousSender: boolean;
  recipientId: string | null;
  viaBotUserId: string | null;
  topicId: string | null;
  threadRootMessageId: string | null;
  discussionChatId: string | null;
  discussionRootMessageId: string | null;
  commentCount: number;
  text: string;
  entities: MessageTextEntity[];
  messageType: string;
  caption: string | null;
  silent: boolean;
  location: MessageLocation | null;
  contactCard: MessageContactCard | null;
  serviceMessage: MessageServiceInfo | null;
  createdAt: string;
  replyToMessageId: string | null;
  forwardedFromChatId: string | null;
  forwardedFromMessageId: string | null;
  poll: Poll | null;
  sticker: Sticker | null;
  attachments: MessageAttachment[];
  reactions: MessageReactionSummary[];
  deliveryStatus: "QUEUED" | "SENT" | "DELIVERED" | "READ";
  deliveredAt: string | null;
  readAt: string | null;
  expiresAt: string | null;
  editedAt: string | null;
  deletedAt: string | null;
};

export type ScheduledMessage = {
  scheduledMessageId: string;
  clientMessageId: string | null;
  chatId: string;
  senderId: string;
  topicId: string | null;
  threadRootMessageId: string | null;
  discussionChatId: string | null;
  discussionRootMessageId: string | null;
  text: string;
  entities: MessageTextEntity[];
  messageType: string;
  caption: string | null;
  silent: boolean;
  location: MessageLocation | null;
  contactCard: MessageContactCard | null;
  serviceMessage: MessageServiceInfo | null;
  replyToMessageId: string | null;
  stickerId: string | null;
  attachments: MessageAttachment[];
  scheduledAt: string;
  createdAt: string;
  status: "QUEUED" | "PENDING" | "WAITING_ONLINE" | "DELIVERED" | "FAILED" | "CANCELED";
};

export type MessageAttachment = {
  attachmentId: string;
  originalFileName: string;
  contentType: string;
  kind: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
  fileSizeBytes: number;
  durationMs: number | null;
  downloadUrl: string;
  previewUrl: string | null;
  thumbnailUrl: string | null;
  width: number | null;
  height: number | null;
  waveform: number[] | null;
  accessExpiresAt: string | null;
  requiresAuthorization: boolean;
  streamingSupported: boolean;
  localUri?: string | null;
  uploadState?: "UPLOADED" | "PENDING_UPLOAD";
};

export type SharedMediaEntry = {
  chatId: string;
  messageId: string;
  createdAt: string;
  senderDisplayName: string | null;
  caption: string | null;
  attachment: MessageAttachment;
};

export type SharedMediaLink = {
  linkId: string;
  chatId: string;
  messageId: string;
  createdAt: string;
  url: string;
  label: string | null;
};

export type SharedMediaBuckets = {
  chatId: string;
  media: SharedMediaEntry[];
  files: SharedMediaEntry[];
  links: SharedMediaLink[];
  loadedAt: string;
};

export type AttachmentAccess = {
  downloadUrl: string;
  previewUrl: string | null;
  accessExpiresAt: string | null;
  requiresAuthorization: boolean;
};

export type AttachmentUploadSession = {
  uploadSessionId: string;
  chatId: string;
  originalFileName: string;
  contentType: string;
  kind: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
  totalSizeBytes: number;
  uploadedBytes: number;
  chunkSizeBytes: number;
  status: "ACTIVE" | "COMPLETED" | "ABORTED" | "EXPIRED";
  complete: boolean;
  expiresAt: string;
  completedAttachmentId: string | null;
};

export type MessageTextEntity = {
  type: "BOLD" | "ITALIC" | "UNDERLINE" | "STRIKETHROUGH" | "SPOILER" | "CODE" | "PRE";
  offset: number;
  length: number;
};

export type MessageLocation = {
  latitude: number;
  longitude: number;
  title: string | null;
  address: string | null;
};

export type MessageContactCard = {
  firstName: string | null;
  lastName: string | null;
  phoneNumber: string | null;
  userId: string | null;
  vcard: string | null;
};

export type MessageServiceInfo = {
  serviceType: string | null;
  text: string | null;
};

export type MessageReactionSummary = {
  emoji: string;
  count: number;
};

export type PollOption = {
  optionId: string;
  text: string;
  voteCount: number;
  selectedByMe: boolean;
};

export type Poll = {
  pollId: string;
  question: string;
  multipleChoice: boolean;
  closed: boolean;
  totalVoters: number;
  options: PollOption[];
};

export type Sticker = {
  stickerId: string;
  packId: string;
  packTitle: string;
  emoji: string;
  label: string;
  backgroundFrom: string;
  backgroundTo: string;
  textColor: string;
};

export type StickerPack = {
  packId: string;
  title: string;
  slug: string;
  stickers: Sticker[];
};

export type Story = {
  storyId: string;
  ownerUserId: string;
  ownerDisplayName: string;
  ownerUsername: string | null;
  text: string | null;
  media: StoryMedia | null;
  backgroundFrom: string;
  backgroundTo: string;
  textColor: string;
  audience: "DEFAULT" | "EVERYBODY" | "CONTACTS" | "NOBODY" | "CLOSE_FRIENDS" | "CUSTOM";
  createdAt: string;
  expiresAt: string;
  expired: boolean;
  viewed: boolean;
  own: boolean;
  viewsCount: number;
};

export type StoryMedia = {
  kind: "IMAGE" | "VIDEO";
  fileName: string | null;
  contentType: string;
  durationMs: number | null;
  downloadUrl: string;
  previewUrl: string | null;
  accessExpiresAt: string | null;
  requiresAuthorization: boolean;
  streamingSupported: boolean;
};

export type StoryFeedItem = {
  ownerUserId: string;
  ownerDisplayName: string;
  ownerUsername: string | null;
  own: boolean;
  hasUnviewed: boolean;
  latestStoryAt: string;
  stories: Story[];
};

export type StoryViewer = {
  viewerUserId: string;
  displayName: string;
  username: string | null;
  viewedAt: string;
};

export type UserSearchResult = {
  userId: string;
  phoneNumber: string | null;
  displayName: string;
  username: string | null;
  bot: boolean;
  botDescription: string | null;
  botSupportsInline: boolean;
  botWebAppUrl: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  online: boolean;
  lastSeenAt: string | null;
};

export type BlockedUser = {
  userId: string;
  phoneNumber: string | null;
  displayName: string;
  username: string | null;
  bot: boolean;
  botDescription: string | null;
  botSupportsInline: boolean;
  botWebAppUrl: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  online: boolean;
  lastSeenAt: string | null;
  blockedAt: string;
};

export type UserReportReceipt = {
  reportId: string;
  reportedUserId: string;
  category: string;
  createdAt: string;
};

export type UserProfile = {
  userId: string;
  phoneNumber: string | null;
  displayName: string;
  username: string | null;
  bot: boolean;
  botDescription: string | null;
  botSupportsInline: boolean;
  botWebAppUrl: string | null;
  about: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  phonePrivacy: "EVERYBODY" | "CONTACTS" | "NOBODY";
  lastSeenPrivacy: "EVERYBODY" | "CONTACTS" | "NOBODY";
  storyPrivacy: "EVERYBODY" | "CONTACTS" | "NOBODY";
  lastSeenAt: string | null;
  online: boolean;
};

export type UserPresenceStatus = {
  userId: string;
  online: boolean;
  lastSeenAt: string | null;
};

export type Contact = {
  userId: string;
  contactName: string;
  displayName: string;
  username: string | null;
  bot: boolean;
  botDescription: string | null;
  botSupportsInline: boolean;
  botWebAppUrl: string | null;
  phoneNumber: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  online: boolean;
  lastSeenAt: string | null;
};

export type BotCommand = {
  command: string;
  description: string;
};

export type BotSummary = {
  userId: string;
  displayName: string;
  username: string;
  description: string | null;
  supportsInline: boolean;
  webAppUrl: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
};

export type DeveloperBot = {
  botUserId: string;
  ownerUserId: string;
  displayName: string;
  username: string;
  description: string | null;
  about: string | null;
  supportsInline: boolean;
  webAppUrl: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  apiTokenPrefix: string;
  tokenRotatedAt: string;
  webhookUrl: string | null;
  webhookEnabled: boolean;
  hasWebhookSecret: boolean;
  lastWebhookDeliveryAt: string | null;
  lastWebhookError: string | null;
  createdAt: string;
  updatedAt: string;
};

export type IssuedBotToken = {
  bot: DeveloperBot;
  apiToken: string;
};

export type BotWebAppLaunch = {
  botUserId: string;
  botUsername: string;
  chatId: string | null;
  launchUrl: string;
  issuedAt: string;
  expiresAt: string;
};

export type InlineBotResult = {
  resultId: string;
  botUserId: string;
  botUsername: string;
  title: string;
  description: string;
  text: string;
};

export type ChatFolder = {
  folderId: string;
  title: string;
  position: number;
  chatIds: string[];
};

export type ForumTopic = {
  topicId: string;
  chatId: string;
  title: string;
  iconEmoji: string | null;
  generalTopic: boolean;
  closed: boolean;
  hidden: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
};

export type ChatInviteLink = {
  inviteLinkId: string;
  chatId: string;
  label: string | null;
  token: string;
  shareUrl: string;
  revoked: boolean;
  usageLimit: number | null;
  usageCount: number;
  expiresAt: string | null;
  createdAt: string;
  lastUsedAt: string | null;
};

export type ChatJoinRequest = {
  userId: string;
  phoneNumber: string | null;
  displayName: string;
  username: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  source: string;
  inviteLinkId: string | null;
  requestedAt: string;
};

export type ChatBan = {
  userId: string;
  phoneNumber: string | null;
  displayName: string;
  username: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  bannedAt: string;
  bannedUntil: string | null;
  reason: string | null;
  bannedByUserId: string | null;
};

export type JoinChatResult = {
  status: "JOINED" | "REQUESTED";
  chat: ChatSummary | null;
  chatId: string;
  title: string;
  publicUsername: string | null;
  requestedAt: string | null;
};

export type PublicChatDiscovery = {
  chatId: string;
  chatType: "GROUP" | "CHANNEL";
  title: string;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  publicUsername: string | null;
  about: string | null;
  forumEnabled: boolean;
  memberCount: number;
  joinRequiresApproval: boolean;
  joined: boolean;
};

export type ChatMember = {
  userId: string;
  phoneNumber: string | null;
  displayName: string;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  role: string;
  joinedAt: string;
  lastReadAt: string | null;
  lastSentMessageAt: string | null;
  canSendMessages: boolean;
  canManageMembers: boolean;
  canManageInviteLinks: boolean;
  canManageMessages: boolean;
  canPinMessages: boolean;
  canApproveJoinRequests: boolean;
  canPostMessages: boolean;
  anonymousAdmin: boolean;
  restrictedUntil: string | null;
  restrictionReason: string | null;
};

export type TypingEvent = {
  chatId: string;
  userId: string;
  typing: boolean;
  emittedAt: string;
};

export type ChatReadEvent = {
  chatId: string;
  userId: string;
  messageId: string;
  readAt: string;
};

export type PinMessageEvent = {
  chatId: string;
  messageId: string;
  pinnedByUserId: string;
  pinnedAt: string;
};

export type PinnedMessageHistoryEntry = {
  pinEventId: string;
  chatId: string;
  messageId: string;
  pinnedByUserId: string;
  pinnedByDisplayName: string;
  pinnedAt: string;
  active: boolean;
  unpinnedAt: string | null;
  message: ChatMessage | null;
};

export type SearchMessagesResponse = {
  query: string;
  messages: ChatMessage[];
};

export type GlobalMessageSearchResult = {
  chat: ChatSummary;
  message: ChatMessage;
};

export type GlobalSearchResponse = {
  query: string;
  users: UserSearchResult[];
  chats: ChatSummary[];
  messages: GlobalMessageSearchResult[];
};

export type CallParticipant = {
  userId: string;
  displayName: string;
  phoneNumber: string | null;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  state: "RINGING" | "JOINED" | "LEFT" | "DECLINED" | "MISSED";
  invitedAt: string;
  joinedAt: string | null;
  leftAt: string | null;
  audioPublishingAllowed: boolean;
  videoPublishingAllowed: boolean;
  screenShareAllowed: boolean;
  screenSharing: boolean;
  moderatedByUserId: string | null;
  moderatedAt: string | null;
};

export type CallSession = {
  callId: string;
  chatId: string;
  createdByUserId: string;
  kind: "VOICE" | "VIDEO";
  mode: "DIRECT" | "GROUP";
  status: "RINGING" | "ACTIVE" | "DECLINED" | "ENDED";
  startedAt: string;
  answeredAt: string | null;
  endedAt: string | null;
  viewerCanModerate: boolean;
  viewerCanManageLinks: boolean;
  participants: CallParticipant[];
};

export type CallJoinLink = {
  linkId: string;
  chatId: string;
  createdByUserId: string;
  kind: "VOICE" | "VIDEO";
  label: string | null;
  token: string;
  shareUrl: string;
  revoked: boolean;
  usageCount: number;
  expiresAt: string | null;
  createdAt: string;
  lastUsedAt: string | null;
};

export type CallSignalEvent = {
  callId: string;
  fromUserId: string;
  toUserId: string;
  signalType: string;
  payload: string;
  emittedAt: string;
};

export type CallInboxEvent = {
  eventType: "STARTED" | "UPDATED" | "SIGNAL";
  call: CallSession | null;
  signal: CallSignalEvent | null;
};

export type CallHistoryEntry = {
  callId: string;
  chatId: string;
  chatType: "DIRECT" | "GROUP" | "CHANNEL" | "SAVED";
  title: string;
  photoUrl: string | null;
  photoAccessExpiresAt: string | null;
  kind: "VOICE" | "VIDEO";
  mode: "DIRECT" | "GROUP";
  status: "RINGING" | "ACTIVE" | "DECLINED" | "ENDED";
  direction: "INCOMING" | "OUTGOING";
  missed: boolean;
  participantCount: number;
  startedAt: string;
  answeredAt: string | null;
  endedAt: string | null;
};

export type SecretChatSummary = {
  secretChatId: string;
  peerUserId: string;
  peerDisplayName: string;
  peerPhoneNumber: string | null;
  peerPhotoUrl: string | null;
  peerPhotoAccessExpiresAt: string | null;
  initiatorSessionId: string;
  recipientSessionId: string | null;
  peerSessionId: string | null;
  peerDeviceName: string | null;
  initiatorPublicKey: string;
  recipientPublicKey: string | null;
  sharedKeyFingerprint: string | null;
  status: "PENDING" | "ACTIVE" | "DECLINED" | "CLOSED";
  direction: "INCOMING" | "OUTGOING";
  autoDeleteSeconds: number | null;
  createdAt: string;
  acceptedAt: string | null;
  closedAt: string | null;
  lastMessageAt: string | null;
};

export type SecretChatMessage = {
  secretChatId: string;
  secretMessageId: string;
  senderUserId: string;
  senderSessionId: string;
  messageType: "TEXT" | "ATTACHMENT";
  ciphertext: string;
  nonce: string;
  createdAt: string;
  readAt: string | null;
  expiresAt: string | null;
};

export type SecretChatAttachment = {
  attachmentId: string;
  kind: "FILE" | "IMAGE" | "VOICE" | "VIDEO";
  originalFileName: string;
  contentType: string;
  fileSizeBytes: number;
  fileNonce: string;
  durationMs?: number | null;
};

export type SecretChatPayload = {
  version: 1;
  text: string | null;
  attachments: SecretChatAttachment[];
};

export type SecretAttachmentUpload = {
  attachmentId: string;
  kind: "FILE" | "IMAGE" | "VOICE" | "VIDEO";
  encryptedFileSizeBytes: number;
  createdAt: string;
};

export type SecretChatReadEvent = {
  secretChatId: string;
  readByUserId: string;
  readAt: string;
  expiresAt: string | null;
  messageIds: string[];
};

export type SecretChatScreenshotEvent = {
  secretChatId: string;
  capturedByUserId: string;
  capturedAt: string;
};

export type SecretChatInboxEvent = {
  eventType: "CHAT_UPDATED" | "MESSAGE_CREATED" | "MESSAGE_READ" | "SCREENSHOT_CAPTURED";
  chat: SecretChatSummary | null;
  message: SecretChatMessage | null;
  read: SecretChatReadEvent | null;
  screenshot: SecretChatScreenshotEvent | null;
};

export type CallIceServer = {
  url: string;
  username: string | null;
  credential: string | null;
};

export type CallRtcConfig = {
  iceServers: CallIceServer[];
  mediaPolicy: CallMediaPolicy;
};

export type CallMediaPolicy = {
  videoBitrateHighKbps: number;
  videoBitrateMediumKbps: number;
  videoBitrateLowKbps: number;
  screenShareBitrateKbps: number;
  statsSampleIntervalSeconds: number;
  degradedConnectionRttMs: number;
  poorConnectionRttMs: number;
  degradedConnectionPacketLossPercent: number;
  poorConnectionPacketLossPercent: number;
};

export type CallAdaptationProfile = "BALANCED" | "AUDIO_PRIORITY" | "VIDEO_PRIORITY";

export type CallNetworkQuality = "UNKNOWN" | "EXCELLENT" | "GOOD" | "FAIR" | "POOR";

export type CallMediaPeerState = {
  userId: string;
  connectionState: string;
  iceConnectionState: string;
  signalingState: string;
  remoteTrackCount: number;
  remoteStreamUrl: string | null;
  restartingIce: boolean;
  quality: CallNetworkQuality;
  roundTripTimeMs: number | null;
  sendBitrateKbps: number | null;
  packetLossPercent: number | null;
};

export type CallMediaState = {
  callId: string | null;
  phase: "IDLE" | "STARTING" | "READY" | "ERROR";
  adaptationProfile: CallAdaptationProfile;
  networkQuality: CallNetworkQuality;
  localAudioEnabled: boolean;
  localVideoEnabled: boolean;
  localScreenSharing: boolean;
  localStreamReady: boolean;
  localStreamUrl: string | null;
  screenShareSupported: boolean;
  estimatedVideoSendBitrateKbps: number | null;
  targetVideoBitrateKbps: number | null;
  speakerOn: boolean;
  peers: CallMediaPeerState[];
  error: string | null;
  requiresNativeBuild: boolean;
};
