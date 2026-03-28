const URL_PATTERN =
  /(?:https?|alex|tg|telegram):\/\/[^\s]+|(?:www\.)?(?:t\.me|telegram\.me)\/[^\s]+/gi;
const MENTION_PATTERN = /@[A-Za-z0-9_]{3,}/g;

export type DetectedTextLink = {
  start: number;
  end: number;
  rawEnd: number;
  url: string;
};

export function trimTrailingLinkPunctuation(value: string) {
  return value.replace(/[),.!?:;]+$/g, "");
}

export function normalizeExternalLinkUrl(url: string) {
  const normalized = url.trim();
  if (!normalized) {
    return "";
  }

  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(normalized)) {
    return normalized;
  }

  if (/^(?:www\.)?(?:t\.me|telegram\.me)\//i.test(normalized)) {
    return `https://${normalized.replace(/^https?:\/\//i, "")}`;
  }

  return normalized;
}

function overlapsExistingRange(
  links: DetectedTextLink[],
  start: number,
  end: number
) {
  return links.some((link) => start < link.rawEnd && end > link.start);
}

export function detectTextLinks(text: string): DetectedTextLink[] {
  if (!text) {
    return [];
  }

  const detected: DetectedTextLink[] = [];

  for (const match of text.matchAll(URL_PATTERN)) {
    const rawMatch = match[0];
    const start = match.index ?? -1;
    if (start < 0) {
      continue;
    }

    const cleaned = trimTrailingLinkPunctuation(rawMatch);
    if (!cleaned) {
      continue;
    }

    detected.push({
      start,
      end: start + cleaned.length,
      rawEnd: start + rawMatch.length,
      url: cleaned
    });
  }

  for (const match of text.matchAll(MENTION_PATTERN)) {
    const mention = match[0];
    const start = match.index ?? -1;
    if (start < 0) {
      continue;
    }

    const previousCharacter = start > 0 ? text[start - 1] : "";
    if (/[A-Za-z0-9._]/.test(previousCharacter)) {
      continue;
    }

    const end = start + mention.length;
    if (overlapsExistingRange(detected, start, end)) {
      continue;
    }

    detected.push({
      start,
      end,
      rawEnd: end,
      url: mention
    });
  }

  return detected.sort((left, right) => left.start - right.start);
}
