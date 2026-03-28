# Core Messenger MVP Regression Checklist

Use this checklist after navigation, account-store, messaging, or media changes.

## Auth and Sessions

- OTP login works and lands on the chats tab.
- App restart restores the last active account.
- Account-scoped feature profile restores with the active account and does not leak across account switching.
- Consumer feature-profile coercion keeps `secretChats` disabled even if cached or server-provided data still says otherwise.
- `Add account` opens the auth flow from settings.
- Adding a second account keeps the first account available locally.
- Switching accounts updates chats, folders, drafts, and settings surface for the selected account only.
- Switching accounts also restores account-scoped local notification, data/storage, appearance, disclosure, and chat-list state for the selected account only.
- `Log out current` removes only the active account from the device and keeps the remaining local accounts intact.
- Cold start and offline restore do not flash `stories`, `bots`, or `secret chats` before the cached/server profile is known.

## Chats and Navigation

- Chats list opens direct chats, groups, channels, and Saved Messages.
- Long-press inbox actions can archive, mute, pin, and mark unread without leaving the chats list.
- Chats, folders, archived chats, global search, and channel headers describe channel audience as subscribers rather than members.
- Archive flow opens archived chats and returns back to the main list.
- Folder filter chips and `Manage` entry still work after store switching.
- Join by invite link screen opens and joins a chat successfully.
- Join by invite link also recognizes pasted call links and app chat links, then routes them into the same final call/chat flow instead of attempting a join request.
- Public chat discoveries in Join by Link show `Request access` instead of `Join` when the target chat requires approval.
- The primary Join by Link CTA opens a matching local public chat directly from parsed `@username` input and otherwise mirrors exact public discovery state instead of staying generic.
- Public chat discoveries inside Join by Link open already-joined chats directly instead of trying to join them again.
- Public `@username`, `https://t.me/...`, and app join links normalize into the same join flow.
- Scheme-less `t.me/...`, `telegram.me/...`, Telegram-style `tg://...` links, and app-domain invite/call links still normalize correctly when pasted by the user.
- Global search opens and can navigate into a target chat/message.
- Global search opens local public chats directly from parsed `@username` quick actions instead of always sending them through the generic join flow.
- Parsed `@username` links from root-managed surfaces open a matching local public chat directly instead of always detouring through Join by Link.
- Invite links, call links, app chat links, and `@username` mentions embedded directly in chat text route through the same internal join/call/chat flow instead of always opening externally.
- The same internal routing also works from service-message text, pinned preview, pinned history, scheduled preview, and reply preview surfaces inside the chat screen.
- Contacts tab opens, creates a direct chat, and returns to the chats shell.
- Members screen opens from a chat and can navigate to shared media.
- Members/manage surface can open the linked discussion chat directly when one is configured.
- Expired invite links in Members/manage stay visible with an explicit expired state, stop offering share actions, and can still be revoked for cleanup.
- Invite links in Members/manage show `Limit reached` when their usage cap is exhausted and stop offering share actions until cleaned up.
- Chat info opens from direct chats, groups, and channels, and group/channel info can hand off into members/manage surfaces.
- Chat info reflects public join, approval-required, and invite-only access states without contradictory join labels.
- Channel chat info can open the linked discussion chat directly when one is configured.
- External call links and deep links route into the correct chat or call surface.

## Messaging and Media

- Text send, edit, delete, reply, forward, and reactions still work in a direct chat.
- Long-pressing a message enters selection mode, tapping additional messages adds or removes them from the selection, and clearing selection returns the chat to normal interaction mode.
- Multi-select batch actions can share selected messages, forward forwardable messages, and delete only messages owned by the current account without breaking single-message reply/edit/pin/report actions.
- Polls, stickers, GIFs, file uploads, voice notes, location, and contact messages still render and send.
- Gallery picker can attach image/video items from the photo library.
- Camera capture can create photo, video, and video-note attachments for the composer.
- Failed media uploads expose retry and do not break the composer state.
- Uploaded pending video and video-note attachments can be trimmed before send without breaking attachment order.
- Pending attachments surface shows a batch summary, per-item upload status, and live progress when uploads are actively running.
- Composer shows an explicit status card for special send modes such as editing, silent send, poll mode, location/contact mode, and attachment preparation instead of silently disabling actions.
- Retryable queued-attachment upload failures during `send`, `schedule`, and `send when online` never fall through to a partial live API request; the whole message is queued for outbox retry instead.
- Poll, sticker, and inline-bot quick-send flows still clear reply/selection state correctly, and retryable offline failures queue the action instead of leaving the composer half-reset.
- Chat bubbles, shared media tiles, pending media rows, and recent GIF picker cards keep showing a visual preview when only a thumbnail or local image URI is available.
- Shared media viewer opens from a chat and can open the full media viewer modal.
- Closing the full media viewer after opening it from shared media returns to the shared media screen instead of dropping the modal stack.
- Voice notes and audio entries inside shared media can play and stop inline without leaving the screen.
- Media, file, and link entries inside shared media can jump back to the source message in chat context.
- Invite links, call links, and app chat links opened from shared media `Links` route into the same internal join/call/chat flow as other discovery entry points.
- Full media viewer opened from shared media can also jump directly to the source message in chat context.
- Full media viewer opened directly from a chat media bubble can also jump directly to the source message in chat context.
- Links and nested message actions inside a selected bubble do not escape selection mode; they toggle selection instead of unexpectedly opening media, links, or reactions.
- Scheduled messages and send-when-online flows still open and submit successfully.
- Pending attachments can be reordered before send.

