import React from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { Contact } from "../../types";
import { SectionCard } from "../ui/SectionCard";
import {
  getCreateStoryAudienceTitle,
  getSelectedStoryContactsLabel,
  type StoryAudience
} from "./createStoryPresentation";

type CreateStoryContactsSectionProps = {
  audience: StoryAudience;
  contacts: Contact[];
  loadingContacts: boolean;
  onToggleViewer: (userId: string) => void;
  selectedContacts: Contact[];
  selectedViewerIds: string[];
};

export function CreateStoryContactsSection({
  audience,
  contacts,
  loadingContacts,
  onToggleViewer,
  selectedContacts,
  selectedViewerIds
}: CreateStoryContactsSectionProps) {
  return (
    <SectionCard title={getCreateStoryAudienceTitle(audience)}>
      {loadingContacts ? <ActivityIndicator color={appColors.textPrimary} /> : null}
      {!loadingContacts && contacts.length === 0 ? (
        <Text style={styles.hint}>Add contacts first to target a private story audience.</Text>
      ) : null}
      <View style={styles.grid}>
        {contacts.map((contact) => {
          const selected = selectedViewerIds.includes(contact.userId);
          return (
            <Pressable
              key={contact.userId}
              onPress={() => onToggleViewer(contact.userId)}
              style={({ pressed }) => [
                styles.contactCard,
                selected && styles.contactCardActive,
                pressed && styles.pressed
              ]}
            >
              <Text style={[styles.contactTitle, selected && styles.contactTextActive]}>
                {contact.displayName}
              </Text>
              <Text style={[styles.contactMeta, selected && styles.contactTextActive]}>
                {contact.username ? `@${contact.username}` : contact.phoneNumber ?? "contact"}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {getSelectedStoryContactsLabel(selectedContacts) ? (
        <Text style={styles.hint}>{getSelectedStoryContactsLabel(selectedContacts)}</Text>
      ) : null}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  grid: {
    gap: appSpacing.sm
  },
  contactCard: {
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    paddingHorizontal: appSpacing.lg,
    paddingVertical: appSpacing.md
  },
  contactCardActive: {
    backgroundColor: "#dbeafe",
    borderColor: appColors.brand,
    borderWidth: 1
  },
  contactTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  contactMeta: {
    color: appColors.textSecondary,
    fontSize: 12,
    marginTop: appSpacing.xs
  },
  contactTextActive: {
    color: appColors.brandText
  },
  hint: {
    color: appColors.textSecondary,
    lineHeight: 18
  },
  pressed: {
    opacity: 0.9
  }
});
