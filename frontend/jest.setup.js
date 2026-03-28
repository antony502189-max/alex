const mockSecureStore = new Map();

jest.mock("expo-secure-store", () => ({
  getItemAsync: jest.fn(async (key) => mockSecureStore.get(key) ?? null),
  setItemAsync: jest.fn(async (key, value) => {
    mockSecureStore.set(key, value);
  }),
  deleteItemAsync: jest.fn(async (key) => {
    mockSecureStore.delete(key);
  })
}));

jest.mock("expo-image-picker", () => ({
  MediaTypeOptions: {
    Images: "Images",
    Videos: "Videos",
    All: "All"
  },
  CameraType: {
    back: "back"
  },
  requestMediaLibraryPermissionsAsync: jest.fn(async () => ({ granted: true })),
  requestCameraPermissionsAsync: jest.fn(async () => ({ granted: true })),
  launchImageLibraryAsync: jest.fn(async () => ({ canceled: true, assets: [] })),
  launchCameraAsync: jest.fn(async () => ({ canceled: true, assets: [] }))
}));

jest.mock("expo-contacts", () => ({
  Fields: {
    PhoneNumbers: "PhoneNumbers",
    Image: "Image"
  },
  requestPermissionsAsync: jest.fn(async () => ({ status: "granted" })),
  getContactsAsync: jest.fn(async () => ({ data: [] }))
}));

jest.mock("expo-location", () => ({
  Accuracy: {
    Balanced: "Balanced"
  },
  getForegroundPermissionsAsync: jest.fn(async () => ({ granted: true })),
  requestForegroundPermissionsAsync: jest.fn(async () => ({ granted: true })),
  getCurrentPositionAsync: jest.fn(async () => ({
    coords: {
      latitude: 53.9,
      longitude: 27.5667
    }
  })),
  reverseGeocodeAsync: jest.fn(async () => [])
}));

jest.mock("expo-device", () => ({
  isDevice: true
}));

jest.mock("expo-notifications", () => ({
  setNotificationHandler: jest.fn(),
  requestPermissionsAsync: jest.fn(async () => ({ status: "granted" })),
  getExpoPushTokenAsync: jest.fn(async () => ({ data: "push-token" }))
}));

jest.mock("expo-sqlite", () => ({
  openDatabaseAsync: jest.fn(async () => ({
    execAsync: jest.fn(async () => undefined),
    withTransactionAsync: jest.fn(async (callback) => callback()),
    runAsync: jest.fn(async () => undefined),
    getAllAsync: jest.fn(async () => [])
  }))
}));

jest.mock("expo-file-system/legacy", () => ({
  documentDirectory: "file://documents/",
  cacheDirectory: "file://cache/",
  getInfoAsync: jest.fn(async () => ({ exists: false, isDirectory: false, size: 0 })),
  makeDirectoryAsync: jest.fn(async () => undefined),
  copyAsync: jest.fn(async () => undefined),
  deleteAsync: jest.fn(async () => undefined),
  downloadAsync: jest.fn(async () => ({ uri: "file://downloaded" }))
}));
