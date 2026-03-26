export type ParsedDeepLink =
  | {
      type: "JOIN";
      token: string;
    }
  | {
      type: "CALL";
      token: string;
    }
  | {
      type: "CHAT";
      chatId: string;
      topicId: string | null;
    };

function trimLeadingSlashes(value: string) {
  return value.replace(/^\/+/, "");
}

export function parseAlexDeepLink(rawUrl: string | null | undefined): ParsedDeepLink | null {
  if (!rawUrl) {
    return null;
  }

  const normalized = rawUrl.trim();
  if (!normalized) {
    return null;
  }

  if (normalized.startsWith("alex://join/")) {
    const token = normalized.slice("alex://join/".length).trim();
    return token ? { type: "JOIN", token } : null;
  }

  if (normalized.startsWith("alex://call/")) {
    const token = normalized.slice("alex://call/".length).trim();
    return token ? { type: "CALL", token } : null;
  }

  try {
    const url = new URL(normalized);
    if (url.protocol !== "alex:") {
      return null;
    }

    const host = url.hostname.toLowerCase();
    const path = trimLeadingSlashes(url.pathname);
    if (host === "join" && path) {
      return { type: "JOIN", token: decodeURIComponent(path) };
    }
    if (host === "call" && path) {
      return { type: "CALL", token: decodeURIComponent(path) };
    }
    if (host === "chat" && path) {
      const topicId = url.searchParams.get("topicId");
      return {
        type: "CHAT",
        chatId: decodeURIComponent(path),
        topicId: topicId && topicId.trim() ? topicId : null
      };
    }
  } catch {
  }

  return null;
}
