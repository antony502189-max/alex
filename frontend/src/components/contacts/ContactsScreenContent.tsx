import React from "react";
import { StyleSheet } from "react-native";
import { ContactsSavedListSection } from "./ContactsSavedListSection";
import { ContactsSearchResultsSection } from "./ContactsSearchResultsSection";
import type { ContactsScreenController } from "./useContactsController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { AppTextField } from "../ui/AppTextField";
import { appSpacing } from "../../theme/tokens";

type ContactsScreenContentProps = {
  controller: ContactsScreenController;
  onClose: () => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
};

export function ContactsScreenContent({
  controller,
  onClose,
  onOpenBotMiniApp
}: ContactsScreenContentProps) {
  function handleOpenBotMiniApp(botUserId: string, title: string) {
    onOpenBotMiniApp?.(botUserId, title, null, null);
  }

  return (
    <>
      <AppHeader
        onBack={onClose}
        subtitle="People, bots, blocked users and local phonebook access"
        title="Contacts"
      />

      <AppTextField
        autoCapitalize="none"
        onChangeText={controller.setQuery}
        placeholder="Search users"
        style={styles.input}
        value={controller.query}
      />
      <ScreenFeedback
        error={controller.error}
        loading={controller.loading || controller.searching}
        notice={controller.notice}
      />

      {controller.query.trim().length >= 2 ? (
        <ContactsSearchResultsSection
          actionUserKey={controller.actionUserKey}
          isBlocked={controller.isBlocked}
          onAddContact={controller.handleAddContact}
          onBlockUser={controller.handleBlockUser}
          onOpenBotMiniApp={handleOpenBotMiniApp}
          onOpenDirect={controller.handleOpenDirect}
          onReportUser={controller.handleReportUser}
          onUnblockUser={controller.handleUnblockUser}
          results={controller.results}
        />
      ) : (
        <ContactsSavedListSection
          actionUserKey={controller.actionUserKey}
          blockedUsers={controller.blockedUsers}
          bots={controller.bots}
          contacts={controller.contacts}
          deviceContactsList={controller.deviceContactsList}
          importSummary={controller.importSummary}
          isBlocked={controller.isBlocked}
          onBlockUser={controller.handleBlockUser}
          onLoadDeviceContacts={controller.handleLoadDeviceContacts}
          onOpenBotMiniApp={handleOpenBotMiniApp}
          onOpenDirect={controller.handleOpenDirect}
          onRemoveContact={controller.handleRemoveContact}
          onUnblockUser={controller.handleUnblockUser}
          syncingDeviceContacts={controller.syncingDeviceContacts}
        />
      )}
    </>
  );
}

const styles = StyleSheet.create({
  input: {
    marginBottom: appSpacing.md
  }
});
