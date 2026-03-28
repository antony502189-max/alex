import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { CallParticipant, CallSession } from "../../types";
import { buildCallMetaLabel } from "./callPresentation";

type CallHeroCardProps = {
  call: CallSession;
  chatPhotoUrl?: string | null;
  chatTitle: string;
  headlineParticipant: CallParticipant | null;
  statusText: string;
};

export function CallHeroCard({
  call,
  chatPhotoUrl,
  chatTitle,
  headlineParticipant,
  statusText
}: CallHeroCardProps) {
  const title = chatTitle || headlineParticipant?.displayName || "Call";

  return (
    <SectionCard style={styles.card}>
      <View style={styles.body}>
        <Avatar
          size={112}
          title={title}
          uri={chatPhotoUrl ?? headlineParticipant?.photoUrl}
        />
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{statusText}</Text>
        <Text style={styles.meta}>{buildCallMetaLabel(call)}</Text>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: "center"
  },
  body: {
    alignItems: "center",
    gap: appSpacing.sm
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 28,
    fontWeight: "700",
    textAlign: "center"
  },
  subtitle: {
    color: "#0f766e",
    fontSize: 16,
    fontWeight: "600",
    textAlign: "center"
  },
  meta: {
    color: appColors.textSecondary
  }
});
