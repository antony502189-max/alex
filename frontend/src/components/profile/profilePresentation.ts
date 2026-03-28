import type { AuthSecurityEvent, DevicePasskey } from "../../types";

export type ProfilePrivacyValue = "EVERYBODY" | "CONTACTS" | "NOBODY";

export const PROFILE_PRIVACY_OPTIONS: ProfilePrivacyValue[] = [
  "EVERYBODY",
  "CONTACTS",
  "NOBODY"
];

export function formatAccountTitle(displayName: string, active: boolean) {
  return active ? `${displayName} | active` : displayName;
}

export function formatAccountIdentity(phoneNumber: string, username: string | null) {
  return username ? `${phoneNumber} | @${username}` : phoneNumber;
}

export function formatSecurityEventTitle(event: AuthSecurityEvent) {
  return event.severity ? `${event.eventType} | ${event.severity}` : event.eventType;
}

export function formatSecurityEventDeviceMeta(event: AuthSecurityEvent) {
  return (
    [event.deviceName, event.platform, event.appVersion].filter(Boolean).join(" | ") ||
    "Unknown device"
  );
}

export function formatSecurityEventNetworkMeta(event: AuthSecurityEvent) {
  return [event.ipAddress, event.userAgent].filter(Boolean).join(" | ");
}

export function formatPasskeyUsage(passkey: DevicePasskey) {
  return passkey.lastUsedAt
    ? `Last used ${new Date(passkey.lastUsedAt).toLocaleString()}`
    : "Not used yet";
}
