import { buildBotMetaLine, buildUserMetaLines } from "./contactsPresentation";

describe("contactsPresentation", () => {
  it("builds user meta lines with blocked and inline labels", () => {
    expect(
      buildUserMetaLines(
        {
          username: "helper_bot",
          phoneNumber: "+375291234567",
          bot: true,
          botSupportsInline: true,
          online: false,
          lastSeenAt: null
        },
        true
      )
    ).toEqual([
      "@helper_bot - bot - inline - blocked",
      "bot account - +375291234567"
    ]);
  });

  it("builds bot summary line", () => {
    expect(buildBotMetaLine("team_helper", true)).toEqual(["@team_helper - bot - inline"]);
  });
});