## Presence

- Chats list refreshes direct-chat `online` and `last seen` state on foreground resume and periodic polling.
- Direct chat header updates presence without breaking cached offline chat snapshots.
- Contacts search and saved contacts refresh presence on screen focus and while the screen stays open.
- Members screen shows refreshed presence for loaded participants on focus, foreground resume, and periodic polling.

## Settings and Device Data

- Profile screen loads account details, 2FA state, and security events.
- Settings home opens dedicated `Profile`, `Privacy & Security`, `Devices`, `Notifications & Sounds`, `Data & Storage`, `Appearance`, `Language`, `Blocked Users & Exceptions`, and `Help & Privacy` sections.
- Device contacts permission prompt appears and local phonebook records render after approval.
- Sessions screen opens from profile and still lists active sessions.
- Consumer settings surfaces clearly disclose server-side storage and do not expose `secret chats`, `admin compliance`, or direct lawful-export entry points.
- Push registration side effects do not break the settings flow on app launch.

## Flagged Surfaces

- Calls tab opens and recent calls screen renders.
- Active call screen still opens for ongoing calls.
- Join-by-call-link input only appears when call-link capability is enabled.
- The Calls-tab join field reroutes pasted invite/chat links into the same internal join/chat flow instead of treating them like broken call tokens.
- The Calls-tab parsed `@username` quick action opens a matching local public chat directly instead of always routing through the generic join flow.
- Active group calls expose share actions for non-revoked call links when call-link capability is enabled.
- Revoked call links stay visible with an explicit revoked state instead of silently looking inactive.
- Expired call links stay visible with an explicit expired state and do not offer share actions as if they were still valid.
- Calls history differentiates missed, declined, and canceled entries instead of collapsing them into one generic label.
- Call moderation controls only appear when moderation capability is enabled.
- Screen-share control path only appears when the screen-sharing feature flag is enabled, and unsupported devices/builds show it as unavailable instead of hiding it silently.
- Active call controls explain why mic, camera, or screen-share actions are unavailable instead of only rendering silent disabled states, and unsupported screen sharing stays visible as unavailable rather than disappearing.
- Stories tab opens, and story creation modal still renders.
- After publishing a story, the app returns to the stories tab, refreshes the feed, and focuses the newly created story instead of dropping the user back into an unfocused feed.
- Story feed, selected story view, and archive surfaces expose clear lifecycle labels such as `Your story`, `New to you`, `Seen`, and `Expired`.
- Archived stories can be opened back into the story preview surface for inspection before deletion instead of being limited to a static text list.
- Consumer MVP does not expose secret-chat entry points from chats list, chat header, or chat info.
- Bot developer and bot mini app surfaces still open when feature flags allow them.
- Bot mini app `Refresh session` requests a new signed launch session instead of only re-rendering the existing WebView.
- Refresh or runtime failures inside a bot mini app stay non-fatal when a signed session already exists: the current mini app remains visible and the user sees an inline notice instead of a full-screen error card.
- Direct bot chats expose command loading and retry states instead of silently hiding command shortcuts when the bot command request fails.
- Inline bot discovery exposes retryable error states instead of collapsing failed inline lookups into a misleading empty-results panel.
- Direct bot profiles use bot-specific presentation and capability labels instead of human presence wording, and they do not expose voice/video call actions.
- Bot profiles can load published command shortcuts with local `loading/error/retry` states instead of relying only on the in-chat composer surface.
