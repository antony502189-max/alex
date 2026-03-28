import * as SQLite from "expo-sqlite";
import type {
  CallHistoryEntry,
  ForumTopic,
  SecretChatMessage,
  SecretChatSummary,
  ChatFolder,
  ChatMessage,
  ChatSummary,
  MessageAttachment,
  ScheduledMessage
} from "../types";
import type { DeferredMessagePayload, OutboxOperation, SendMessagePayload } from "./api";

export type OutboxMessageRecord = {
  clientMessageId: string;
  chatId: string;
  currentUserId: string;
  createdAt: string;
  operation: OutboxOperation;
  attachments: MessageAttachment[];
  lastError: string | null;
};

export type ScheduledOutboxRecord = {
  clientMessageId: string;
  chatId: string;
  currentUserId: string;
  createdAt: string;
  payload: DeferredMessagePayload;
  attachments: MessageAttachment[];
  lastError: string | null;
};

class LocalDatabaseService {
  private databasePromise?: Promise<SQLite.SQLiteDatabase>;

  private normalizeMessages(messages: ChatMessage[]) {
    const map = new Map<string, ChatMessage>();

    function buildKey(message: ChatMessage) {
      return message.clientMessageId
        ? `client:${message.clientMessageId}`
        : `message:${message.messageId}`;
    }

    function preferMessage(left: ChatMessage, right: ChatMessage) {
      if (left.deliveryStatus === "QUEUED" && right.deliveryStatus !== "QUEUED") {
        return right;
      }
      if (right.deliveryStatus === "QUEUED" && left.deliveryStatus !== "QUEUED") {
        return left;
      }
      return right.createdAt.localeCompare(left.createdAt) >= 0 ? right : left;
    }

    for (const message of messages) {
      const key = buildKey(message);
      const existing = map.get(key);
      map.set(key, existing ? preferMessage(existing, message) : message);
    }

    return [...map.values()].sort((left, right) =>
      left.createdAt.localeCompare(right.createdAt)
    );
  }

  private normalizeScheduledMessages(messages: ScheduledMessage[]) {
    const map = new Map<string, ScheduledMessage>();

    function buildKey(message: ScheduledMessage) {
      return message.clientMessageId
        ? `client:${message.clientMessageId}`
        : `scheduled:${message.scheduledMessageId}`;
    }

    function preferMessage(left: ScheduledMessage, right: ScheduledMessage) {
      if (left.status === "QUEUED" && right.status !== "QUEUED") {
        return right;
      }
      if (right.status === "QUEUED" && left.status !== "QUEUED") {
        return left;
      }
      return right.createdAt.localeCompare(left.createdAt) >= 0 ? right : left;
    }

    for (const message of messages) {
      const key = buildKey(message);
      const existing = map.get(key);
      map.set(key, existing ? preferMessage(existing, message) : message);
    }

    return [...map.values()].sort((left, right) =>
      left.scheduledAt.localeCompare(right.scheduledAt)
    );
  }

  async init() {
    await this.getDatabase();
  }

