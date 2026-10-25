# Everload DJ Console

The two-deck console keeps Everload's NAS browser, local files, YouTube player,
queues, session history and MIDI mappings. Its equipment-style layout uses a cyan
deck A, a red deck B and a central mixer.

Pro is the only console layout, with larger platters, mirrored performance racks
and rotary-style EQ controls. The Classic layout and its selector were removed.
Its platter size follows the available compartment rather than a fixed pixel size.
The five primary transport buttons are previous, cue, play/pause, BPM sync and
next. The per-deck overflow menu keeps queue access, reset and positional sync.
The obsolete `everload.dj.skin` preference is cleared on startup, so returning
users always receive Pro without affecting their queues, cues or MIDI mappings.
The Dark switch restores a neutral black palette. Its `everload.dj.theme`
preference does not alter playback or the layout.

## Performance Controls

- TAP estimates tempo from a rolling median of the last eight tap intervals.
  After a pause longer than two seconds, a new sequence starts. At least three
  taps are required. Tapped tempo is local to the current deck/track; it does not
  overwrite file metadata.
- Beat jump moves by 1, 2, 4, 8 or 16 beats, bounded by track duration. Jumping
  exits an active loop.
- Loops support 4/8/16 beats and half/double length between 1/4 and 64 beats.
  Loop lengths are calculated in source-media seconds, independent of pitch.
- Optional quantization snaps cues and loop starts to the tempo grid. The grid
  originates at track start, or the last successful TAP. It is not automatic
  onset detection or a fully analyzed beat grid.
- Hot cues, tapped tempo and quantization reset when the loaded track changes,
  including when the same relative filename belongs to a different NAS root.
- Pitch is bounded to +/-10 percent. Double-click its label to reset it.
  BPM synchronization can only reach targets within that range. Positional SYNC
  retains the previous relative-position behavior; it is not phase/beat sync.

## Signal and Source Limitations

The upper monitor displays real live time-domain samples, not a decoded full-track
waveform. Meter values are approximate levels, not calibrated broadcast meters.
Visuals run at up to 24 fps while playing, 2 fps when idle, and stop drawing while
the document is hidden. Time-domain buffers are reused instead of allocated each
frame. Only the two active decks are analyzed; browsing never decodes the library.

The YouTube iframe does not expose decoded PCM audio. For that source, the console
shows an unavailable-signal notice and disables pitch, EQ, filter and echo controls
that cannot be applied to the embedded audio. Transport, seeking, queues and
duration-based operations remain subject to YouTube player timing and buffering.
Browser-based loops are not sample-accurate. No stems, DVS, scratch engine or
independent headphone output is claimed.

## Provenance

The layout follows the approved Everload mockup. Mixxx's performance features were
reviewed as functional inspiration; no Mixxx C++ source, skin artwork or VirtualDJ
assets have been incorporated. Implementation uses Everload's existing Web Audio
and playback services.

## MIDI Connection

Open MIDI and explicitly connect to request browser permission and unlock the audio
contexts. Access can be retried after correcting browser permissions. Inputs are
refreshed on device connection/disconnection; a manually disabled input stays off.

LEARN accepts Note On/Off buttons, absolute 7-bit CC controls and 14-bit pitch bend.
Note release is never captured as a new mapping. Pitch bend uses both data bytes
and a stable per-channel identity. MIDI clock, aftertouch, SysEx and channel-mode
messages are ignored. The diagnostic field shows the latest accepted MIDI message
without writing a persistent message log. Continuous actions are coalesced in 16ms
batches; note buttons retain immediate press/release delivery.

Mappings are stored in this browser. Existing Note Off and pitch bend mappings are
canonicalized on load. Manufacturer-wide templates are suggestions and are only
applied explicitly; only hardware-verified exact-model presets may auto-apply.
Previously saved custom mappings are retained. TAP, beat jumps, loop size and hot
cues are available in LEARN alongside the original transport, fader and EQ actions.

Compatibility with a physical controller must still be tested with its exact
model, MIDI mode and driver. HID-only controllers, relative encoders, jog/scratch,
motorized faders, LED feedback, vendor-specific initialization and 14-bit CC pairs
are not implemented. This is not a universal Mixxx controller driver layer.

Protocol references: [Web MIDI API](https://www.w3.org/TR/webmidi/) and
[MIDI 1.0 message summary](https://midi.org/summary-of-midi-1-0-messages).
