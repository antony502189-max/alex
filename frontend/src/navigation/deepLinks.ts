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

function decodePathSegments(value: string) {
  return trimLeadingSlashes(value)
    .split("/")
    .filter(Boolean)
    .map((segment) => decodeURIComponent(segment));
}

function resolveCallTokenFromUrl(url: URL, pathSegments: string[]) {
  const fromPath = pathSegments.join("/").trim();
  if (fromPath) {
    return fromPath;
  }

  const fromQuery =
    url.searchParams.get("token")?.trim() ??
    url.searchParams.get("link")?.trim() ??
    url.searchParams.get("id")?.trim() ??
    "";
  return fromQuery;
}

function normalizeUrlLikeInput(value: string) {
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(value)) {
    return value;
  }

  if (/^(?:[a-z0-9-]+\.)+[a-z]{2,}(?:\/|$)/i.test(value)) {
    return `https://${value}`;
  }

  return value;
}

export function parseAlexDeepLink(rawUrl: string | null | undefined): ParsedDeepLink | null {
  if (!rawUrl) {
    return null;
  }

  const normalized = rawUrl.trim();
  if (!normalized) {
    return null;
  }

  if (normalized.startsWith("@")) {
    const username = normalized.slice(1).trim();
    return username ? { type: "JOIN", token: `@${username}` } : null;
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
    const url = new URL(normalizeUrlLikeInput(normalized));
    const host = url.hostname.toLowerCase();
    const path = trimLeadingSlashes(url.pathname);
    const pathSegments = decodePathSegments(url.pathname);
    const topicId = url.searchParams.get("topicId");

    if (url.protocol === "alex:") {
      if (host === "join" && path) {
        return { type: "JOIN", token: decodeURIComponent(path).replace(/^\+/, "") };
      }
      if (host === "call") {
        const token = resolveCallTokenFromUrl(url, pathSegments);
        return token ? { type: "CALL", token } : null;
      }
      if (host === "chat" && path) {
        return {
          type: "CHAT",
          chatId: decodeURIComponent(path),
          topicId: topicId && topicId.trim() ? topicId : null
        };
      }
    }

    if (url.protocol === "tg:" || url.protocol === "telegram:") {
      if (host === "join") {
        const invite = url.searchParams.get("invite")?.trim().replace(/^\+/, "") ?? "";
        return invite ? { type: "JOIN", token: invite } : null;
      }
      if (host === "call") {
        const token = resolveCallTokenFromUrl(url, pathSegments);
        return token ? { type: "CALL", token } : null;
      }
      if (host === "resolve") {
        const domain = url.searchParams.get("domain")?.trim().replace(/^@+/, "") ?? "";
        return domain ? { type: "JOIN", token: `@${domain}` } : null;
      }
    }

    if (
      ["t.me", "telegram.me", "www.t.me", "www.telegram.me"].includes(host) &&
      pathSegments.length > 0
    ) {
      const [first] = pathSegments;
      if (first === "call") {
        const token = resolveCallTokenFromUrl(url, pathSegments.slice(1));
        return token ? { type: "CALL", token } : null;
      }
      const [, second] = pathSegments;
      if ((first === "join" || first === "joinchat") && second) {
        return { type: "JOIN", token: second.replace(/^\+/, "") };
      }
      if (first.startsWith("+")) {
        return { type: "JOIN", token: first.slice(1) };
      }
      if (pathSegments.length === 1) {
        return { type: "JOIN", token: `@${first.replace(/^@+/, "")}` };
      }
    }

    if (pathSegments[0] === "join" && pathSegments[1]) {
      return {
        type: "JOIN",
        token: pathSegments[1].replace(/^\+/, "")
      };
    }
    if (pathSegments[0] === "call") {
      const token = resolveCallTokenFromUrl(url, pathSegments.slice(1));
      return token ? { type: "CALL", token } : null;
    }
    if (pathSegments[0] === "chat" && pathSegments[1]) {
      const topicId = url.searchParams.get("topicId");
      return {
        type: "CHAT",
        chatId: pathSegments[1],
        topicId: topicId && topicId.trim() ? topicId : null
      };
    }
  } catch {
  }

  return null;
}
