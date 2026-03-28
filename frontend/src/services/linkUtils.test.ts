import {
  detectTextLinks,
  normalizeExternalLinkUrl,
  trimTrailingLinkPunctuation
} from "./linkUtils";

describe("linkUtils", () => {
  it("detects url-like links and usernames inside message text", () => {
    const detected = detectTextLinks(
      "Join t.me/team, call tg://call/room-77, or ping @alex_team."
    );

    expect(detected.map((entry) => entry.url)).toEqual([
      "t.me/team",
      "tg://call/room-77",
      "@alex_team"
    ]);
    expect(detected[0]).toEqual({
      start: 5,
      end: 14,
      rawEnd: 15,
      url: "t.me/team"
    });
  });

  it("trims trailing punctuation and normalizes scheme-less telegram links", () => {
    expect(trimTrailingLinkPunctuation("t.me/team,")).toBe("t.me/team");
    expect(normalizeExternalLinkUrl("t.me/team")).toBe("https://t.me/team");
    expect(normalizeExternalLinkUrl("https://example.com")).toBe("https://example.com");
  });
});
