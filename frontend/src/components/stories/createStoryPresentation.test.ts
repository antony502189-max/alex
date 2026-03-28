import type { Contact } from "../../types";
import {
  getCreateStoryAudienceHint,
  getCreateStoryAudienceTitle,
  getCreateStoryMediaKind,
  getCreateStoryPreviewLabel,
  getSelectedStoryContactsLabel,
  inferStoryDurationMs
} from "./createStoryPresentation";

function createContact(overrides: Partial<Contact> = {}): Contact {
  return {
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    contactName: "Kate",
    displayName: "Kate",
    lastSeenAt: null,
    online: false,
    phoneNumber: "+375291111111",
    photoAccessExpiresAt: null,
    photoUrl: null,
    userId: "user-1",
    username: "kate",
    ...overrides
  };
}

describe("createStoryPresentation", () => {
  it("builds preview, audience and contact labels", () => {
    expect(getCreateStoryPreviewLabel(" Hello ", null)).toBe("Hello");
    expect(getCreateStoryPreviewLabel("", null)).toBe("Your story preview");
    expect(getCreateStoryPreviewLabel("", {
      name: "clip.mp4",
      type: "video/mp4",
      uri: "file://clip.mp4"
    })).toBe("Add a caption if you want");
    expect(getCreateStoryAudienceHint("CUSTOM")).toBe("Hand-pick who can view this story.");
    expect(getCreateStoryAudienceTitle("CLOSE_FRIENDS")).toBe("Close friends");
    expect(getSelectedStoryContactsLabel([createContact()])).toBe("Selected: Kate");
  });

  it("infers media type and duration", () => {
    expect(inferStoryDurationMs(null)).toBeNull();
    expect(inferStoryDurationMs({
      durationMs: 22000,
      name: "clip.mp4",
      type: "video/mp4",
      uri: "file://clip.mp4"
    })).toBe(22000);
    expect(getCreateStoryMediaKind({
      name: "cover.jpg",
      type: "image/jpeg",
      uri: "file://cover.jpg"
    })).toBe("IMAGE");
  });
});
