const TELEGRAM_HOSTS = new Set([
  "t.me",
  "telegram.me",
  "www.t.me",
  "www.telegram.me"
]);

function trimLeadingSlashes(value: string) {
  return value.replace(/^\/+/, "");
}

function decodePathSegments(value: string) {
  return trimLeadingSlashes(value)
    .split("/")
    .filter(Boolean)
    .map((segment) => decodeURIComponent(segment));
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

function normalizeUsername(value: string) {
  const normalized = value.trim().replace(/^@+/, "");
  return normalized ? `@${normalized}` : "";
}

function normalizeInvitePathSegments(segments: string[]) {
  if (segments.length === 0) {
    return "";
  }

  const [first, second] = segments;
  if ((first === "join" || first === "joinchat") && second) {
    return second.replace(/^\+/, "");
  }
  if (first.startsWith("+")) {
    return first.slice(1);
  }
  if (segments.length === 1) {
    return normalizeUsername(first);
  }
  return "";
}

function resolveCallTokenFromUrl(url: URL, segments: string[]) {
  const fromPath = segments.join("/").trim();
  if (fromPath) {
    return fromPath;
  }

  return (
    url.searchParams.get("token")?.trim() ??
    url.searchParams.get("link")?.trim() ??
    url.searchParams.get("id")?.trim() ??
    ""
  );
}

export function normalizeInviteToken(value: string) {
  const normalized = value.trim();
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("@")) {
    return normalizeUsername(normalized);
  }
  if (normalized.startsWith("alex://join/")) {
    return normalized.slice("alex://join/".length).trim().replace(/^\+/, "");
  }

  try {
    const url = new URL(normalizeUrlLikeInput(normalized));
    const host = url.hostname.toLowerCase();
    const segments = decodePathSegments(url.pathname);

    if (url.protocol === "alex:" && host === "join") {
      return segments.join("/").replace(/^\+/, "");
    }
    if (url.protocol === "tg:" || url.protocol === "telegram:") {
      if (host === "join") {
        return url.searchParams.get("invite")?.trim().replace(/^\+/, "") ?? "";
      }
      if (host === "resolve") {
        return normalizeUsername(url.searchParams.get("domain") ?? "");
      }
    }
    if (TELEGRAM_HOSTS.has(host)) {
      return normalizeInvitePathSegments(segments);
    }
    if (segments[0] === "join" && segments[1]) {
      return segments[1].replace(/^\+/, "");
    }
  } catch {
  }

  return normalized;
}

export function normalizeCallLinkToken(value: string) {
  const normalized = value.trim();
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("alex://call/")) {
    return normalized.slice("alex://call/".length).trim();
  }

  try {
    const url = new URL(normalizeUrlLikeInput(normalized));
    const host = url.hostname.toLowerCase();
    const segments = decodePathSegments(url.pathname);

    if (url.protocol === "alex:" && host === "call") {
      return resolveCallTokenFromUrl(url, segments);
    }
    if ((url.protocol === "tg:" || url.protocol === "telegram:") && host === "call") {
      return resolveCallTokenFromUrl(url, segments);
    }
    if (TELEGRAM_HOSTS.has(host) && segments[0] === "call") {
      return resolveCallTokenFromUrl(url, segments.slice(1));
    }
    if (segments[0] === "call") {
      return resolveCallTokenFromUrl(url, segments.slice(1));
    }
  } catch {
  }

  return normalized;
}
