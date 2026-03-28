import React from "react";
import { ChatComposerSurface } from "./ChatComposerSurface";
import { PendingAttachmentBar } from "./PendingAttachmentBar";

type ChatComposerSectionProps = {
  composerSurface: React.ComponentProps<typeof ChatComposerSurface>;
  pendingAttachmentBar: React.ComponentProps<typeof PendingAttachmentBar>;
};

export function ChatComposerSection({
  composerSurface,
  pendingAttachmentBar
}: ChatComposerSectionProps) {
  return (
    <>
      <PendingAttachmentBar {...pendingAttachmentBar} />
      <ChatComposerSurface {...composerSurface} />
    </>
  );
}
