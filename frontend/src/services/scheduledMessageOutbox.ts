import { api } from "./api";
import type { DeferredMessagePayload } from "./api";
import { cleanupStagedAttachment, isPendingLocalAttachment, uploadPendingAttachment } from "./attachmentDrafts";
import { toQueuedScheduledMessageId } from "./clientMessageIds";
import { localDatabase } from "./localDatabase";
import type { MessageAttachment, ScheduledMessage } from "../types";

type QueueScheduledParams = {
  chatId: string;
  currentUserId: string;
  payload: DeferredMessagePayload;
  attachments: MessageAttachment[];
  threadRootMessageId?: string | null;
  discussionChatId?: string | null;
  discussionRootMessageId?: string | null;
  mode?: "SCHEDULED" | "WHEN_ONLINE";
};

type FlushHandlers = {
  onSynced?: (queuedScheduledMessageId: string, message: ScheduledMessage) => void;
  onDropped?: (queuedScheduledMessageId: string, chatId: string) => void;
};

class ScheduledMessageOutboxService {
  private flushPromise?: Promise<void>;

  async queueMessage(params: QueueScheduledParams): Promise<ScheduledMessage> {
    if (!params.payload.clientMessageId) {
      throw new Error("Queued scheduled messages require clientMessageId");
    }

    const createdAt = new Date().toISOString();
    const optimisticMessage = this.buildOptimisticMessage(params, createdAt);

    await localDatabase.enqueueScheduledOutbox({
      clientMessageId: params.payload.clientMessageId,
      chatId: params.chatId,
      currentUserId: params.currentUserId,
      createdAt,
      payload: {
        ...params.payload,
        deliveryMode: params.mode ?? params.payload.deliveryMode ?? "SCHEDULED"
      },
      attachments: params.attachments,
      lastError: null
    });
    await localDatabase.upsertScheduledMessages(params.currentUserId, [optimisticMessage]);

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

  async removeQueuedMessage(currentUserId: string, chatId: string, clientMessageId: string) {
    await localDatabase.removeScheduledOutboxMessage(currentUserId, clientMessageId);
    await localDatabase.removeScheduledMessage(
      currentUserId,
      chatId,
      toQueuedScheduledMessageId(clientMessageId)
    );
  }

  private async flushInternal(token: string, currentUserId: string, handlers: FlushHandlers) {
    const queuedMessages = await localDatabase.listScheduledOutboxMessages(currentUserId);

    for (const queued of queuedMessages) {
      try {
        const attachmentIds: string[] = [];
        for (const attachment of queued.attachments) {
          const resolvedAttachment = isPendingLocalAttachment(attachment)
            ? await uploadPendingAttachment(token, queued.chatId, attachment)
            : attachment;
          attachmentIds.push(resolvedAttachment.attachmentId);
        }

        const { deliveryMode = "SCHEDULED", ...payload } = queued.payload;
        const syncedMessage = deliveryMode === "WHEN_ONLINE"
          ? await api.sendWhenOnlineMessage(token, {
              ...payload,
              attachmentIds
            })
          : await api.scheduleMessage(token, {
              ...payload,
              scheduledAt: payload.scheduledAt ?? queued.createdAt,
              attachmentIds
            });
        await localDatabase.removeScheduledOutboxMessage(queued.currentUserId, queued.clientMessageId);
        await localDatabase.replaceQueuedScheduledMessage(
          queued.currentUserId,
          queued.chatId,
          toQueuedScheduledMessageId(queued.clientMessageId),
          syncedMessage
        );
        handlers.onSynced?.(toQueuedScheduledMessageId(queued.clientMessageId), syncedMessage);
      } catch (error) {
        if (this.isRetryable(error)) {
          await localDatabase.updateScheduledOutboxError(
            queued.currentUserId,
            queued.clientMessageId,
            error instanceof Error ? error.message : "retryable_error"
          );
          break;
        }

        await localDatabase.removeScheduledOutboxMessage(queued.currentUserId, queued.clientMessageId);
        await localDatabase.removeScheduledMessage(
          queued.currentUserId,
          queued.chatId,
          toQueuedScheduledMessageId(queued.clientMessageId)
        );
        await Promise.all(
          queued.attachments
            .filter((attachment) => isPendingLocalAttachment(attachment))
            .map((attachment) => cleanupStagedAttachment(attachment).catch(() => undefined))
        );
        handlers.onDropped?.(toQueuedScheduledMessageId(queued.clientMessageId), queued.chatId);
      }
    }
  }

  private buildOptimisticMessage(
    params: QueueScheduledParams,
    createdAt: string
  ): ScheduledMessage {
    const clientMessageId = params.payload.clientMessageId;
    if (!clientMessageId) {
      throw new Error("Optimistic scheduled messages require clientMessageId");
    }

    return {
      scheduledMessageId: toQueuedScheduledMessageId(clientMessageId),
      clientMessageId,
      chatId: params.chatId,
      senderId: params.currentUserId,
      topicId: params.payload.topicId ?? null,
      threadRootMessageId: params.threadRootMessageId ?? null,
      discussionChatId: params.discussionChatId ?? null,
      discussionRootMessageId: params.discussionRootMessageId ?? null,
      text: params.payload.text ?? "",
      entities: params.payload.entities ?? [],
      messageType: params.payload.messageType ?? "TEXT",
      caption: params.payload.caption ?? null,
      silent: params.payload.silent ?? false,
      location: params.payload.location ?? null,
      contactCard: params.payload.contactCard ?? null,
      serviceMessage: null,
      replyToMessageId: params.payload.replyToMessageId ?? null,
      stickerId: params.payload.stickerId ?? null,
      attachments: params.attachments,
      scheduledAt: params.payload.scheduledAt ?? createdAt,
      createdAt,
      status: "QUEUED"
    };
  }
}

export const scheduledMessageOutbox = new ScheduledMessageOutboxService();
