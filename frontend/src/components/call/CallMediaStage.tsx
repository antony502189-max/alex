import React from "react";
import { StyleSheet, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

let RTCViewComponent: null | React.ComponentType<{
  mirror?: boolean;
  objectFit?: "contain" | "cover";
  streamURL?: string;
  style?: object;
  zOrder?: number;
}> = null;

try {
  RTCViewComponent = require("react-native-webrtc").RTCView as typeof RTCViewComponent;
} catch {
  RTCViewComponent = null;
}

type CallMediaStageProps = {
  localStreamUrl: string | null;
  localVisible: boolean;
  remoteStreamUrl: string | null;
};

export function CallMediaStage({
  localStreamUrl,
  localVisible,
  remoteStreamUrl
}: CallMediaStageProps) {
  if (!RTCViewComponent || !remoteStreamUrl) {
    return null;
  }

  return (
    <View style={styles.videoStage}>
      <RTCViewComponent objectFit="cover" streamURL={remoteStreamUrl} style={styles.remoteVideo} />
      {localStreamUrl && localVisible ? (
        <RTCViewComponent
          mirror
          objectFit="cover"
          streamURL={localStreamUrl}
          style={styles.localVideo}
          zOrder={2}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  videoStage: {
    backgroundColor: "#020617",
    borderRadius: appRadii.xl + 6,
    height: 320,
    overflow: "hidden",
    position: "relative"
  },
  remoteVideo: {
    height: "100%",
    width: "100%"
  },
  localVideo: {
    backgroundColor: appColors.textPrimary,
    borderRadius: appRadii.lg,
    bottom: appSpacing.lg,
    height: 168,
    position: "absolute",
    right: appSpacing.lg,
    width: 110
  }
});
