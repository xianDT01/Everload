# Chat and floating player

The standalone `/chat` page uses a graphite layout with conversation filters,
profile navigation and a low-contrast music background. The Windows XP Messenger
embeds the same chat with `embedded=true` and retains its existing appearance.

Standalone private-chat, group-creation, group-information and confirmation
dialogs share `chat-dialog.css`. Child dialogs opt in with `modern=true`, so
the embedded Messenger keeps its previous palette. Dialogs trap and restore
keyboard focus, support Escape and keep long forms/member lists scrollable
without losing the header or footer on small screens.

The floating player is another view of `MusicService.mainPlayer`, not a separate
playback engine. Clicking its summary expands controls; Escape or an outside
click collapses them. It appears only when a track exists. Other routes retain
their existing mini/full/Modern player presentation.

The island overlays the existing page without reserving vertical space
(`--floating-player-clearance` stays zero). Chat and profile retain their
integrated header placement. On small screens other pages default to the
bottom edge to leave navigation accessible. Its drag handle supports pointer,
touch and arrow-key movement. A lock pins its position; the expanded player
offers a reset button. Position and lock are browser-local preferences, not
account data. Positions are clamped on resize and expansion to stay reachable.

## Account Settings

`/profile` offers playback, account, privacy and security sections. The playback
section previews the island and existing bar without applying draft changes.
Saving persists `floatingPlayerStyle` (`island` or `bar`) in the user profile;
the frontend loads it at sign-in and resets it on account changes or logout.
An unset preference preserves the previous defaults (island in chat, bar on
other ordinary pages). Modern, Windows and DJ retain their own players.

The nullable user column is added by the existing Hibernate schema-update
configuration; it does not rewrite established profile preferences.

## Generated Asset

- File: `everload-front/src/assets/images/chat-music-texture.png`
- Tool: built-in image generation.
- Prompt: "Create a square seamless repeating monochrome music doodle wallpaper tile for a dark chat interface. Thin precise hand-drawn outlines of headphones, vinyl records, cassette tapes, music notes and tiny sound waves, spaced apart with lots of breathing room, no text, no letters, no logos. Flat charcoal #191b1d background with subtle graphite #2b2e30 lines, extremely low contrast, no gradients, no glow, no 3D, no shadows, no color. All shapes roughly same small size, varied rotations, seamless tile, refined understated UI texture. 1024x1024."
