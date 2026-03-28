import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type {
  BlockedUser,
  BotSummary,
  DeviceContactRecord,
  ImportContactsResult
} from "../../types";
import { ContactUserCard } from "./ContactUserCard";
import { buildBotMetaLine, buildUserMetaLines } from "./contactsPresentation";

type ContactsDefaultSectionsProps = {
  actionUserKey: string | null;
  blockedUsers: BlockedUser[];
  bots: BotSummary[];
  deviceContactsList: DeviceContactRecord[];
  importSummary: ImportContactsResult | null;
  onLoadDeviceContacts: () => void | Promise<void>;
  onOpenBotMiniApp: (botUserId: string, title: string) => void;
  onOpenDirect: (userId: string) => void | Promise<void>;
  onUnblockUser: (userId: string) => void | Promise<void>;
  syncingDeviceContacts: boolean;
};

export function ContactsDefaultSections({
  actionUserKey,
  blockedUsers,
  bots,
  deviceContactsList,
  importSummary,
  onLoadDeviceContacts,
  onOpenBotMiniApp,
  onOpenDirect,
  onUnblockUser,
  syncingDeviceContacts
}: ContactsDefaultSectionsProps) {
  return (
    <View style={styles.sectionList}>
      <SectionCard
        description="Import your phonebook, let the backend match registered users and persist those matches into your saved contacts."
        title="Device contacts"
      >
        <AppButton
          disabled={syncingDeviceContacts}
          onPress={() => void onLoadDeviceContacts()}
          size="sm"
          variant="secondary"
        >
          {syncingDeviceContacts ? "Importing..." : "Import phonebook"}
        </AppButton>
        {importSummary ? (
          <View style={styles.summaryCard}>
            <Text style={styles.summaryTitle}>
              Imported {importSummary.importedCount} numbers, matched {importSummary.matchedCount}.
            </Text>
            <Text style={styles.sectionMeta}>
              {importSummary.persistedMatches
                ? "Matched users were added to server contacts."
                : "Matched users were found, but server persistence is still pending."}
            </Text>
            {importSummary.unmatchedPhoneNumbers.length > 0 ? (
              <Text style={styles.sectionMeta}>
                Unmatched: {importSummary.unmatchedPhoneNumbers.slice(0, 6).join(" | ")}
                {importSummary.unmatchedPhoneNumbers.length > 6
                  ? ` +${importSummary.unmatchedPhoneNumbers.length - 6} more`
                  : ""}
              </Text>
            ) : null}
          </View>
        ) : null}
        {deviceContactsList.length > 0 ? (
          <View style={styles.sectionList}>
            {deviceContactsList.slice(0, 8).map((contact) => (
              <View key={contact.contactId} style={styles.deviceContactCard}>
                <View style={styles.cardInfo}>
                  <Text style={styles.cardTitle}>{contact.displayName}</Text>
                  <Text style={styles.cardMeta}>{contact.phoneNumbers.join(" | ")}</Text>
                </View>
              </View>
            ))}
            {deviceContactsList.length > 8 ? (
              <Text style={styles.sectionMeta}>
                Showing 8 of {deviceContactsList.length} device contacts.
              </Text>
            ) : null}
          </View>
        ) : null}
      </SectionCard>

      {bots.length > 0 ? (
        <SectionCard title="Bots">
          <View style={styles.sectionList}>
            {bots.map((bot) => (
              <ContactUserCard
                key={bot.userId}
                actions={
                  <>
                    {bot.webAppUrl ? (
                      <AppButton
                        onPress={() => onOpenBotMiniApp(bot.userId, bot.displayName)}
                        size="sm"
                        variant="secondary"
                      >
                        Mini App
                      </AppButton>
                    ) : null}
                    <AppButton onPress={() => void onOpenDirect(bot.userId)} size="sm" variant="primary">
                      Chat
                    </AppButton>
                  </>
                }
                description={bot.description}
                metaLines={buildBotMetaLine(bot.username, bot.supportsInline)}
                photoUrl={bot.photoUrl}
                title={bot.displayName}
              />
            ))}
          </View>
        </SectionCard>
      ) : null}

      {blockedUsers.length > 0 ? (
        <SectionCard title="Blocked">
          <View style={styles.sectionList}>
            {blockedUsers.map((user) => (
              <ContactUserCard
                key={user.userId}
                actions={
                  <>
                    <AppButton onPress={() => void onOpenDirect(user.userId)} size="sm" variant="primary">
                      Chat
                    </AppButton>
                    <AppButton
                      disabled={actionUserKey === `unblock:${user.userId}`}
                      onPress={() => void onUnblockUser(user.userId)}
                      size="sm"
                      variant="secondary"
                    >
                      {actionUserKey === `unblock:${user.userId}` ? "..." : "Unblock"}
                    </AppButton>
                  </>
                }
                description={user.botDescription}
                metaLines={buildUserMetaLines(
                  {
                    username: user.username,
                    phoneNumber: user.phoneNumber,
                    bot: user.bot,
                    botSupportsInline: user.botSupportsInline,
                    online: user.online,
                    lastSeenAt: user.lastSeenAt
                  },
                  true
                )}
                photoUrl={user.photoUrl}
                title={user.displayName}
              />
            ))}
          </View>
        </SectionCard>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  sectionMeta: {
    color: appColors.textSecondary
  },
  sectionList: {
    gap: appSpacing.md
  },
  deviceContactCard: {
    backgroundColor: appColors.surfaceMuted,
    borderRadius: appRadii.md,
    padding: appSpacing.md
  },
  cardInfo: {
    flex: 1
  },
  summaryCard: {
    backgroundColor: "#e0f2fe",
    borderRadius: appRadii.md,
    padding: appSpacing.md
  },
  summaryTitle: {
    color: appColors.textPrimary,
    fontSize: 15,
    fontWeight: "700",
    marginBottom: appSpacing.xs
  },
  cardTitle: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "600"
  },
  cardMeta: {
    color: appColors.textSecondary,
    marginTop: 3
  }
});
