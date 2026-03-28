import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type ContactUserCardProps = {
  actions?: React.ReactNode;
  description?: string | null;
  metaLines: string[];
  photoUrl: string | null;
  title: string;
};

export function ContactUserCard({
  actions,
  description,
  metaLines,
  photoUrl,
  title
}: ContactUserCardProps) {
  return (
    <View style={styles.card}>
      <Avatar uri={photoUrl} title={title} size={52} />
      <View style={styles.cardInfo}>
        <Text style={styles.cardTitle}>{title}</Text>
        {metaLines.map((line) => (
          <Text key={line} style={styles.cardMeta}>
            {line}
          </Text>
        ))}
        {description ? <Text style={styles.cardMeta}>{description}</Text> : null}
      </View>
      {actions ? <View style={styles.cardActions}>{actions}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.lg
  },
  cardInfo: {
    flex: 1
  },
  cardTitle: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "600"
  },
  cardMeta: {
    color: appColors.textSecondary,
    marginTop: 3
  },
  cardActions: {
    gap: appSpacing.sm
  }
});
