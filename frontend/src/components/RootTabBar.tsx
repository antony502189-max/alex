import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { ROOT_TABS, type RootTab } from "../navigation/types";
import { appColors } from "../theme/tokens";

const TAB_LABELS: Record<RootTab, string> = {
  CHATS: "Chats",
  CALLS: "Calls",
  CONTACTS: "Contacts",
  STORIES: "Stories",
  SETTINGS: "Settings"
};

type RootTabBarProps = {
  activeTab: RootTab;
  hiddenTabs?: Partial<Record<RootTab, boolean>>;
  onSelectTab: (tab: RootTab) => void;
};

export function RootTabBar({
  activeTab,
  hiddenTabs,
  onSelectTab
}: RootTabBarProps) {
  return (
    <View style={styles.wrapper}>
      {ROOT_TABS.filter((tab) => !hiddenTabs?.[tab]).map((tab) => {
        const active = activeTab === tab;
        return (
          <Pressable
            key={tab}
            onPress={() => onSelectTab(tab)}
            style={[styles.tabButton, active && styles.tabButtonActive]}
          >
            <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>
              {TAB_LABELS[tab]}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 12,
    paddingTop: 10,
    paddingBottom: 16,
    backgroundColor: appColors.surfaceMuted,
    borderTopWidth: 1,
    borderTopColor: appColors.border
  },
  tabButton: {
    flex: 1,
    borderRadius: 16,
    paddingVertical: 12,
    alignItems: "center",
    backgroundColor: appColors.surfaceAccent
  },
  tabButtonActive: {
    backgroundColor: appColors.brand
  },
  tabLabel: {
    color: appColors.brandText,
    fontWeight: "700",
    fontSize: 12
  },
  tabLabelActive: {
    color: appColors.inverse
  }
});
