import type { ForumTopic } from "../../types";
import {
  buildForumTopicsSubtitle,
  buildTopicMeta,
  buildTopicTitle
} from "./forumTopicsPresentation";

function createTopic(overrides: Partial<ForumTopic> = {}): ForumTopic {
  return {
    topicId: "topic-1",
    chatId: "chat-1",
    title: "General",
    iconEmoji: null,
    generalTopic: false,
    closed: false,
    hidden: false,
    createdBy: "user-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    updatedAt: "2026-03-27T10:00:00.000Z",
    lastMessageAt: null,
    ...overrides
  };
}

describe("forumTopicsPresentation", () => {
  it("builds topic title and metadata", () => {
    expect(buildTopicTitle(createTopic({ iconEmoji: "🔥", title: "Launch" }))).toBe("🔥 Launch");
    expect(buildTopicMeta(createTopic())).toContain("Open");
    expect(buildTopicMeta(createTopic({ generalTopic: true, closed: true }))).toContain("General");
    expect(buildForumTopicsSubtitle(1)).toBe("1 topic");
    expect(buildForumTopicsSubtitle(3)).toBe("3 topics");
  });
});
