import React from "react";
import { StyleSheet, View } from "react-native";
import type { MessageAttachment } from "../../types";

type ChatWaveformProps = {
  attachment: Pick<MessageAttachment, "attachmentId" | "waveform">;
  color: string;
};

export function ChatWaveform({ attachment, color }: ChatWaveformProps) {
  if (!attachment.waveform || attachment.waveform.length === 0) {
    return null;
  }

  return (
    <View style={styles.row}>
      {attachment.waveform.map((sample, index) => (
        <View
          key={`${attachment.attachmentId}-wave-${index}`}
          style={[
            styles.bar,
            {
              backgroundColor: color,
              height: Math.max(6, Math.round((24 * sample) / 100))
            }
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "flex-end", gap: 2, marginTop: 8, minHeight: 24 },
  bar: { width: 4, borderRadius: 999 }
});
