import React from "react";
import { StyleSheet, View } from "react-native";
import { MainTabsNavigator } from "./MainTabsNavigator";
import type { StoryFocusTarget } from "./rootScreenRendererTypes";
import type { RootTab } from "./types";
import { CallsScreen } from "../screens/CallsScreen";
import { ChatsListScreen } from "../screens/ChatsListScreen";
import { ContactsScreen } from "../screens/ContactsScreen";
import { ProfileScreen } from "../screens/ProfileScreen";
import { StoriesScreen } from "../screens/StoriesScreen";
import type { ParsedDeepLink } from "./deepLinks";
import type { SettingsSectionId } from "./types";
import type { AuthSession, ChatSummary } from "../types";

type RootMainTabsScreenProps = {
  session: AuthSession;
  activeRootTab: RootTab;
  availableChats: ChatSummary[];
  callsEnabled: boolean;
  callJoinLinksEnabled: boolean;
  storiesEnabled: boolean;
  botsEnabled: boolean;
  onSelectRootTab: (tab: RootTab) => void;
  onStartChatCall: (chatId: string, kind: "VOICE" | "VIDEO") => void;
  onJoinCallLink: (rawToken: string) => void;
  onOpenCallParsedLink: (parsedLink: ParsedDeepLink) => void;
  onOpenCallChat: (chatId: string) => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenContactChat: (chat: ChatSummary) => void;
  onOpenCreateChat: (mode: "direct" | "group" | "channel") => void;
  onOpenJoinByLink: () => void;
  onOpenGlobalSearch: () => void;
  onOpenArchived: () => void;
  onOpenCreateStory: () => void;
  onOpenFolders: () => void;
  onOpenBotDeveloper: () => void;
  onOpenAddAccount: () => void;
  onOpenSessions: () => void;
  onOpenSettingsSection: (section: SettingsSectionId) => void;
  onConsumeCreatedStoryFocus: () => void;
  onOpenContactBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
  onOpenSavedMessages: () => void;
  pendingCreatedStoryFocus: StoryFocusTarget | null;
};

export function RootMainTabsScreen({
  session,
  activeRootTab,
  availableChats,
  callsEnabled,
  callJoinLinksEnabled,
  storiesEnabled,
  botsEnabled,
  onSelectRootTab,
  onStartChatCall,
  onJoinCallLink,
  onOpenCallParsedLink,
  onOpenCallChat,
  onOpenChat,
  onOpenContactChat,
  onOpenCreateChat,
  onOpenJoinByLink,
  onOpenGlobalSearch,
  onOpenArchived,
  onOpenCreateStory,
  onOpenFolders,
  onOpenBotDeveloper,
  onOpenAddAccount,
  onOpenSessions,
  onOpenSettingsSection,
  onConsumeCreatedStoryFocus,
  onOpenContactBotMiniApp,
  onOpenSavedMessages,
  pendingCreatedStoryFocus
}: RootMainTabsScreenProps) {
  return (
    <View style={styles.screen}>
      <View style={styles.rootContent}>
        <MainTabsNavigator
          activeTab={activeRootTab}
          hiddenTabs={{
            CALLS: !callsEnabled,
            STORIES: !storiesEnabled
          }}
          onSelectTab={onSelectRootTab}
          renderCallsTab={() =>
            callsEnabled ? (
              <CallsScreen
                availableChats={availableChats}
                callJoinLinksEnabled={callJoinLinksEnabled}
                currentUserId={session.userId}
                onCallBack={onStartChatCall}
                onClose={() => onSelectRootTab("CHATS")}
                onJoinCallLink={onJoinCallLink}
                onOpenChat={onOpenCallChat}
                onOpenParsedLink={onOpenCallParsedLink}
                token={session.token}
              />
            ) : null
          }
          renderChatsTab={() => (
            <ChatsListScreen
              featureFlags={{
                bots: botsEnabled,
                calls: callsEnabled,
                stories: storiesEnabled
              }}
              onCreateChannel={() => onOpenCreateChat("channel")}
              onOpenJoinByLink={onOpenJoinByLink}
              onOpenGlobalSearch={onOpenGlobalSearch}
              onCreateDirect={() => onOpenCreateChat("direct")}
              onCreateGroup={() => onOpenCreateChat("group")}
              onOpenCalls={() => {
                if (callsEnabled) {
                  onSelectRootTab("CALLS");
                }
              }}
              onOpenArchived={onOpenArchived}
              onOpenChat={onOpenChat}
              onOpenStories={() => {
                if (storiesEnabled) {
                  onSelectRootTab("STORIES");
                }
              }}
              onCreateStory={onOpenCreateStory}
              onOpenContacts={() => onSelectRootTab("CONTACTS")}
              onOpenFolders={onOpenFolders}
              onOpenProfile={() => onSelectRootTab("SETTINGS")}
              onOpenSavedMessages={onOpenSavedMessages}
            />
          )}
          renderContactsTab={() => (
            <ContactsScreen
              onClose={() => onSelectRootTab("CHATS")}
              onOpenChat={onOpenContactChat}
              onOpenBotMiniApp={onOpenContactBotMiniApp}
              token={session.token}
            />
          )}
          renderSettingsTab={() => (
            <ProfileScreen
              onClose={() => onSelectRootTab("CHATS")}
              onOpenBotDeveloper={botsEnabled ? onOpenBotDeveloper : undefined}
              onAddAccount={onOpenAddAccount}
              onOpenSessions={onOpenSessions}
              onOpenSettingsSection={onOpenSettingsSection}
              token={session.token}
            />
          )}
          renderStoriesTab={() =>
            storiesEnabled ? (
              <StoriesScreen
                focusStory={pendingCreatedStoryFocus}
                onClose={() => onSelectRootTab("CHATS")}
                onConsumeFocusStory={onConsumeCreatedStoryFocus}
                onCreateStory={onOpenCreateStory}
                token={session.token}
              />
            ) : null
          }
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1
  },
  rootContent: {
    flex: 1
  }
});
