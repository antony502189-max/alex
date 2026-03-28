import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import type { RootStackParamList } from "./types";

const Stack = createNativeStackNavigator<RootStackParamList>();

type ScreenRenderer = () => React.ReactElement | null;

type RootStackScreensProps = {
  renderAuthScreen: ScreenRenderer;
  renderMainTabsScreen: ScreenRenderer;
  renderChatScreen: ScreenRenderer;
  renderForumTopicsScreen: ScreenRenderer;
  renderMembersScreen: ScreenRenderer;
  renderCallScreen: ScreenRenderer;
  renderCreateChatScreen: ScreenRenderer;
  renderAddAccountScreen: ScreenRenderer;
  renderBotDeveloperScreen: ScreenRenderer;
  renderSessionsScreen: ScreenRenderer;
  renderSettingsSectionScreen: ScreenRenderer;
  renderGlobalSearchScreen: ScreenRenderer;
  renderCreateStoryScreen: ScreenRenderer;
  renderJoinByLinkScreen: ScreenRenderer;
  renderMediaViewerScreen: ScreenRenderer;
  renderSharedMediaScreen: ScreenRenderer;
  renderChatInfoScreen: ScreenRenderer;
  renderArchivedScreen: ScreenRenderer;
  renderFoldersScreen: ScreenRenderer;
  renderBotMiniAppScreen: ScreenRenderer;
};

export function RootStackScreens({
  renderAuthScreen,
  renderMainTabsScreen,
  renderChatScreen,
  renderForumTopicsScreen,
  renderMembersScreen,
  renderCallScreen,
  renderCreateChatScreen,
  renderAddAccountScreen,
  renderBotDeveloperScreen,
  renderSessionsScreen,
  renderSettingsSectionScreen,
  renderGlobalSearchScreen,
  renderCreateStoryScreen,
  renderJoinByLinkScreen,
  renderMediaViewerScreen,
  renderSharedMediaScreen,
  renderChatInfoScreen,
  renderArchivedScreen,
  renderFoldersScreen,
  renderBotMiniAppScreen
}: RootStackScreensProps) {
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false
      }}
    >
      <Stack.Group>
        <Stack.Screen name="AUTH">{() => renderAuthScreen()}</Stack.Screen>
        <Stack.Screen name="MAIN_TABS">{() => renderMainTabsScreen()}</Stack.Screen>
        <Stack.Screen name="CHAT">{() => renderChatScreen()}</Stack.Screen>
        <Stack.Screen name="FORUM_TOPICS">{() => renderForumTopicsScreen()}</Stack.Screen>
        <Stack.Screen name="MEMBERS">{() => renderMembersScreen()}</Stack.Screen>
        <Stack.Screen name="CALL">{() => renderCallScreen()}</Stack.Screen>
      </Stack.Group>
      <Stack.Group
        screenOptions={{
          presentation: "modal"
        }}
      >
        <Stack.Screen name="CREATE_CHAT">{() => renderCreateChatScreen()}</Stack.Screen>
        <Stack.Screen name="AUTH_ADD_ACCOUNT">{() => renderAddAccountScreen()}</Stack.Screen>
        <Stack.Screen name="BOT_DEVELOPER">{() => renderBotDeveloperScreen()}</Stack.Screen>
        <Stack.Screen name="SESSIONS">{() => renderSessionsScreen()}</Stack.Screen>
        <Stack.Screen name="SETTINGS_SECTION">{() => renderSettingsSectionScreen()}</Stack.Screen>
        <Stack.Screen name="GLOBAL_SEARCH">{() => renderGlobalSearchScreen()}</Stack.Screen>
        <Stack.Screen name="CREATE_STORY">{() => renderCreateStoryScreen()}</Stack.Screen>
        <Stack.Screen name="JOIN_BY_LINK">{() => renderJoinByLinkScreen()}</Stack.Screen>
        <Stack.Screen name="MEDIA_VIEWER">{() => renderMediaViewerScreen()}</Stack.Screen>
        <Stack.Screen name="SHARED_MEDIA">{() => renderSharedMediaScreen()}</Stack.Screen>
        <Stack.Screen name="CHAT_INFO">{() => renderChatInfoScreen()}</Stack.Screen>
        <Stack.Screen name="ARCHIVED">{() => renderArchivedScreen()}</Stack.Screen>
        <Stack.Screen name="FOLDERS">{() => renderFoldersScreen()}</Stack.Screen>
        <Stack.Screen name="BOT_MINI_APP">{() => renderBotMiniAppScreen()}</Stack.Screen>
      </Stack.Group>
    </Stack.Navigator>
  );
}
