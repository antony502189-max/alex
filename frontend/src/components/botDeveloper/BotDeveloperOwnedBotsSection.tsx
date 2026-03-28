import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { DeveloperBot } from "../../types";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { buildOwnedBotMeta } from "./botDeveloperPresentation";

type BotDeveloperOwnedBotsSectionProps = {
  bots: DeveloperBot[];
  onCreateNew: () => void;
  onSelectBot: (bot: DeveloperBot) => void;
  selectedBotId: string | null;
};

export function BotDeveloperOwnedBotsSection({
  bots,
  onCreateNew,
  onSelectBot,
  selectedBotId
}: BotDeveloperOwnedBotsSectionProps) {
  return (
    <SectionCard
      title="Owned bots"
      description="Manage tokens, webhook state and inline capabilities for bots you own."
    >
      <View style={styles.headerAction}>
        <AppButton onPress={onCreateNew} size="sm">
          New bot
        </AppButton>
      </View>

      {bots.length === 0 ? (
        <Text style={styles.meta}>No bots yet.</Text>
      ) : (
        <View style={styles.list}>
          {bots.map((bot) => {
            const selected = bot.botUserId === selectedBotId;
            return (
              <Pressable
                key={bot.botUserId}
                onPress={() => onSelectBot(bot)}
                style={({ pressed }) => [
                  styles.card,
                  selected && styles.cardActive,
                  pressed && styles.pressed
                ]}
              >
                <Avatar size={44} title={bot.displayName} uri={bot.photoUrl} />
                <View style={styles.info}>
                  <Text style={styles.title}>{bot.displayName}</Text>
                  <Text style={styles.meta}>@{bot.username}</Text>
                  <Text style={styles.meta}>{buildOwnedBotMeta(bot)}</Text>
                </View>
              </Pressable>
            );
          })}
        </View>
      )}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  headerAction: {
    alignItems: "flex-start"
  },
  list: {
    gap: appSpacing.sm
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.background,
    borderColor: appColors.border,
    borderRadius: appRadii.lg,
    borderWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.md
  },
  cardActive: {
    backgroundColor: appColors.surfaceAccent,
    borderColor: appColors.textPrimary
  },
  pressed: {
    opacity: 0.9
  },
  info: {
    flex: 1,
    gap: appSpacing.xs
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 16,
    fontWeight: "700"
  },
  meta: {
    color: appColors.textSecondary
  }
});
