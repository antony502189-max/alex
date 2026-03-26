import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { ROOT_TABS, type RootTab } from "../navigation/types";

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
    backgroundColor: "#eef4ff",
    borderTopWidth: 1,
    borderTopColor: "#d7e3fb"
  },
  tabButton: {
    flex: 1,
    borderRadius: 16,
    paddingVertical: 12,
    alignItems: "center",
    backgroundColor: "#dbe7ff"
  },
  tabButtonActive: {
    backgroundColor: "#2563eb"
  },
  tabLabel: {
    color: "#1d4ed8",
    fontWeight: "700",
    fontSize: 12
  },
  tabLabelActive: {
    color: "#ffffff"
  }
});
