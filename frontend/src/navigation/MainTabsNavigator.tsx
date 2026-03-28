import React, { useEffect, type ReactNode } from "react";
import {
  NavigationIndependentTree,
  NavigationContainer,
  useNavigationContainerRef
} from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { RootTabBar } from "../components/RootTabBar";
import type { MainTabsParamList, RootTab } from "./types";

const Tab = createBottomTabNavigator<MainTabsParamList>();

type MainTabsNavigatorProps = {
  activeTab: RootTab;
  hiddenTabs?: Partial<Record<RootTab, boolean>>;
  onSelectTab: (tab: RootTab) => void;
  renderChatsTab: () => ReactNode;
  renderCallsTab: () => ReactNode;
  renderContactsTab: () => ReactNode;
  renderStoriesTab: () => ReactNode;
  renderSettingsTab: () => ReactNode;
};

export function MainTabsNavigator({
  activeTab,
  hiddenTabs,
  onSelectTab,
  renderChatsTab,
  renderCallsTab,
  renderContactsTab,
  renderStoriesTab,
  renderSettingsTab
}: MainTabsNavigatorProps) {
  const navigationRef = useNavigationContainerRef<MainTabsParamList>();

  useEffect(() => {
    if (!navigationRef.isReady()) {
      return;
    }

    const currentRoute = navigationRef.getCurrentRoute()?.name as RootTab | undefined;
    if (currentRoute !== activeTab) {
      navigationRef.navigate(activeTab);
    }
  }, [activeTab, navigationRef]);

  return (
    <NavigationIndependentTree>
      <NavigationContainer
        onReady={() => {
          const currentRoute = navigationRef.getCurrentRoute()?.name as RootTab | undefined;
          if (currentRoute && currentRoute !== activeTab) {
            onSelectTab(currentRoute);
          }
        }}
        onStateChange={() => {
          const currentRoute = navigationRef.getCurrentRoute()?.name as RootTab | undefined;
          if (currentRoute && currentRoute !== activeTab) {
            onSelectTab(currentRoute);
          }
        }}
        ref={navigationRef}
      >
        <Tab.Navigator
          initialRouteName={activeTab}
          screenOptions={{
            animation: "none",
            headerShown: false,
            lazy: false,
            tabBarHideOnKeyboard: true
          }}
          tabBar={({ navigation, state }) => (
            <RootTabBar
              activeTab={state.routeNames[state.index] as RootTab}
              hiddenTabs={hiddenTabs}
              onSelectTab={(tab) => {
                navigation.navigate(tab);
                onSelectTab(tab);
              }}
            />
          )}
        >
          <Tab.Screen name="CHATS">{() => <>{renderChatsTab()}</>}</Tab.Screen>
          <Tab.Screen name="CALLS">{() => <>{renderCallsTab()}</>}</Tab.Screen>
          <Tab.Screen name="CONTACTS">{() => <>{renderContactsTab()}</>}</Tab.Screen>
          <Tab.Screen name="STORIES">{() => <>{renderStoriesTab()}</>}</Tab.Screen>
          <Tab.Screen name="SETTINGS">{() => <>{renderSettingsTab()}</>}</Tab.Screen>
        </Tab.Navigator>
      </NavigationContainer>
    </NavigationIndependentTree>
  );
}
