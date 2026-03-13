import * as DocumentPicker from "expo-document-picker";

export type PickedImageFile = {
  uri: string;
  name: string;
  type: string;
};

export type PickedMediaFile = {
  uri: string;
  name: string;
  type: string;
};

export async function pickSingleImage(): Promise<PickedImageFile | null> {
  const selection = await DocumentPicker.getDocumentAsync({
    type: "image/*",
    copyToCacheDirectory: true,
    multiple: false
  });

  if (selection.canceled || selection.assets.length === 0) {
    return null;
  }

  const asset = selection.assets[0];
  return {
    uri: asset.uri,
    name: asset.name ?? "photo.jpg",
    type: asset.mimeType ?? "image/jpeg"
  };
}

export async function pickSingleStoryMedia(): Promise<PickedMediaFile | null> {
  const selection = await DocumentPicker.getDocumentAsync({
    type: ["image/*", "video/*"],
    copyToCacheDirectory: true,
    multiple: false
  });

  if (selection.canceled || selection.assets.length === 0) {
    return null;
  }

  const asset = selection.assets[0];
  const fallbackType = asset.name?.toLowerCase().match(/\.(mp4|mov|m4v|webm)$/)
    ? "video/mp4"
    : "image/jpeg";

  return {
    uri: asset.uri,
    name: asset.name ?? "story-media",
    type: asset.mimeType ?? fallbackType
  };
}
