import * as ImagePicker from "expo-image-picker";

export type PickedImageFile = {
  uri: string;
  name: string;
  type: string;
  width?: number | null;
  height?: number | null;
};

export type PickedMediaFile = {
  uri: string;
  name: string;
  type: string;
  width?: number | null;
  height?: number | null;
  durationMs?: number | null;
  source?: "CAMERA" | "LIBRARY";
};

type LibraryPickOptions = {
  allowsMultipleSelection?: boolean;
  maxSelection?: number;
};

function inferMimeType(fileName: string | undefined, defaultType: string) {
  const normalized = fileName?.toLowerCase() ?? "";
  if (normalized.endsWith(".png")) {
    return "image/png";
  }
  if (normalized.endsWith(".gif")) {
    return "image/gif";
  }
  if (normalized.endsWith(".mov")) {
    return "video/quicktime";
  }
  if (normalized.endsWith(".webm")) {
    return "video/webm";
  }
  return defaultType;
}

function normalizeAsset(
  asset: ImagePicker.ImagePickerAsset,
  fallbackName: string,
  source: "CAMERA" | "LIBRARY"
): PickedMediaFile {
  const inferredType = asset.type === "video" ? "video/mp4" : "image/jpeg";
  return {
    uri: asset.uri,
    name: asset.fileName ?? fallbackName,
    type: asset.mimeType ?? inferMimeType(asset.fileName ?? undefined, inferredType),
    width: typeof asset.width === "number" ? asset.width : null,
    height: typeof asset.height === "number" ? asset.height : null,
    durationMs:
      typeof asset.duration === "number" && Number.isFinite(asset.duration)
        ? asset.duration
        : null,
    source
  };
}

async function ensureLibraryPermission() {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) {
    throw new Error("Media library permission was not granted on this device");
  }
}

async function pickFromLibrary(
  mediaTypes: ImagePicker.MediaTypeOptions,
  options: LibraryPickOptions = {}
) {
  await ensureLibraryPermission();
  const selection = await ImagePicker.launchImageLibraryAsync({
    mediaTypes,
    quality: 1,
    allowsEditing: false,
    allowsMultipleSelection: options.allowsMultipleSelection ?? false,
    selectionLimit: options.maxSelection ?? 10
  });

  if (selection.canceled || selection.assets.length === 0) {
    return [];
  }

  return selection.assets.map((asset, index) =>
    normalizeAsset(
      asset,
      asset.type === "video" ? `video-${index + 1}.mp4` : `photo-${index + 1}.jpg`,
      "LIBRARY"
    )
  );
}

export async function pickSingleImage(): Promise<PickedImageFile | null> {
  const [asset] = await pickFromLibrary(ImagePicker.MediaTypeOptions.Images);
  if (!asset) {
    return null;
  }
  return asset;
}

export async function pickSingleStoryMedia(): Promise<PickedMediaFile | null> {
  const [asset] = await pickFromLibrary(ImagePicker.MediaTypeOptions.All);
  return asset ?? null;
}

export async function pickChatLibraryMedia(
  mediaKind: "IMAGE" | "VIDEO",
  options: LibraryPickOptions = {}
): Promise<PickedMediaFile[]> {
  return pickFromLibrary(
    mediaKind === "VIDEO"
      ? ImagePicker.MediaTypeOptions.Videos
      : ImagePicker.MediaTypeOptions.Images,
    {
      allowsMultipleSelection: true,
      maxSelection: options.maxSelection ?? 10
    }
  );
}

export async function captureChatPhoto(): Promise<PickedImageFile | null> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    throw new Error("Camera permission was not granted on this device");
  }

  const result = await ImagePicker.launchCameraAsync({
    cameraType: ImagePicker.CameraType.back,
    mediaTypes: ImagePicker.MediaTypeOptions.Images,
    quality: 1
  });

  if (result.canceled || result.assets.length === 0) {
    return null;
  }

  return normalizeAsset(result.assets[0], "camera-photo.jpg", "CAMERA");
}

export async function captureChatVideo(
  cameraType: ImagePicker.CameraType = ImagePicker.CameraType.back
): Promise<PickedMediaFile | null> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    throw new Error("Camera permission was not granted on this device");
  }

  const result = await ImagePicker.launchCameraAsync({
    cameraType,
    mediaTypes: ImagePicker.MediaTypeOptions.Videos,
    quality: 1,
    videoMaxDuration: 180
  });

  if (result.canceled || result.assets.length === 0) {
    return null;
  }

  return normalizeAsset(result.assets[0], "camera-video.mp4", "CAMERA");
}
