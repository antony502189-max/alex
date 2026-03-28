import React, { useEffect } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { RootNavigator } from "./src/navigation/RootNavigator";
import { localDatabase } from "./src/services/localDatabase";
import { useAppStore } from "./src/store/useAppStore";

export default function App() {
  const hydrate = useAppStore((state) => state.hydrate);
  const hydrated = useAppStore((state) => state.hydrated);
  const hydrating = useAppStore((state) => state.hydrating);

  useEffect(() => {
    void Promise.all([
      localDatabase.init().catch(() => undefined),
      hydrate().catch(() => undefined)
    ]);
  }, [hydrate]);

  if (!hydrated || hydrating) {
    return (
      <GestureHandlerRootView style={styles.root}>
        <SafeAreaProvider>
          <View style={styles.screen}>
            <View style={styles.card}>
              <ActivityIndicator size="large" color="#2563eb" />
              <Text style={styles.title}>Loading Alex</Text>
              <Text style={styles.subtitle}>Restoring accounts, cached chats, and local drafts.</Text>
            </View>
          </View>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    );
  }

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <RootNavigator />
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1
  },
  screen: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#e8f0ff",
    padding: 24
  },
  card: {
    width: "100%",
    maxWidth: 360,
    borderRadius: 24,
    padding: 24,
    gap: 10,
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#d7e3fb"
  },
  title: {
    fontSize: 20,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    textAlign: "center",
    color: "#475569"
  }
});
