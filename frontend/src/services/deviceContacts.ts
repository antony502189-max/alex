import * as Contacts from "expo-contacts";
import type { DeviceContactRecord } from "../types";

function normalizePhoneNumber(value: string | undefined | null) {
  if (!value) {
    return "";
  }
  return value.replace(/[^\d+]/g, "");
}

export const deviceContacts = {
  async requestAndList(limit = 100): Promise<DeviceContactRecord[]> {
    const permission = await Contacts.requestPermissionsAsync();
    if (permission.status !== "granted") {
      throw new Error("Contacts permission was not granted on this device");
    }

    const response = await Contacts.getContactsAsync({
      fields: [Contacts.Fields.PhoneNumbers, Contacts.Fields.Image],
      pageSize: limit
    });

    return response.data
      .map((contact) => {
        const phoneNumbers = (contact.phoneNumbers ?? [])
          .map((entry) => normalizePhoneNumber(entry.number))
          .filter(Boolean);

        if (phoneNumbers.length === 0) {
          return null;
        }

        return {
          contactId: contact.id,
          displayName: contact.name ?? [contact.firstName, contact.lastName].filter(Boolean).join(" "),
          phoneNumbers,
          firstName: contact.firstName ?? null,
          lastName: contact.lastName ?? null,
          thumbnailUri: contact.imageAvailable ? contact.image?.uri ?? null : null
        } satisfies DeviceContactRecord;
      })
      .filter((contact): contact is DeviceContactRecord => Boolean(contact));
  }
};
