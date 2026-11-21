# Windows mode: Winamp-style player

EverLoad retains its Angular player, shared queue and existing audio engine. It
does not embed the complete Webamp application or its experimental media API.

The player includes separate play, pause and stop controls, a compact title-bar
mode, a seek slider, stereo balance, preamp and ten independent equalizer bands.
EQ presets can be imported and exported as Winamp EQF files. Files are validated
before parsing. Import/export actions live in the equalizer's three-dot menu.
Audio effects are unavailable for embedded YouTube playback,
whose audio does not pass through EverLoad's Web Audio graph.

EQF compatibility uses Jordan Eldredge's `winamp-eqf` package (ISC), part of the
[Webamp project](https://github.com/captbaritone/webamp). Version 1.0.0 is pinned
for compatibility with the project's Node toolchain; it has no runtime
dependencies. The rest of Webamp and its skins are not bundled.

The optional Visuals window lazy-loads Butterchurn 2.6.7 and the minimal preset
collection from butterchurn-presets 2.4.7 (MIT). Preset author names remain in
the selector. It analyzes the existing audio graph without a second audio output.
Twelve presets are grouped into fluids, geometry, fractals, tunnels, spirals and
neon, using the same installed collection rather than adding another preset pack.
Rendering is capped at 30 fps and 1280x720, pauses with playback or a hidden tab,
and drops to 10 fps with reduced-motion preferences. Closing the window releases
its audio connection and WebGL context without closing the player's AudioContext.
Embedded YouTube audio cannot drive the visualization and displays an explicit
unavailable state. No sample tracks or demo audio are included.

Focused tests cover EQF validation, controls, audio routing and desktop icon
placement. Browser checks exercise real generated stereo audio and the compact
layout at desktop viewport widths of 980, 1366 and 1920 pixels.