  async getChats(currentUserId: string): Promise<ChatSummary[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_chats_v2
        WHERE current_user_id = ?
        ORDER BY updated_at DESC
      `,
      [currentUserId]
    );
    return rows.map((row) => JSON.parse(row.payload) as ChatSummary);
  }

  async upsertChats(currentUserId: string, chats: ChatSummary[]) {
    if (chats.length === 0) {
      return;
    }

    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      for (const chat of chats) {
        await database.runAsync(
          `
            INSERT INTO cached_chats_v2 (current_user_id, chat_id, updated_at, payload)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(current_user_id, chat_id) DO UPDATE SET
              updated_at = excluded.updated_at,
              payload = excluded.payload
          `,
          [currentUserId, chat.chatId, chat.lastMessageAt, JSON.stringify(chat)]
        );
      }
    });
  }

  async replaceChats(currentUserId: string, chats: ChatSummary[]) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      if (chats.length === 0) {
        await database.runAsync(
          "DELETE FROM cached_chats_v2 WHERE current_user_id = ?",
          [currentUserId]
        );
        return;
      }

      const placeholders = chats.map(() => "?").join(", ");
      await database.runAsync(
        `
          DELETE FROM cached_chats_v2
          WHERE current_user_id = ?
            AND chat_id NOT IN (${placeholders})
        `,
        [currentUserId, ...chats.map((chat) => chat.chatId)]
      );

      for (const chat of chats) {
        await database.runAsync(
          `
            INSERT INTO cached_chats_v2 (current_user_id, chat_id, updated_at, payload)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(current_user_id, chat_id) DO UPDATE SET
              updated_at = excluded.updated_at,
              payload = excluded.payload
          `,
          [currentUserId, chat.chatId, chat.lastMessageAt, JSON.stringify(chat)]
        );
      }
    });
  }

  async getFolders(currentUserId: string): Promise<ChatFolder[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_folders_v1
        WHERE current_user_id = ?
        ORDER BY position ASC
      `,
      [currentUserId]
    );
    return rows.map((row) => JSON.parse(row.payload) as ChatFolder);
  }

  async getForumTopics(currentUserId: string, chatId: string): Promise<ForumTopic[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_forum_topics_v1
        WHERE current_user_id = ? AND chat_id = ?
        ORDER BY updated_at DESC
      `,
      [currentUserId, chatId]
    );
    return rows.map((row) => JSON.parse(row.payload) as ForumTopic);
  }

  async getRecentCalls(currentUserId: string): Promise<CallHistoryEntry[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_recent_calls_v1
        WHERE current_user_id = ?
        ORDER BY activity_at DESC
      `,
      [currentUserId]
    );
    return rows.map((row) => JSON.parse(row.payload) as CallHistoryEntry);
  }

  async getSecretChats(currentUserId: string): Promise<SecretChatSummary[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_secret_chats_v1
        WHERE current_user_id = ?
        ORDER BY updated_at DESC
      `,
      [currentUserId]
    );
    return rows.map((row) => JSON.parse(row.payload) as SecretChatSummary);
  }

  async getSecretChatMessages(currentUserId: string, secretChatId: string, limit = 100): Promise<SecretChatMessage[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_secret_messages_v1
        WHERE current_user_id = ? AND secret_chat_id = ?
        ORDER BY created_at DESC
        LIMIT ?
      `,
      [currentUserId, secretChatId, limit]
    );
    return rows.map((row) => JSON.parse(row.payload) as SecretChatMessage).reverse();
  }

  async replaceFolders(currentUserId: string, folders: ChatFolder[]) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      if (folders.length === 0) {
        await database.runAsync(
          "DELETE FROM cached_folders_v1 WHERE current_user_id = ?",
          [currentUserId]
        );
        return;
      }

      const placeholders = folders.map(() => "?").join(", ");
      await database.runAsync(
        `
          DELETE FROM cached_folders_v1
          WHERE current_user_id = ?
            AND folder_id NOT IN (${placeholders})
        `,
        [currentUserId, ...folders.map((folder) => folder.folderId)]
      );

      for (const folder of folders) {
        await database.runAsync(
          `
            INSERT INTO cached_folders_v1 (current_user_id, folder_id, position, payload)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(current_user_id, folder_id) DO UPDATE SET
              position = excluded.position,
              payload = excluded.payload
          `,
          [currentUserId, folder.folderId, folder.position, JSON.stringify(folder)]
        );
      }
    });
  }

  async replaceForumTopics(currentUserId: string, chatId: string, topics: ForumTopic[]) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      await database.runAsync(
        "DELETE FROM cached_forum_topics_v1 WHERE current_user_id = ? AND chat_id = ?",
        [currentUserId, chatId]
      );

      for (const topic of topics) {
        const updatedAt = topic.lastMessageAt ?? topic.updatedAt;
        await database.runAsync(
          `
            INSERT INTO cached_forum_topics_v1 (current_user_id, topic_id, chat_id, updated_at, payload)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(current_user_id, topic_id) DO UPDATE SET
              chat_id = excluded.chat_id,
              updated_at = excluded.updated_at,
              payload = excluded.payload
          `,
          [currentUserId, topic.topicId, topic.chatId, updatedAt, JSON.stringify(topic)]
        );
      }
    });
  }

  async upsertForumTopics(currentUserId: string, topics: ForumTopic[]) {
    if (topics.length === 0) {
      return;
    }

    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      for (const topic of topics) {
        const updatedAt = topic.lastMessageAt ?? topic.updatedAt;
        await database.runAsync(
          `
            INSERT INTO cached_forum_topics_v1 (current_user_id, topic_id, chat_id, updated_at, payload)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(current_user_id, topic_id) DO UPDATE SET
              chat_id = excluded.chat_id,
              updated_at = excluded.updated_at,
              payload = excluded.payload
          `,
          [currentUserId, topic.topicId, topic.chatId, updatedAt, JSON.stringify(topic)]
        );
      }
    });
  }

  async replaceRecentCalls(currentUserId: string, calls: CallHistoryEntry[]) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      if (calls.length === 0) {
        await database.runAsync(
          "DELETE FROM cached_recent_calls_v1 WHERE current_user_id = ?",
          [currentUserId]
        );
        return;
      }

      const placeholders = calls.map(() => "?").join(", ");
      await database.runAsync(
        `
          DELETE FROM cached_recent_calls_v1
          WHERE current_user_id = ?
            AND call_id NOT IN (${placeholders})
        `,
        [currentUserId, ...calls.map((call) => call.callId)]
      );

      for (const call of calls) {
        const activityAt = call.endedAt ?? call.answeredAt ?? call.startedAt;
        await database.runAsync(
          `
            INSERT INTO cached_recent_calls_v1 (current_user_id, call_id, activity_at, payload)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(current_user_id, call_id) DO UPDATE SET
              activity_at = excluded.activity_at,
              payload = excluded.payload
          `,
          [currentUserId, call.callId, activityAt, JSON.stringify(call)]
        );
      }
    });
  }

  async replaceSecretChats(currentUserId: string, secretChats: SecretChatSummary[]) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      if (secretChats.length === 0) {
        await database.runAsync(
          "DELETE FROM cached_secret_chats_v1 WHERE current_user_id = ?",
          [currentUserId]
        );
        return;
      }

      const placeholders = secretChats.map(() => "?").join(", ");
      await database.runAsync(
        `
          DELETE FROM cached_secret_chats_v1
          WHERE current_user_id = ?
            AND secret_chat_id NOT IN (${placeholders})
        `,
        [currentUserId, ...secretChats.map((chat) => chat.secretChatId)]
      );

      for (const secretChat of secretChats) {
        const updatedAt = secretChat.lastMessageAt ?? secretChat.acceptedAt ?? secretChat.createdAt;
        await database.runAsync(
          `
            INSERT INTO cached_secret_chats_v1 (current_user_id, secret_chat_id, updated_at, payload)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(current_user_id, secret_chat_id) DO UPDATE SET
              updated_at = excluded.updated_at,
              payload = excluded.payload
          `,
          [currentUserId, secretChat.secretChatId, updatedAt, JSON.stringify(secretChat)]
        );
      }
    });
  }

  async upsertSecretChat(currentUserId: string, secretChat: SecretChatSummary) {
    const database = await this.getDatabase();
    const updatedAt = secretChat.lastMessageAt ?? secretChat.acceptedAt ?? secretChat.createdAt;
    await database.runAsync(
      `
        INSERT INTO cached_secret_chats_v1 (current_user_id, secret_chat_id, updated_at, payload)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(current_user_id, secret_chat_id) DO UPDATE SET
          updated_at = excluded.updated_at,
          payload = excluded.payload
      `,
      [currentUserId, secretChat.secretChatId, updatedAt, JSON.stringify(secretChat)]
    );
  }

  async removeSecretChat(currentUserId: string, secretChatId: string) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      await database.runAsync(
        "DELETE FROM cached_secret_messages_v1 WHERE current_user_id = ? AND secret_chat_id = ?",
        [currentUserId, secretChatId]
      );
      await database.runAsync(
        "DELETE FROM cached_secret_chats_v1 WHERE current_user_id = ? AND secret_chat_id = ?",
        [currentUserId, secretChatId]
      );
    });
  }

  async removeSecretChatMessages(currentUserId: string, secretChatId: string) {
    const database = await this.getDatabase();
    await database.runAsync(
      "DELETE FROM cached_secret_messages_v1 WHERE current_user_id = ? AND secret_chat_id = ?",
      [currentUserId, secretChatId]
    );
  }

  async clearSecretState(currentUserId: string) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      await database.runAsync(
        "DELETE FROM cached_secret_messages_v1 WHERE current_user_id = ?",
        [currentUserId]
      );
      await database.runAsync(
        "DELETE FROM cached_secret_chats_v1 WHERE current_user_id = ?",
        [currentUserId]
      );
    });
  }

  async replaceSecretChatMessages(currentUserId: string, secretChatId: string, messages: SecretChatMessage[]) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      await database.runAsync(
        "DELETE FROM cached_secret_messages_v1 WHERE current_user_id = ? AND secret_chat_id = ?",
        [currentUserId, secretChatId]
      );

      for (const message of messages) {
        await database.runAsync(
          `
            INSERT INTO cached_secret_messages_v1 (
              current_user_id,
              secret_chat_id,
              secret_message_id,
              created_at,
              payload
            )
            VALUES (?, ?, ?, ?, ?)
          `,
          [
            currentUserId,
            message.secretChatId,
            message.secretMessageId,
            message.createdAt,
            JSON.stringify(message)
          ]
        );
      }
    });
  }

  async upsertSecretChatMessages(currentUserId: string, messages: SecretChatMessage[]) {
    if (messages.length === 0) {
      return;
    }

    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      for (const message of messages) {
        await database.runAsync(
          `
            INSERT INTO cached_secret_messages_v1 (
              current_user_id,
              secret_chat_id,
              secret_message_id,
              created_at,
              payload
            )
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(current_user_id, secret_message_id) DO UPDATE SET
              secret_chat_id = excluded.secret_chat_id,
              created_at = excluded.created_at,
              payload = excluded.payload
          `,
          [
            currentUserId,
            message.secretChatId,
            message.secretMessageId,
            message.createdAt,
            JSON.stringify(message)
          ]
        );
      }
    });
  }

  async getMessages(
    currentUserId: string,
    chatId: string,
    limit = 100,
    topicId?: string | null,
    threadRootMessageId?: string | null
  ): Promise<ChatMessage[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_messages_v2
        WHERE current_user_id = ? AND chat_id = ?
        ORDER BY created_at ASC
      `,
      [currentUserId, chatId]
    );

    const filteredMessages = rows
      .map((row) => JSON.parse(row.payload) as ChatMessage)
      .filter((message) => (topicId ?? null) === (message.topicId ?? null))
      .filter((message) =>
        threadRootMessageId != null
          ? (message.threadRootMessageId ?? null) === threadRootMessageId
          : true
      );

    return this.normalizeMessages(limit > 0 ? filteredMessages.slice(-limit) : filteredMessages);
  }

  async getScheduledMessages(
    currentUserId: string,
    chatId: string,
    topicId?: string | null,
    threadRootMessageId?: string | null
  ): Promise<ScheduledMessage[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{ payload: string }>(
      `
        SELECT payload
        FROM cached_scheduled_messages_v1
        WHERE current_user_id = ? AND chat_id = ?
        ORDER BY scheduled_at ASC
      `,
      [currentUserId, chatId]
    );

    return this.normalizeScheduledMessages(
      rows
        .map((row) => JSON.parse(row.payload) as ScheduledMessage)
        .filter((message) => (topicId ?? null) === (message.topicId ?? null))
        .filter((message) =>
          threadRootMessageId != null
            ? (message.threadRootMessageId ?? null) === threadRootMessageId
            : true
        )
    );
  }

  async upsertMessages(currentUserId: string, messages: ChatMessage[]) {
    if (messages.length === 0) {
      return;
    }

    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      for (const message of messages) {
        await database.runAsync(
          `
            INSERT INTO cached_messages_v2 (current_user_id, message_id, chat_id, created_at, payload)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(current_user_id, message_id) DO UPDATE SET
              chat_id = excluded.chat_id,
              created_at = excluded.created_at,
              payload = excluded.payload
          `,
          [currentUserId, message.messageId, message.chatId, message.createdAt, JSON.stringify(message)]
        );
      }
    });
  }

  async replaceQueuedMessage(
    currentUserId: string,
    chatId: string,
    queuedMessageId: string,
    message: ChatMessage
  ) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      await database.runAsync(
        "DELETE FROM cached_messages_v2 WHERE current_user_id = ? AND message_id = ? AND chat_id = ?",
        [currentUserId, queuedMessageId, chatId]
      );
      await database.runAsync(
        `
          INSERT INTO cached_messages_v2 (current_user_id, message_id, chat_id, created_at, payload)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT(current_user_id, message_id) DO UPDATE SET
            chat_id = excluded.chat_id,
            created_at = excluded.created_at,
            payload = excluded.payload
        `,
        [currentUserId, message.messageId, message.chatId, message.createdAt, JSON.stringify(message)]
      );
    });
  }

  async upsertScheduledMessages(currentUserId: string, messages: ScheduledMessage[]) {
    if (messages.length === 0) {
      return;
    }

    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      for (const message of messages) {
        await database.runAsync(
          `
            INSERT INTO cached_scheduled_messages_v1 (
              current_user_id,
              scheduled_message_id,
              chat_id,
              scheduled_at,
              payload
            )
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(current_user_id, scheduled_message_id) DO UPDATE SET
              chat_id = excluded.chat_id,
              scheduled_at = excluded.scheduled_at,
              payload = excluded.payload
          `,
          [
            currentUserId,
            message.scheduledMessageId,
            message.chatId,
            message.scheduledAt,
            JSON.stringify(message)
          ]
        );
      }
    });
  }

  async replaceScheduledMessages(
    currentUserId: string,
    chatId: string,
    messages: ScheduledMessage[],
    topicId?: string | null,
    threadRootMessageId?: string | null
  ) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      const normalizedMessages = this.normalizeScheduledMessages(messages);
      let messagesToPersist = normalizedMessages;

      if (topicId != null || threadRootMessageId != null) {
        const existingRows = await database.getAllAsync<{ payload: string }>(
          `
            SELECT payload
            FROM cached_scheduled_messages_v1
            WHERE current_user_id = ? AND chat_id = ?
            ORDER BY scheduled_at ASC
          `,
          [currentUserId, chatId]
        );
        const preservedMessages = this.normalizeScheduledMessages(
          existingRows
            .map((row) => JSON.parse(row.payload) as ScheduledMessage)
            .filter((message) => {
              const sameTopic = (message.topicId ?? null) === (topicId ?? null);
              const sameThread =
                (message.threadRootMessageId ?? null) === (threadRootMessageId ?? null);
              return !(sameTopic && sameThread);
            })
        );
        messagesToPersist = this.normalizeScheduledMessages([
          ...preservedMessages,
          ...normalizedMessages
        ]);
      }

      await database.runAsync(
        "DELETE FROM cached_scheduled_messages_v1 WHERE current_user_id = ? AND chat_id = ?",
        [currentUserId, chatId]
      );

      for (const message of messagesToPersist) {
        await database.runAsync(
          `
            INSERT INTO cached_scheduled_messages_v1 (
              current_user_id,
              scheduled_message_id,
              chat_id,
              scheduled_at,
              payload
            )
            VALUES (?, ?, ?, ?, ?)
          `,
          [
            currentUserId,
            message.scheduledMessageId,
            message.chatId,
            message.scheduledAt,
            JSON.stringify(message)
          ]
        );
      }
    });
  }

  async replaceQueuedScheduledMessage(
    currentUserId: string,
    chatId: string,
    queuedScheduledMessageId: string,
    message: ScheduledMessage
  ) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      await database.runAsync(
        "DELETE FROM cached_scheduled_messages_v1 WHERE current_user_id = ? AND scheduled_message_id = ? AND chat_id = ?",
        [currentUserId, queuedScheduledMessageId, chatId]
      );
      await database.runAsync(
        `
          INSERT INTO cached_scheduled_messages_v1 (
            current_user_id,
            scheduled_message_id,
            chat_id,
            scheduled_at,
            payload
          )
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT(current_user_id, scheduled_message_id) DO UPDATE SET
            chat_id = excluded.chat_id,
            scheduled_at = excluded.scheduled_at,
            payload = excluded.payload
        `,
        [
          currentUserId,
          message.scheduledMessageId,
          message.chatId,
          message.scheduledAt,
          JSON.stringify(message)
        ]
      );
    });
  }

  async removeScheduledMessage(currentUserId: string, chatId: string, scheduledMessageId: string) {
    const database = await this.getDatabase();
    await database.runAsync(
      "DELETE FROM cached_scheduled_messages_v1 WHERE current_user_id = ? AND scheduled_message_id = ? AND chat_id = ?",
      [currentUserId, scheduledMessageId, chatId]
    );
  }

  async removeMessage(currentUserId: string, chatId: string, messageId: string) {
    const database = await this.getDatabase();
    await database.runAsync(
      "DELETE FROM cached_messages_v2 WHERE current_user_id = ? AND message_id = ? AND chat_id = ?",
      [currentUserId, messageId, chatId]
    );
  }

  async enqueueOutbox(record: OutboxMessageRecord) {
    const database = await this.getDatabase();
    await database.runAsync(
      `
        INSERT INTO outbox_messages_v2 (
          client_message_id,
          chat_id,
          current_user_id,
          created_at,
          payload,
          attachments,
          last_error
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(current_user_id, client_message_id) DO UPDATE SET
          chat_id = excluded.chat_id,
          current_user_id = excluded.current_user_id,
          created_at = excluded.created_at,
          payload = excluded.payload,
          attachments = excluded.attachments,
          last_error = excluded.last_error
      `,
      [
        record.clientMessageId,
        record.chatId,
        record.currentUserId,
        record.createdAt,
        JSON.stringify(record.operation),
        JSON.stringify(record.attachments),
        record.lastError
      ]
    );
  }

  async listOutboxMessages(currentUserId: string): Promise<OutboxMessageRecord[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{
      client_message_id: string;
      chat_id: string;
      current_user_id: string;
      created_at: string;
      payload: string;
      attachments: string;
      last_error: string | null;
    }>(
      `
        SELECT client_message_id, chat_id, current_user_id, created_at, payload, attachments, last_error
        FROM outbox_messages_v2
        WHERE current_user_id = ?
        ORDER BY created_at ASC
      `,
      [currentUserId]
    );

    return rows.map((row) => ({
      clientMessageId: row.client_message_id,
      chatId: row.chat_id,
      currentUserId: row.current_user_id,
      createdAt: row.created_at,
      operation: this.parseOutboxOperation(row.payload),
      attachments: JSON.parse(row.attachments) as MessageAttachment[],
      lastError: row.last_error
    }));
  }

  async enqueueScheduledOutbox(record: ScheduledOutboxRecord) {
    const database = await this.getDatabase();
    await database.runAsync(
      `
        INSERT INTO outbox_scheduled_messages_v1 (
          client_message_id,
          chat_id,
          current_user_id,
          created_at,
          payload,
          attachments,
          last_error
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(current_user_id, client_message_id) DO UPDATE SET
          chat_id = excluded.chat_id,
          current_user_id = excluded.current_user_id,
          created_at = excluded.created_at,
          payload = excluded.payload,
          attachments = excluded.attachments,
          last_error = excluded.last_error
      `,
      [
        record.clientMessageId,
        record.chatId,
        record.currentUserId,
        record.createdAt,
        JSON.stringify(record.payload),
        JSON.stringify(record.attachments),
        record.lastError
      ]
    );
  }

  async listScheduledOutboxMessages(currentUserId: string): Promise<ScheduledOutboxRecord[]> {
    const database = await this.getDatabase();
    const rows = await database.getAllAsync<{
      client_message_id: string;
      chat_id: string;
      current_user_id: string;
      created_at: string;
      payload: string;
      attachments: string;
      last_error: string | null;
    }>(
      `
        SELECT client_message_id, chat_id, current_user_id, created_at, payload, attachments, last_error
        FROM outbox_scheduled_messages_v1
        WHERE current_user_id = ?
        ORDER BY created_at ASC
      `,
      [currentUserId]
    );

    return rows.map((row) => ({
      clientMessageId: row.client_message_id,
      chatId: row.chat_id,
      currentUserId: row.current_user_id,
      createdAt: row.created_at,
      payload: JSON.parse(row.payload) as DeferredMessagePayload,
      attachments: JSON.parse(row.attachments) as MessageAttachment[],
      lastError: row.last_error
    }));
  }

  private parseOutboxOperation(serialized: string): OutboxOperation {
    const parsed = JSON.parse(serialized) as OutboxOperation | SendMessagePayload;
    if (
      typeof parsed === "object" &&
      parsed !== null &&
      "kind" in parsed &&
      "request" in parsed
    ) {
      return parsed as OutboxOperation;
    }

    return {
      kind: "SEND_MESSAGE",
      request: parsed as SendMessagePayload
    };
  }

  async removeOutboxMessage(currentUserId: string, clientMessageId: string) {
    const database = await this.getDatabase();
    await database.runAsync(
      "DELETE FROM outbox_messages_v2 WHERE current_user_id = ? AND client_message_id = ?",
      [currentUserId, clientMessageId]
    );
  }

  async updateOutboxError(currentUserId: string, clientMessageId: string, lastError: string | null) {
    const database = await this.getDatabase();
    await database.runAsync(
      "UPDATE outbox_messages_v2 SET last_error = ? WHERE current_user_id = ? AND client_message_id = ?",
      [lastError, currentUserId, clientMessageId]
    );
  }

  async removeScheduledOutboxMessage(currentUserId: string, clientMessageId: string) {
    const database = await this.getDatabase();
    await database.runAsync(
      "DELETE FROM outbox_scheduled_messages_v1 WHERE current_user_id = ? AND client_message_id = ?",
      [currentUserId, clientMessageId]
    );
  }

  async updateScheduledOutboxError(currentUserId: string, clientMessageId: string, lastError: string | null) {
    const database = await this.getDatabase();
    await database.runAsync(
      "UPDATE outbox_scheduled_messages_v1 SET last_error = ? WHERE current_user_id = ? AND client_message_id = ?",
      [lastError, currentUserId, clientMessageId]
    );
  }

  async getSyncCursor(currentUserId: string): Promise<number | null> {
    const database = await this.getDatabase();
    const row = await database.getFirstAsync<{ cursor: number }>(
      `
        SELECT cursor
        FROM cached_sync_cursor_v1
        WHERE current_user_id = ?
      `,
      [currentUserId]
    );

    if (!row || typeof row.cursor !== "number" || !Number.isFinite(row.cursor)) {
      return null;
    }

    return row.cursor;
  }

  async setSyncCursor(currentUserId: string, cursor: number | null) {
    const database = await this.getDatabase();
    if (cursor == null || !Number.isFinite(cursor)) {
      await database.runAsync(
        "DELETE FROM cached_sync_cursor_v1 WHERE current_user_id = ?",
        [currentUserId]
      );
      return;
    }

    await database.runAsync(
      `
        INSERT INTO cached_sync_cursor_v1 (current_user_id, cursor, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT(current_user_id) DO UPDATE SET
          cursor = excluded.cursor,
          updated_at = excluded.updated_at
      `,
      [currentUserId, cursor, new Date().toISOString()]
    );
  }

  async clearSyncCursor(currentUserId: string) {
    await this.setSyncCursor(currentUserId, null);
  }

  async purgeAccountData(currentUserId: string) {
    const database = await this.getDatabase();
    await database.withTransactionAsync(async () => {
      const tables = [
        "cached_chats_v2",
        "cached_messages_v2",
        "cached_folders_v1",
        "cached_forum_topics_v1",
        "cached_recent_calls_v1",
        "cached_secret_chats_v1",
        "cached_secret_messages_v1",
        "cached_sync_cursor_v1",
        "cached_scheduled_messages_v1",
        "outbox_messages_v2",
        "outbox_scheduled_messages_v1"
      ];

      for (const table of tables) {
        await database.runAsync(
          `DELETE FROM ${table} WHERE current_user_id = ?`,
          [currentUserId]
        );
      }
    });
  }

  private async getDatabase() {
    if (!this.databasePromise) {
      this.databasePromise = this.openDatabase();
    }
    return this.databasePromise;
  }

  private async openDatabase() {
    const database = await SQLite.openDatabaseAsync("alex-offline.db");
    await database.execAsync(`
      PRAGMA journal_mode = WAL;
      CREATE TABLE IF NOT EXISTS cached_chats_v2 (
        current_user_id TEXT NOT NULL,
        chat_id TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, chat_id)
      );
      CREATE TABLE IF NOT EXISTS cached_messages_v2 (
        current_user_id TEXT NOT NULL,
        message_id TEXT NOT NULL,
        chat_id TEXT NOT NULL,
        created_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_messages_v2_chat_created
        ON cached_messages_v2 (current_user_id, chat_id, created_at DESC);
      CREATE TABLE IF NOT EXISTS cached_folders_v1 (
        current_user_id TEXT NOT NULL,
        folder_id TEXT NOT NULL,
        position INTEGER NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, folder_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_folders_v1_position
        ON cached_folders_v1 (current_user_id, position ASC);
      CREATE TABLE IF NOT EXISTS cached_forum_topics_v1 (
        current_user_id TEXT NOT NULL,
        topic_id TEXT NOT NULL,
        chat_id TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, topic_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_forum_topics_v1_chat_updated
        ON cached_forum_topics_v1 (current_user_id, chat_id, updated_at DESC);
      CREATE TABLE IF NOT EXISTS cached_recent_calls_v1 (
        current_user_id TEXT NOT NULL,
        call_id TEXT NOT NULL,
        activity_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, call_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_recent_calls_v1_activity
        ON cached_recent_calls_v1 (current_user_id, activity_at DESC);
      CREATE TABLE IF NOT EXISTS cached_secret_chats_v1 (
        current_user_id TEXT NOT NULL,
        secret_chat_id TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, secret_chat_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_secret_chats_v1_updated
        ON cached_secret_chats_v1 (current_user_id, updated_at DESC);
      CREATE TABLE IF NOT EXISTS cached_secret_messages_v1 (
        current_user_id TEXT NOT NULL,
        secret_chat_id TEXT NOT NULL,
        secret_message_id TEXT NOT NULL,
        created_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, secret_message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_secret_messages_v1_chat_created
        ON cached_secret_messages_v1 (current_user_id, secret_chat_id, created_at DESC);
      CREATE TABLE IF NOT EXISTS cached_sync_cursor_v1 (
        current_user_id TEXT NOT NULL,
        cursor INTEGER NOT NULL,
        updated_at TEXT NOT NULL,
        PRIMARY KEY (current_user_id)
      );
      CREATE TABLE IF NOT EXISTS cached_scheduled_messages_v1 (
        current_user_id TEXT NOT NULL,
        scheduled_message_id TEXT NOT NULL,
        chat_id TEXT NOT NULL,
        scheduled_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        PRIMARY KEY (current_user_id, scheduled_message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_cached_scheduled_messages_v1_chat_scheduled
        ON cached_scheduled_messages_v1 (current_user_id, chat_id, scheduled_at ASC);
      CREATE TABLE IF NOT EXISTS outbox_messages_v2 (
        client_message_id TEXT NOT NULL,
        chat_id TEXT NOT NULL,
        current_user_id TEXT NOT NULL,
        created_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        attachments TEXT NOT NULL,
        last_error TEXT,
        PRIMARY KEY (current_user_id, client_message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_outbox_messages_v2_created
        ON outbox_messages_v2 (current_user_id, created_at ASC);
      CREATE TABLE IF NOT EXISTS outbox_scheduled_messages_v1 (
        client_message_id TEXT NOT NULL,
        chat_id TEXT NOT NULL,
        current_user_id TEXT NOT NULL,
        created_at TEXT NOT NULL,
        payload TEXT NOT NULL,
        attachments TEXT NOT NULL,
        last_error TEXT,
        PRIMARY KEY (current_user_id, client_message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_outbox_scheduled_messages_v1_created
        ON outbox_scheduled_messages_v1 (current_user_id, created_at ASC);
    `);
    return database;
  }
}

export const localDatabase = new LocalDatabaseService();
