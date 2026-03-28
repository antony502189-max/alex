import React, { useMemo, useState } from "react";
import { StyleSheet, Text, type StyleProp, type TextStyle } from "react-native";
import type { MessageTextEntity } from "../types";
import { detectTextLinks } from "../services/linkUtils";

type FormattedMessageTextProps = {
  text: string;
  entities?: MessageTextEntity[] | null;
  onOpenLink?: (url: string) => void;
  style?: StyleProp<TextStyle>;
  variant?: "default" | "inverse" | "muted";
  numberOfLines?: number;
};

type TextSegment = {
  key: string;
  text: string;
  entityKeys: string[];
  entityTypes: Set<MessageTextEntity["type"]>;
  linkUrl: string | null;
};

function buildSegments(text: string, entities: MessageTextEntity[]) {
  if (!text) {
    return [] as TextSegment[];
  }

  const detectedLinks = detectTextLinks(text);

  if (entities.length === 0 && detectedLinks.length === 0) {
    return [
      {
        key: "plain",
        text,
        entityKeys: [],
        entityTypes: new Set<MessageTextEntity["type"]>(),
        linkUrl: null
      }
    ] satisfies TextSegment[];
  }

  const boundaries = new Set<number>([0, text.length]);
  for (const entity of entities) {
    boundaries.add(entity.offset);
    boundaries.add(entity.offset + entity.length);
  }
  for (const link of detectedLinks) {
    boundaries.add(link.start);
    boundaries.add(link.end);
    boundaries.add(link.rawEnd);
  }

  const orderedBoundaries = [...boundaries]
    .filter((value) => value >= 0 && value <= text.length)
    .sort((left, right) => left - right);

  const segments: TextSegment[] = [];
  for (let index = 0; index < orderedBoundaries.length - 1; index += 1) {
    const start = orderedBoundaries[index];
    const end = orderedBoundaries[index + 1];
    if (end <= start) {
      continue;
    }

    const segmentText = text.slice(start, end);
    const activeEntities = entities.filter(
      (entity) => entity.offset <= start && entity.offset + entity.length >= end
    );
    const activeLink =
      detectedLinks.find((link) => link.start <= start && link.end >= end) ?? null;
    segments.push({
      key: `${start}:${end}`,
      text: segmentText,
      entityKeys: activeEntities.map(
        (entity) => `${entity.type}:${entity.offset}:${entity.length}`
      ),
      entityTypes: new Set(activeEntities.map((entity) => entity.type)),
      linkUrl: activeLink?.url ?? null
    });
  }

  return segments;
}

export function FormattedMessageText({
  text,
  entities,
  onOpenLink,
  style,
  variant = "default",
  numberOfLines
}: FormattedMessageTextProps) {
  const [revealedSpoilers, setRevealedSpoilers] = useState<Set<string>>(new Set());
  const normalizedEntities = useMemo(() => entities ?? [], [entities]);
  const segments = useMemo(
    () => buildSegments(text, normalizedEntities),
    [text, normalizedEntities]
  );

  if (!text) {
    return null;
  }

  const variantStyle =
    variant === "inverse"
      ? styles.inverseText
      : variant === "muted"
        ? styles.mutedText
        : styles.defaultText;

  return (
    <Text numberOfLines={numberOfLines} style={[styles.baseText, variantStyle, style]}>
      {segments.map((segment) => {
        const isBold = segment.entityTypes.has("BOLD");
        const isItalic = segment.entityTypes.has("ITALIC");
        const isUnderlined = segment.entityTypes.has("UNDERLINE");
        const isStrikethrough = segment.entityTypes.has("STRIKETHROUGH");
        const isCode = segment.entityTypes.has("CODE");
        const isPre = segment.entityTypes.has("PRE");
        const spoilerKeys = segment.entityKeys.filter((key) => key.startsWith("SPOILER:"));
        const hiddenBySpoiler =
          spoilerKeys.length > 0 &&
          spoilerKeys.some((key) => !revealedSpoilers.has(key));
        const linkStyle =
          segment.linkUrl
            ? variant === "inverse"
              ? styles.linkInverseText
              : variant === "muted"
                ? styles.linkMutedText
                : styles.linkDefaultText
            : null;

        return (
          <Text
            key={segment.key}
            onPress={
              hiddenBySpoiler
                ? () =>
                    setRevealedSpoilers((current) => {
                      const next = new Set(current);
                      spoilerKeys.forEach((key) => next.add(key));
                      return next;
                    })
                : segment.linkUrl && onOpenLink
                  ? () => onOpenLink(segment.linkUrl!)
                : undefined
            }
            style={[
              styles.segmentText,
              isBold && styles.boldText,
              isItalic && styles.italicText,
              isUnderlined && styles.underlinedText,
              isStrikethrough && styles.strikethroughText,
              isCode && styles.codeText,
              isPre && styles.preText,
              linkStyle,
              hiddenBySpoiler &&
                (variant === "inverse" ? styles.hiddenSpoilerInverse : styles.hiddenSpoiler)
            ]}
          >
            {segment.text}
          </Text>
        );
      })}
    </Text>
  );
}

const styles = StyleSheet.create({
  baseText: {
    color: "#0f172a"
  },
  defaultText: {
    color: "#0f172a"
  },
  inverseText: {
    color: "#ffffff"
  },
  mutedText: {
    color: "#475569"
  },
  linkDefaultText: {
    color: "#2563eb",
    textDecorationLine: "underline"
  },
  linkInverseText: {
    color: "#bfdbfe",
    textDecorationLine: "underline"
  },
  linkMutedText: {
    color: "#1d4ed8",
    textDecorationLine: "underline"
  },
  segmentText: {
    includeFontPadding: false
  },
  boldText: {
    fontWeight: "700"
  },
  italicText: {
    fontStyle: "italic"
  },
  underlinedText: {
    textDecorationLine: "underline"
  },
  strikethroughText: {
    textDecorationLine: "line-through"
  },
  codeText: {
    fontFamily: "monospace",
    backgroundColor: "#e2e8f0"
  },
  preText: {
    fontFamily: "monospace",
    backgroundColor: "#dbeafe"
  },
  hiddenSpoiler: {
    color: "#cbd5e1",
    backgroundColor: "#334155"
  },
  hiddenSpoilerInverse: {
    color: "#0f172a",
    backgroundColor: "#e2e8f0"
  }
});
