import React from "react";
import { StyleSheet, TextInput, type TextInputProps, type StyleProp, type TextStyle } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppTextFieldProps = TextInputProps & {
  style?: StyleProp<TextStyle>;
};

export function AppTextField({ multiline, style, ...props }: AppTextFieldProps) {
  return (
    <TextInput
      multiline={multiline}
      placeholderTextColor={appColors.textSecondary}
      style={[styles.input, multiline && styles.multiline, style]}
      {...props}
    />
  );
}

const styles = StyleSheet.create({
  input: {
    backgroundColor: appColors.surface,
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    color: appColors.textPrimary,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.md
  },
  multiline: {
    minHeight: 100,
    textAlignVertical: "top"
  }
});
