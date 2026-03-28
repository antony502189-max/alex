import type {
  ChatSummary,
  Contact,
  UserPresenceStatus,
  UserSearchResult
} from "../types";

type PresenceMap = Record<string, UserPresenceStatus>;

function buildPresenceMap(statuses: UserPresenceStatus[]): PresenceMap {
  return statuses.reduce<PresenceMap>((result, status) => {
    result[status.userId] = status;
    return result;
  }, {});
}

export function getUniquePresenceUserIds(userIds: Array<string | null | undefined>) {
  return [
    ...new Set(
      userIds
        .map((userId) => userId?.trim() ?? "")
        .filter((userId) => userId.length > 0)
    )
  ];
}

export function getDirectChatPresenceUserIds(chats: ChatSummary[]) {
  return getUniquePresenceUserIds(
    chats
      .filter((chat) => chat.chatType === "DIRECT")
      .map((chat) => chat.peerUserId)
  );
}

export function mergePresenceIntoChats(
  chats: ChatSummary[],
  statuses: UserPresenceStatus[]
) {
  const presenceByUserId = buildPresenceMap(statuses);

  return chats.map((chat) => {
    if (chat.chatType !== "DIRECT" || !chat.peerUserId) {
      return chat;
    }

    const presence = presenceByUserId[chat.peerUserId];
    if (
      !presence ||
      (chat.peerOnline === presence.online &&
        chat.peerLastSeenAt === presence.lastSeenAt)
    ) {
      return chat;
    }

    return {
      ...chat,
      peerOnline: presence.online,
      peerLastSeenAt: presence.lastSeenAt
    };
  });
}

export function mergePresenceIntoContacts(
  contacts: Contact[],
  statuses: UserPresenceStatus[]
) {
  const presenceByUserId = buildPresenceMap(statuses);

  return contacts.map((contact) => {
    const presence = presenceByUserId[contact.userId];
    if (
      !presence ||
      (contact.online === presence.online && contact.lastSeenAt === presence.lastSeenAt)
    ) {
      return contact;
    }

    return {
      ...contact,
      online: presence.online,
      lastSeenAt: presence.lastSeenAt
    };
  });
}

export function mergePresenceIntoUserResults(
  results: UserSearchResult[],
  statuses: UserPresenceStatus[]
) {
  const presenceByUserId = buildPresenceMap(statuses);

  return results.map((result) => {
    const presence = presenceByUserId[result.userId];
    if (
      !presence ||
      (result.online === presence.online && result.lastSeenAt === presence.lastSeenAt)
    ) {
      return result;
    }

    return {
      ...result,
      online: presence.online,
      lastSeenAt: presence.lastSeenAt
    };
  });
}

export function mapPresenceByUserId(statuses: UserPresenceStatus[]) {
  return buildPresenceMap(statuses);
}
