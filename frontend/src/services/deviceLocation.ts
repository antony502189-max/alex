import * as Location from "expo-location";

export type DeviceLocationSnapshot = {
  address: string | null;
  latitude: number;
  longitude: number;
};

function formatAddress(parts: Location.LocationGeocodedAddress[] | null | undefined) {
  const first = parts?.[0];
  if (!first) {
    return null;
  }

  const line = [
    first.name,
    first.street,
    first.city,
    first.region,
    first.postalCode,
    first.country
  ]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join(", ");

  return line || null;
}

async function ensureForegroundPermission() {
  const existing = await Location.getForegroundPermissionsAsync();
  if (existing.granted) {
    return;
  }

  const requested = await Location.requestForegroundPermissionsAsync();
  if (!requested.granted) {
    throw new Error("Location permission was not granted on this device");
  }
}

export const deviceLocation = {
  async getCurrentPosition(): Promise<DeviceLocationSnapshot> {
    await ensureForegroundPermission();

    const position = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced
    });

    const latitude = position.coords.latitude;
    const longitude = position.coords.longitude;

    let address: string | null = null;
    try {
      address = formatAddress(
        await Location.reverseGeocodeAsync({
          latitude,
          longitude
        })
      );
    } catch {
      address = null;
    }

    return {
      address,
      latitude,
      longitude
    };
  }
};
