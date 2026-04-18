export interface MidiPreset {
  name: string;
  /** messageId ("status-data1") → actionId */
  mappings: Record<string, string>;
}

/**
 * Each entry: pattern matched against the MIDI input device name (case-insensitive).
 * First match wins. Mappings are applied automatically on device connect when the
 * device has no stored user mappings.
 *
 * Status byte reference:
 *   Note On  Ch1=144  Ch2=145  Ch3=146  Ch4=147
 *   CC       Ch1=176  Ch2=177  Ch3=178  Ch4=179
 */
export const MIDI_PRESETS: { pattern: RegExp; preset: MidiPreset }[] = [

  // ─── Pioneer DDJ-SB / SB2 / SB3 / 200 / 400 / FLX4 ───────────────────────
  {
    pattern: /pioneer|ddj/i,
    preset: {
      name: 'Pioneer DDJ',
      mappings: {
        // Play / Pause
        '144-11': 'PLAY_A',
        '145-11': 'PLAY_B',
        // Cue
        '144-12': 'CUE_A',
        '145-12': 'CUE_B',
        // Sync
        '144-88': 'SYNC_A',
        '145-88': 'SYNC_B',
        // Volume faders (CC 32)
        '176-32': 'VOL_A',
        '177-32': 'VOL_B',
        // Crossfader (CC 31)
        '176-31': 'CROSSFADER',
        // EQ  Low/Mid/High  (CC 70/71/74)
        '176-70': 'EQ_LOW_A',
        '176-71': 'EQ_MID_A',
        '176-74': 'EQ_HIGH_A',
        '177-70': 'EQ_LOW_B',
        '177-71': 'EQ_MID_B',
        '177-74': 'EQ_HIGH_B',
        // Pitch / Tempo slider (CC 0)
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },

  // ─── Numark Party Mix / Mixtrack Pro / Mixtrack Platinum ──────────────────
  {
    pattern: /numark|party.?mix|mixtrack/i,
    preset: {
      name: 'Numark',
      mappings: {
        // Play / Pause
        '144-0': 'PLAY_A',
        '145-0': 'PLAY_B',
        // Cue
        '144-1': 'CUE_A',
        '145-1': 'CUE_B',
        // Sync
        '144-4': 'SYNC_A',
        '145-4': 'SYNC_B',
        // Volume faders (CC 9)
        '176-9': 'VOL_A',
        '177-9': 'VOL_B',
        // Crossfader (CC 1)
        '176-1': 'CROSSFADER',
        // EQ Low/Mid/High (CC 2/3/4)
        '176-2': 'EQ_LOW_A',
        '176-3': 'EQ_MID_A',
        '176-4': 'EQ_HIGH_A',
        '177-2': 'EQ_LOW_B',
        '177-3': 'EQ_MID_B',
        '177-4': 'EQ_HIGH_B',
        // Pitch/Tempo slider (CC 0)
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },

  // ─── Hercules DJControl Inpulse 200 / 300 / 500 / Starlight ───────────────
  {
    pattern: /hercules|djcontrol|inpulse|starlight/i,
    preset: {
      name: 'Hercules DJControl',
      mappings: {
        // Play / Pause (Note 64)
        '144-64': 'PLAY_A',
        '145-64': 'PLAY_B',
        // Cue (Note 65)
        '144-65': 'CUE_A',
        '145-65': 'CUE_B',
        // Sync (Note 68)
        '144-68': 'SYNC_A',
        '145-68': 'SYNC_B',
        // Volume faders (CC 7 — GM standard channel volume)
        '176-7': 'VOL_A',
        '177-7': 'VOL_B',
        // Crossfader (CC 8)
        '176-8': 'CROSSFADER',
        // EQ High/Mid/Low  (CC 70/71/72)
        '176-70': 'EQ_HIGH_A',
        '176-71': 'EQ_MID_A',
        '176-72': 'EQ_LOW_A',
        '177-70': 'EQ_HIGH_B',
        '177-71': 'EQ_MID_B',
        '177-72': 'EQ_LOW_B',
        // Pitch/Jog (CC 0)
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },

  // ─── Denon DJ MC2000 / MC3000 / MC4000 / SC Live ──────────────────────────
  {
    pattern: /denon|mc[0-9]{4}|sc.?live/i,
    preset: {
      name: 'Denon DJ',
      mappings: {
        '144-0': 'PLAY_A',
        '145-0': 'PLAY_B',
        '144-1': 'CUE_A',
        '145-1': 'CUE_B',
        '144-16': 'SYNC_A',
        '145-16': 'SYNC_B',
        '176-7': 'VOL_A',
        '177-7': 'VOL_B',
        '176-10': 'CROSSFADER',
        '176-22': 'EQ_LOW_A',
        '176-23': 'EQ_MID_A',
        '176-24': 'EQ_HIGH_A',
        '177-22': 'EQ_LOW_B',
        '177-23': 'EQ_MID_B',
        '177-24': 'EQ_HIGH_B',
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },

  // ─── Native Instruments Traktor Kontrol S2 / S3 / S4 ─────────────────────
  {
    pattern: /traktor|kontrol|native instruments/i,
    preset: {
      name: 'Traktor Kontrol',
      mappings: {
        '144-0': 'PLAY_A',
        '145-0': 'PLAY_B',
        '144-1': 'CUE_A',
        '145-1': 'CUE_B',
        '144-8': 'SYNC_A',
        '145-8': 'SYNC_B',
        '176-7': 'VOL_A',
        '177-7': 'VOL_B',
        '176-14': 'CROSSFADER',
        '176-70': 'EQ_LOW_A',
        '176-71': 'EQ_MID_A',
        '176-72': 'EQ_HIGH_A',
        '177-70': 'EQ_LOW_B',
        '177-71': 'EQ_MID_B',
        '177-72': 'EQ_HIGH_B',
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },

  // ─── Reloop Mixon / Terminal Mix ──────────────────────────────────────────
  {
    pattern: /reloop|mixon|terminal.?mix/i,
    preset: {
      name: 'Reloop',
      mappings: {
        '144-0': 'PLAY_A',
        '145-0': 'PLAY_B',
        '144-1': 'CUE_A',
        '145-1': 'CUE_B',
        '144-4': 'SYNC_A',
        '145-4': 'SYNC_B',
        '176-7': 'VOL_A',
        '177-7': 'VOL_B',
        '176-1': 'CROSSFADER',
        '176-70': 'EQ_LOW_A',
        '176-71': 'EQ_MID_A',
        '176-72': 'EQ_HIGH_A',
        '177-70': 'EQ_LOW_B',
        '177-71': 'EQ_MID_B',
        '177-72': 'EQ_HIGH_B',
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },

  // ─── Behringer CMD / BCD series ───────────────────────────────────────────
  {
    pattern: /behringer|cmd|bcd/i,
    preset: {
      name: 'Behringer CMD',
      mappings: {
        '144-0': 'PLAY_A',
        '145-0': 'PLAY_B',
        '144-1': 'CUE_A',
        '145-1': 'CUE_B',
        '144-6': 'SYNC_A',
        '145-6': 'SYNC_B',
        '176-7': 'VOL_A',
        '177-7': 'VOL_B',
        '176-1': 'CROSSFADER',
        '176-70': 'EQ_LOW_A',
        '176-71': 'EQ_MID_A',
        '176-72': 'EQ_HIGH_A',
        '177-70': 'EQ_LOW_B',
        '177-71': 'EQ_MID_B',
        '177-72': 'EQ_HIGH_B',
        '176-0': 'PITCH_A',
        '177-0': 'PITCH_B',
      }
    }
  },
];

/** Returns the first matching preset for a given device name, or null. */
export function findPreset(deviceName: string): MidiPreset | null {
  for (const entry of MIDI_PRESETS) {
    if (entry.pattern.test(deviceName)) return entry.preset;
  }
  return null;
}