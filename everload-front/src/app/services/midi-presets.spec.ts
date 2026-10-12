import { findPreset, MIDI_PRESETS } from './midi-presets';

describe('MIDI presets', () => {
  it('finds supported devices case-insensitively and returns their mappings', () => {
    expect(findPreset('Pioneer DDJ-FLX4')?.name).toBe('Pioneer DDJ');
    expect(findPreset('NUMARK Party Mix')?.mappings['144-0']).toBe('PLAY_A');
    expect(findPreset('Native Instruments Traktor Kontrol S2')?.name).toBe('Traktor Kontrol');
  });

  it('returns null for unknown and empty device names', () => {
    expect(findPreset('Unknown MIDI Device')).toBeNull();
    expect(findPreset('')).toBeNull();
  });

  it('contains unique matching patterns and non-empty preset mappings', () => {
    expect(MIDI_PRESETS.length).toBeGreaterThan(0);
    for (const entry of MIDI_PRESETS) {
      expect(entry.preset.name).toBeTruthy();
      expect(Object.keys(entry.preset.mappings).length).toBeGreaterThan(0);
    }
  });
});
