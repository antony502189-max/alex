import { api } from "./api";
import type { OutboxOperation } from "./api";
import { cleanupStagedAttachment, isPendingLocalAttachment, uploadPendingAttachment } from "./attachmentDrafts";
import { toQueuedMessageId } from "./clientMessageIds";
import { localDatabase } from "./localDatabase";
import type {
  ChatMessage,
  MessageAttachment,
  MessageContactCard,
  MessageLiveLocation,
  MessageLocation,
  MessageServiceInfo,
  MessageTextEntity,
  Poll,
  Sticker
} from "../types";

type OptimisticMessageDraft = {
  text?: string;
  entities?: MessageTextEntity[];
  messageType?: string;
  caption?: string | null;
  silent?: boolean;
  liveLocation?: MessageLiveLocation | null;
  location?: MessageLocation | null;
  contactCard?: MessageContactCard | null;
  serviceMessage?: MessageServiceInfo | null;
  displaySenderName?: string | null;
  displaySenderPhotoUrl?: string | null;
  displaySenderPhotoAccessExpiresAt?: string | null;
  anonymousSender?: boolean;
  replyToMessageId?: string | null;
  viaBotUserId?: string | null;
  threadRootMessageId?: string | null;
  discussionChatId?: string | null;
  discussionRootMessageId?: string | null;
  forwardedFromChatId?: string | null;
  forwardedFromMessageId?: string | null;
  poll?: Poll | null;
  sticker?: Sticker | null;
  attachments?: MessageAttachment[];
};

type QueueOutgoingParams = {
  chatId: string;
  currentUserId: string;
  operation: OutboxOperation;
  attachments: MessageAttachment[];
  optimistic: OptimisticMessageDraft;
};

type FlushHandlers = {
  onSynced?: (queuedMessageId: string, message: ChatMessage) => void;
  onDropped?: (queuedMessageId: string, chatId: string) => void;
};

class MessageOutboxService {
  private flushPromise?: Promise<void>;

  async queueMessage(params: QueueOutgoingParams): Promise<ChatMessage> {
    const clientMessageId = this.getClientMessageId(params.operation);
    if (!clientMessageId) {
      throw new Error("Queued messages require clientMessageId");
    }

    const createdAt = new Date().toISOString();
    const optimisticMessage = this.buildOptimisticMessage(params, createdAt);

    await localDatabase.enqueueOutbox({
      clientMessageId,
      chatId: params.chatId,
      currentUserId: params.currentUserId,
      createdAt,
      operation: params.operation,
      attachments: params.attachments,
      lastError: null
    });
    await localDatabase.upsertMessages(params.currentUserId, [optimisticMessage]);

    return optimisticMessage;
  }

  async flush(token: string, currentUserId: string, handlers: FlushHandlers = {}) {
    if (this.flushPromise) {
      return this.flushPromise;
    }

    this.flushPromise = this.flushInternal(token, currentUserId, handlers).finally(() => {
      this.flushPromise = undefined;
    });
    return this.flushPromise;
  }

  isRetryable(error: unknown) {
    const message = error instanceof Error ? error.message.toLowerCase() : "";
    return (
      message.includes("network") ||
      message.includes("fetch") ||
      message.includes("timeout") ||
      message.includes("failed to fetch") ||
      message.includes("status 5") ||
      message.includes("503") ||
      message.includes("service unavailable") ||
      message.includes("being processed")
    );
  }

  private async flushInternal(token: string, currentUserId: string, handlers: FlushHandlers) {
    const queuedMessages = await localDatabase.listOutboxMessages(currentUserId);

    for (const queued of queuedMessages) {
      try {
        const syncedMessage = await this.sendQueuedOperation(token, queued);
        await localDatabase.removeOutboxMessage(queued.currentUserId, queued.clientMessageId);
        await localDatabase.replaceQueuedMessage(
          queued.currentUserId,
          queued.chatId,
          toQueuedMessageId(queued.clientMessageId),
          syncedMessage
        );
        handlers.onSynced?.(toQueuedMessageId(queued.clientMessageId), syncedMessage);
      } catch (error) {
        if (this.isRetryable(error)) {
          await localDatabase.updateOutboxError(
            queued.currentUserId,
            queued.clientMessageId,
            error instanceof Error ? error.message : "retryable_error"
          );
          break;
        }

        await localDatabase.removeOutboxMessage(queued.currentUserId, queued.clientMessageId);
        await localDatabase.removeMessage(
          queued.currentUserId,
          queued.chatId,
          toQueuedMessageId(queued.clientMessageId)
        );
        await Promise.all(
          queued.attachments
            .filter((attachment) => isPendingLocalAttachment(attachment))
            .map((attachment) => cleanupStagedAttachment(attachment).catch(() => undefined))
        );
        handlers.onDropped?.(toQueuedMessageId(queued.clientMessageId), queued.chatId);
      }
    }
  }

