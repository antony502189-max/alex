import React from "react";
import { ActivityIndicator } from "react-native";
import { render } from "@testing-library/react-native";
import { ScreenFeedback } from "./ScreenFeedback";

describe("ScreenFeedback", () => {
  it("renders nothing without feedback state", () => {
    const screen = render(<ScreenFeedback />);

    expect(screen.toJSON()).toBeNull();
  });

  it("renders loader, error and notice messages", () => {
    const screen = render(
      <ScreenFeedback error="Something went wrong" loading notice="Saved successfully" />
    );

    expect(screen.UNSAFE_getByType(ActivityIndicator)).toBeTruthy();
    expect(screen.getByText("Something went wrong")).toBeTruthy();
    expect(screen.getByText("Saved successfully")).toBeTruthy();
  });
});
