import React from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { PublicChatDiscovery } from "../../types";
import {
  buildDiscoveryMetaLines,
  getDiscoveryActionLabel
} from "./joinChatPresentation";

type JoinChatDiscoveriesListProps = {
  discoveries: PublicChatDiscovery[];
  joining: boolean;
  onJoinDiscovery: (chat: PublicChatDiscovery) => void;
};

export function JoinChatDiscoveriesList({
  discoveries,
  joining,
  onJoinDiscovery
}: JoinChatDiscoveriesListProps) {
  if (discoveries.length === 0) {
    return null;
  }

  return (
    <SectionCard
      description="Matching public chats and channels you can open, join immediately, or request access to."
      title="Public chats"
    >
      <FlatList
        data={discoveries}
        keyExtractor={(item) => item.chatId}
        renderItem={({ item }) => {
          const metaLines = buildDiscoveryMetaLines(item);
          const disabled = joining || (!item.joined && !item.publicUsername);

          return (
            <View style={styles.discoveryCard}>
              <Avatar uri={item.photoUrl} title={item.title} size={44} />
              <View style={styles.discoveryBody}>
                <Text style={styles.discoveryName}>{item.title}</Text>
                {metaLines.map((line) => (
                  <Text key={`${item.chatId}:${line}`} style={styles.discoveryMeta}>
                    {line}
                  </Text>
                ))}
              </View>
              <AppButton
                disabled={disabled}
                onPress={() => onJoinDiscovery(item)}
                size="sm"
                variant="primary"
              >
                {joining ? "Joining..." : getDiscoveryActionLabel(item)}
              </AppButton>
            </View>
          );
        }}
        scrollEnabled={false}
      />
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  discoveryCard: {
    alignItems: "center",
    backgroundColor: appColors.background,
    borderRadius: appRadii.md,
    flexDirection: "row",
    gap: appSpacing.md,
    marginBottom: appSpacing.sm + 2,
    padding: appSpacing.md
  },
  discoveryBody: {
    flex: 1
  },
  discoveryName: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  discoveryMeta: {
    color: appColors.textSecondary,
    fontSize: 12,
    marginTop: 2
  }
});
