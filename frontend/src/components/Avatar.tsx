import React from "react";
import { Image, StyleSheet, Text, View } from "react-native";

type AvatarProps = {
  uri?: string | null;
  title: string;
  size?: number;
};

const PALETTE = [
  ["#0f766e", "#99f6e4"],
  ["#1d4ed8", "#bfdbfe"],
  ["#9a3412", "#fed7aa"],
  ["#7c2d12", "#fdba74"],
  ["#166534", "#bbf7d0"],
  ["#6d28d9", "#ddd6fe"]
];

function getInitials(title: string) {
  const parts = title
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2);

  if (parts.length === 0) {
    return "?";
  }

  return parts.map((part) => part[0]?.toUpperCase() ?? "").join("");
}

function hashTitle(title: string) {
  let hash = 0;
  for (const char of title) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }
  return hash;
}

export function Avatar({ uri, title, size = 48 }: AvatarProps) {
  const [backgroundColor, accentColor] = PALETTE[hashTitle(title) % PALETTE.length];
  const initials = getInitials(title);

  return (
    <View
      style={[
        styles.base,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: accentColor,
          borderColor: backgroundColor
        }
      ]}
    >
      {uri ? (
        <Image
          source={{ uri }}
          style={{ width: size, height: size, borderRadius: size / 2 }}
        />
      ) : (
        <Text style={[styles.initials, { color: backgroundColor, fontSize: Math.max(14, size * 0.36) }]}>
          {initials}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    overflow: "hidden"
  },
  initials: {
    fontWeight: "700"
  }
});
