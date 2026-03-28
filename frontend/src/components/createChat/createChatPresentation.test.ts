import type { UserSearchResult } from "../../types";
import {
  buildCollectionAboutPlaceholder,
  buildCollectionTitlePlaceholder,
  buildCreateChatEmptyState,
  buildCreateChatSubmitLabel,
  buildCreateChatSubtitle,
  buildCreateChatTitle,
  buildCreateChatUserMeta,
  canSubmitCollectionChat
} from "./createChatPresentation";

function createUser(overrides: Partial<UserSearchResult> = {}): UserSearchResult {
  return {
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    username: "alex",
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    photoUrl: null,
    photoAccessExpiresAt: null,
    online: true,
    lastSeenAt: null,
    ...overrides
  };
}

describe("createChatPresentation", () => {
  it("builds mode-specific copy and submit labels", () => {
    expect(buildCreateChatTitle("direct")).toBe("New direct chat");
    expect(buildCreateChatTitle("group")).toBe("New group");
    expect(buildCreateChatSubtitle("channel")).toContain("Choose people");
    expect(buildCollectionTitlePlaceholder("group")).toBe("Group title");
    expect(buildCollectionAboutPlaceholder("channel")).toBe("Channel description");
    expect(buildCreateChatSubmitLabel("group", 3, false)).toBe("Create group (3)");
    expect(buildCreateChatSubmitLabel("channel", 2, true)).toBe("Creating...");
  });

  it("derives submit state, empty state, and user meta", () => {
    expect(canSubmitCollectionChat("group", "Team", 0)).toBe(false);
    expect(canSubmitCollectionChat("group", "Team", 2)).toBe(true);
    expect(canSubmitCollectionChat("channel", "News", 0)).toBe(true);
    expect(buildCreateChatEmptyState("a")).toContain("two characters");
    expect(buildCreateChatEmptyState("alex")).toContain("No users");
    expect(buildCreateChatUserMeta(createUser())).toEqual(
      expect.arrayContaining(["@alex", "online - +375291111111"])
    );
    expect(buildCreateChatUserMeta(createUser({ bot: true, username: null }))).toEqual(
      expect.arrayContaining(["bot - +375291111111"])
    );
  });
});