  private buildOptimisticMessage(
    params: QueueOutgoingParams,
    createdAt: string
  ): ChatMessage {
    const clientMessageId = this.getClientMessageId(params.operation);
    if (!clientMessageId) {
      throw new Error("Optimistic messages require clientMessageId");
    }

    return {
      chatId: params.chatId,
      messageId: toQueuedMessageId(clientMessageId),
      clientMessageId,
      senderId: params.currentUserId,
      displaySenderName: params.optimistic.displaySenderName ?? null,
      displaySenderPhotoUrl: params.optimistic.displaySenderPhotoUrl ?? null,
      displaySenderPhotoAccessExpiresAt:
        params.optimistic.displaySenderPhotoAccessExpiresAt ?? null,
      anonymousSender: params.optimistic.anonymousSender ?? false,
      recipientId: null,
      viaBotUserId: params.optimistic.viaBotUserId ?? null,
      topicId: params.operation.request.topicId ?? null,
      threadRootMessageId: params.optimistic.threadRootMessageId ?? null,
      discussionChatId: params.optimistic.discussionChatId ?? null,
      discussionRootMessageId: params.optimistic.discussionRootMessageId ?? null,
      commentCount: 0,
      text: params.optimistic.text ?? "",
      entities: params.optimistic.entities ?? [],
      messageType: params.optimistic.messageType ?? "TEXT",
      caption: params.optimistic.caption ?? null,
      silent: params.optimistic.silent ?? false,
      liveLocation: params.optimistic.liveLocation ?? null,
      location: params.optimistic.location ?? null,
      contactCard: params.optimistic.contactCard ?? null,
      serviceMessage: params.optimistic.serviceMessage ?? null,
      createdAt,
      replyToMessageId: params.optimistic.replyToMessageId ?? null,
      forwardedFromChatId: params.optimistic.forwardedFromChatId ?? null,
      forwardedFromMessageId: params.optimistic.forwardedFromMessageId ?? null,
      poll: params.optimistic.poll ?? null,
      sticker: params.optimistic.sticker ?? null,
      attachments: params.optimistic.attachments ?? params.attachments,
      reactions: [],
      deliveryStatus: "QUEUED",
      deliveredAt: null,
      readAt: null,
      expiresAt: null,
      editedAt: null,
      deletedAt: null
    };
  }

  private getClientMessageId(operation: OutboxOperation) {
    return operation.request.clientMessageId;
  }

  private async sendQueuedOperation(
    token: string,
    queued: Awaited<ReturnType<typeof localDatabase.listOutboxMessages>>[number]
  ) {
    switch (queued.operation.kind) {
      case "SEND_MESSAGE": {
        const attachmentIds: string[] = [];
        for (const attachment of queued.attachments) {
          const resolvedAttachment = isPendingLocalAttachment(attachment)
            ? await uploadPendingAttachment(token, queued.chatId, attachment)
            : attachment;
          attachmentIds.push(resolvedAttachment.attachmentId);
        }
        return api.sendMessage(token, {
          ...queued.operation.request,
          attachmentIds
        });
      }
      case "CREATE_POLL_MESSAGE":
        return api.createPollMessage(token, queued.operation.request);
      case "FORWARD_MESSAGE":
        return api.forwardMessage(token, queued.operation.request);
      case "SEND_INLINE_BOT_RESULT":
        return api.sendInlineBotResult(token, queued.operation.request);
      default:
        throw new Error("Unsupported queued operation");
    }
  }
}

export const messageOutbox = new MessageOutboxService();
