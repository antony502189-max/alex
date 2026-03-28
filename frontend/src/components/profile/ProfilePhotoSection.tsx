import React from "react";
import { StyleSheet, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";

type ProfilePhotoSectionProps = {
  onRemovePhoto: () => void;
  onUploadPhoto: () => void;
  photoUrl: string | null;
  removingPhoto: boolean;
  title: string;
  uploadingPhoto: boolean;
};

export function ProfilePhotoSection({
  onRemovePhoto,
  onUploadPhoto,
  photoUrl,
  removingPhoto,
  title,
  uploadingPhoto
}: ProfilePhotoSectionProps) {
  return (
    <SectionCard style={styles.photoCard}>
      <Avatar uri={photoUrl} title={title} size={92} />
      <View style={styles.photoActions}>
        <AppButton
          disabled={uploadingPhoto}
          fullWidth
          onPress={onUploadPhoto}
          variant="secondary"
        >
          {uploadingPhoto ? "Uploading..." : "Change photo"}
        </AppButton>
        <AppButton
          disabled={removingPhoto || !photoUrl}
          fullWidth
          onPress={onRemovePhoto}
          variant="danger"
        >
          {removingPhoto ? "Removing..." : "Remove photo"}
        </AppButton>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  photoCard: {
    alignItems: "center",
    gap: appSpacing.md + 2
  },
  photoActions: {
    gap: appSpacing.sm + 2,
    width: "100%"
  }
});
