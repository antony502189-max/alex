import type { ChatMember } from "../../types";
import {
  buildPublicChatShareUrl,
  getMemberPermissionLabels,
  getMembersChatTypeLabel,
  isInviteLinkExpired,
  isInviteLinkLimitReached,
  sortMembers
} from "./membersPresentation";

function createMember(overrides: Partial<ChatMember> = {}): ChatMember {
  return {
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    photoUrl: null,
    photoAccessExpiresAt: null,
    role: "MEMBER",
    joinedAt: "2026-03-27T10:00:00.000Z",
    lastReadAt: null,
    lastSentMessageAt: null,
    canSendMessages: true,
    canManageMembers: false,
    canManageInviteLinks: false,
    canManageMessages: false,
    canPinMessages: false,
    canApproveJoinRequests: false,
    canPostMessages: false,
    anonymousAdmin: false,
    restrictedUntil: null,
    restrictionReason: null,
    ...overrides
  };
}

describe("membersPresentation", () => {
  it("sorts owners and admins before regular members", () => {
    const sorted = sortMembers([
      createMember({ displayName: "Mira", role: "MEMBER", userId: "user-3" }),
      createMember({ displayName: "Boris", role: "OWNER", userId: "user-1" }),
      createMember({ displayName: "Alex", role: "ADMIN", userId: "user-2" })
    ]);

    expect(sorted.map((member) => member.role)).toEqual(["OWNER", "ADMIN", "MEMBER"]);
  });

  it("builds permission labels for members and channels", () => {
    const labels = getMemberPermissionLabels(
      createMember({
        role: "ADMIN",
        canManageMembers: true,
        canManageInviteLinks: true,
        canApproveJoinRequests: true,
        canPostMessages: true,
        anonymousAdmin: true
      }),
      "CHANNEL"
    );

    expect(labels).toEqual([
      "Members",
      "Invite links",
      "Join requests",
      "Anonymous",
      "Can post"
    ]);
  });

  it("resolves screen chat type labels", () => {
    expect(getMembersChatTypeLabel("GROUP")).toBe("Group");
    expect(getMembersChatTypeLabel("CHANNEL")).toBe("Channel");
  });

  it("builds a shareable public chat URL from the saved username", () => {
    expect(buildPublicChatShareUrl("@team")).toBe("https://alex.example/join/team");
    expect(buildPublicChatShareUrl("")).toBeNull();
  });

  it("detects expired invite links", () => {
    expect(
      isInviteLinkExpired(
        {
          chatId: "chat-1",
          createdAt: "2026-03-28T10:00:00.000Z",
          expiresAt: "2026-03-27T10:00:00.000Z",
          inviteLinkId: "invite-1",
          label: "Main link",
          lastUsedAt: null,
          revoked: false,
          shareUrl: "https://alex.example/invite-token",
          token: "invite-token",
          usageCount: 3,
          usageLimit: null
        },
        new Date("2026-03-28T10:00:00.000Z").getTime()
      )
    ).toBe(true);
  });

  it("detects invite links that reached their usage limit", () => {
    expect(
      isInviteLinkLimitReached({
        chatId: "chat-1",
        createdAt: "2026-03-28T10:00:00.000Z",
        expiresAt: null,
        inviteLinkId: "invite-1",
        label: "Main link",
        lastUsedAt: null,
        revoked: false,
        shareUrl: "https://alex.example/invite-token",
        token: "invite-token",
        usageCount: 3,
        usageLimit: 3
      })
    ).toBe(true);
  });
});
