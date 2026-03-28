import React from "react";
import { ActivityIndicator, StyleSheet, type StyleProp, type ViewStyle } from "react-native";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppBanner } from "./AppBanner";

type ScreenFeedbackProps = {
  error?: string | null;
  errorStyle?: StyleProp<ViewStyle>;
  loading?: boolean;
  loadingFirst?: boolean;
  loadingStyle?: StyleProp<ViewStyle>;
  notice?: string | null;
  noticeStyle?: StyleProp<ViewStyle>;
  noticeTone?: "danger" | "info" | "success";
};

export function ScreenFeedback({
  error,
  errorStyle,
  loading = false,
  loadingFirst = true,
  loadingStyle,
  notice,
  noticeStyle,
  noticeTone = "success"
}: ScreenFeedbackProps) {
  if (!loading && !error && !notice) {
    return null;
  }

  const loader = loading ? (
    <ActivityIndicator color={appColors.textPrimary} style={[styles.loader, loadingStyle]} />
  ) : null;

  return (
    <>
      {loadingFirst ? loader : null}
      {error ? <AppBanner message={error} style={errorStyle} tone="danger" /> : null}
      {notice ? <AppBanner message={notice} style={noticeStyle} tone={noticeTone} /> : null}
      {loadingFirst ? null : loader}
    </>
  );
}

const styles = StyleSheet.create({
  loader: {
    marginBottom: appSpacing.md
  }
});
