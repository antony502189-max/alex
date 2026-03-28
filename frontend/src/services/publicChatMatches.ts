import type { ParsedDeepLink } from "../navigation/deepLinks";
import type { ChatSummary } from "../types";

export function findExactPublicChatMatch(
  chats: ChatSummary[],
  parsedLink: ParsedDeepLink | null
) {
  if (!parsedLink || parsedLink.type !== "JOIN" || !parsedLink.token.startsWith("@")) {
    return null;
  }

  const normalizedUsername = parsedLink.token.slice(1).toLocaleLowerCase();
  return chats.find((chat) => chat.publicUsername?.toLocaleLowerCase() === normalizedUsername) ?? null;
}
