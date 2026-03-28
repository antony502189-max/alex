import React from "react";
import { StyleSheet, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { appSpacing } from "../../theme/tokens";

type MediaViewerNavigationProps = {
  hasNext: boolean;
  hasPrevious: boolean;
  onNext: () => void;
  onPrevious: () => void;
  visible: boolean;
};

export function MediaViewerNavigation({
  hasNext,
  hasPrevious,
  onNext,
  onPrevious,
  visible
}: MediaViewerNavigationProps) {
  if (!visible) {
    return null;
  }

  return (
    <View style={styles.row}>
      <AppButton
        disabled={!hasPrevious}
        fullWidth
        onPress={onPrevious}
        style={styles.button}
        textStyle={styles.text}
      >
        Previous
      </AppButton>
      <AppButton
        disabled={!hasNext}
        fullWidth
        onPress={onNext}
        style={styles.button}
        textStyle={styles.text}
      >
        Next
      </AppButton>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    gap: appSpacing.md,
    justifyContent: "space-between",
    marginTop: appSpacing.lg
  },
  button: {
    backgroundColor: "#1e293b",
    flex: 1
  },
  text: {
    color: "#f8fafc"
  }
});
