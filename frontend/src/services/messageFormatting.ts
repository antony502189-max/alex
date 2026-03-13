import type { MessageTextEntity } from "../types";

export type MessageComposerSelection = {
  start: number;
  end: number;
};

export const SUPPORTED_MESSAGE_ENTITY_TYPES = [
  "BOLD",
  "ITALIC",
  "UNDERLINE",
  "STRIKETHROUGH",
  "SPOILER",
  "CODE",
  "PRE"
] as const;

export function normalizeMessageEntities(
  text: string,
  entities: MessageTextEntity[] | null | undefined
): MessageTextEntity[] {
  if (!entities || entities.length === 0) {
    return [];
  }

  const seen = new Set<string>();
  return [...entities]
    .filter((entity): entity is MessageTextEntity => {
      if (!entity) {
        return false;
      }
      if (!SUPPORTED_MESSAGE_ENTITY_TYPES.includes(entity.type)) {
        return false;
      }
      if (entity.offset < 0 || entity.length <= 0) {
        return false;
      }
      if (entity.offset + entity.length > text.length) {
        return false;
      }
      const key = `${entity.type}:${entity.offset}:${entity.length}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    })
    .sort((left, right) => {
      if (left.offset !== right.offset) {
        return left.offset - right.offset;
      }
      if (left.length !== right.length) {
        return left.length - right.length;
      }
      return left.type.localeCompare(right.type);
    });
}

export function toggleMessageEntity(
  text: string,
  entities: MessageTextEntity[],
  type: MessageTextEntity["type"],
  selection: MessageComposerSelection
) {
  const start = Math.max(0, Math.min(selection.start, selection.end));
  const end = Math.max(0, Math.max(selection.start, selection.end));
  if (start === end || start >= text.length) {
    return normalizeMessageEntities(text, entities);
  }

  const length = Math.min(end, text.length) - start;
  const existing = entities.find(
    (entity) => entity.type === type && entity.offset === start && entity.length === length
  );

  if (existing) {
    return normalizeMessageEntities(
      text,
      entities.filter(
        (entity) =>
          !(entity.type === type && entity.offset === start && entity.length === length)
      )
    );
  }

  return normalizeMessageEntities(text, [
    ...entities,
    {
      type,
      offset: start,
      length
    }
  ]);
}

export function trimFormattedMessage(
  text: string,
  entities: MessageTextEntity[]
): { text: string; entities: MessageTextEntity[] } {
  if (!text) {
    return {
      text: "",
      entities: []
    };
  }

  const leadingWhitespace = text.length - text.trimStart().length;
  const trailingWhitespace = text.length - text.trimEnd().length;
  const start = leadingWhitespace;
  const end = text.length - trailingWhitespace;
  const trimmedText = text.slice(start, end);

  if (!trimmedText) {
    return {
      text: "",
      entities: []
    };
  }

  const trimmedEntities = entities
    .map((entity) => {
      const entityStart = Math.max(entity.offset, start);
      const entityEnd = Math.min(entity.offset + entity.length, end);
      if (entityEnd <= entityStart) {
        return null;
      }
      return {
        type: entity.type,
        offset: entityStart - start,
        length: entityEnd - entityStart
      } satisfies MessageTextEntity;
    })
    .filter((entity): entity is MessageTextEntity => !!entity);

  return {
    text: trimmedText,
    entities: normalizeMessageEntities(trimmedText, trimmedEntities)
  };
}
