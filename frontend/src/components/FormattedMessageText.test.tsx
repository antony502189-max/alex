import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { FormattedMessageText } from "./FormattedMessageText";

describe("FormattedMessageText", () => {
  it("opens detected links and mentions through the provided callback", () => {
    const onOpenLink = jest.fn();

    const screen = render(
      <FormattedMessageText
        onOpenLink={onOpenLink}
        text="Open t.me/team, then @alex_team"
      />
    );

    fireEvent.press(screen.getByText("t.me/team"));
    fireEvent.press(screen.getByText("@alex_team"));

    expect(onOpenLink).toHaveBeenNthCalledWith(1, "t.me/team");
    expect(onOpenLink).toHaveBeenNthCalledWith(2, "@alex_team");
  });
});
