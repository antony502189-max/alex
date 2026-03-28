import { parseAlexDeepLink } from "./deepLinks";

describe("parseAlexDeepLink", () => {
  it("parses join links", () => {
    expect(parseAlexDeepLink("alex://join/invite-token")).toEqual({
      type: "JOIN",
      token: "invite-token"
    });
  });

  it("parses call links", () => {
    expect(parseAlexDeepLink("alex://call/token-123")).toEqual({
      type: "CALL",
      token: "token-123"
    });
    expect(parseAlexDeepLink("alex://call?token=token-124")).toEqual({
      type: "CALL",
      token: "token-124"
    });
    expect(parseAlexDeepLink("tg://call/token-456")).toEqual({
      type: "CALL",
      token: "token-456"
    });
    expect(parseAlexDeepLink("telegram://call?token=token-789")).toEqual({
      type: "CALL",
      token: "token-789"
    });
    expect(parseAlexDeepLink("t.me/call/token-999")).toEqual({
      type: "CALL",
      token: "token-999"
    });
    expect(parseAlexDeepLink("https://t.me/call?token=token-1000")).toEqual({
      type: "CALL",
      token: "token-1000"
    });
  });

  it("parses chat links with topic", () => {
    expect(parseAlexDeepLink("alex://chat/chat-1?topicId=topic-9")).toEqual({
      type: "CHAT",
      chatId: "chat-1",
      topicId: "topic-9"
    });
  });

  it("parses public usernames and telegram invite links", () => {
    expect(parseAlexDeepLink("@team")).toEqual({
      type: "JOIN",
      token: "@team"
    });
    expect(parseAlexDeepLink("tg://resolve?domain=team")).toEqual({
      type: "JOIN",
      token: "@team"
    });
    expect(parseAlexDeepLink("telegram://join?invite=invite-token")).toEqual({
      type: "JOIN",
      token: "invite-token"
    });
    expect(parseAlexDeepLink("t.me/team")).toEqual({
      type: "JOIN",
      token: "@team"
    });
    expect(parseAlexDeepLink("https://t.me/team")).toEqual({
      type: "JOIN",
      token: "@team"
    });
    expect(parseAlexDeepLink("telegram.me/+invite-token")).toEqual({
      type: "JOIN",
      token: "invite-token"
    });
    expect(parseAlexDeepLink("https://t.me/+invite-token")).toEqual({
      type: "JOIN",
      token: "invite-token"
    });
  });

  it("parses http call and chat links", () => {
    expect(parseAlexDeepLink("alex.example/call/room-1")).toEqual({
      type: "CALL",
      token: "room-1"
    });
    expect(parseAlexDeepLink("https://alex.example/call/room-1")).toEqual({
      type: "CALL",
      token: "room-1"
    });
    expect(parseAlexDeepLink("https://alex.example/chat/chat-1?topicId=topic-9")).toEqual({
      type: "CHAT",
      chatId: "chat-1",
      topicId: "topic-9"
    });
  });

  it("returns null for unsupported links", () => {
    expect(parseAlexDeepLink("https://example.com")).toBeNull();
  });
});
