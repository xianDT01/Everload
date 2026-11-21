import { readWinampPresets, writeWinampPreset } from './winamp-presets';

describe('Webamp EQF interoperability', () => {
  it('round-trips ten independent bands and preamp within one quantization step', () => {
    const preset = { name: 'EverLoad test', bands: [-12, -8, -5, -2, 0, 1, 3, 6, 9, 12], preamp: -4 };
    const result = readWinampPresets(writeWinampPreset(preset))[0];
    expect(result.name).toBe(preset.name);
    result.bands.forEach((db, index) => expect(Math.abs(db - preset.bands[index])).toBeLessThan(0.4));
    expect(Math.abs(result.preamp - preset.preamp)).toBeLessThan(0.4);
  });

  it('preserves silence-neutral flat settings exactly', () => {
    const flat = { name: 'Flat', bands: Array(10).fill(0), preamp: 0 };
    expect(readWinampPresets(writeWinampPreset(flat))[0]).toEqual(flat);
  });

  it('rejects oversized, incomplete, empty and incorrectly signed files', () => {
    const valid = writeWinampPreset({ name: 'Flat', bands: Array(10).fill(0), preamp: 0 });
    for (const buffer of [new ArrayBuffer(0), new ArrayBuffer(65537), valid.slice(0, -1), new ArrayBuffer(valid.byteLength)]) {
      expect(() => readWinampPresets(buffer)).toThrow();
    }
  });

  it('rejects invalid slider values and unterminated names before upstream parsing', () => {
    const valid = writeWinampPreset({ name: 'Flat', bands: Array(10).fill(0), preamp: 0 });
    const badGain = valid.slice(0); new Uint8Array(badGain)[badGain.byteLength - 1] = 255;
    expect(() => readWinampPresets(badGain)).toThrow();
    const badName = valid.slice(0); new Uint8Array(badName).fill(65, valid.byteLength - 268, valid.byteLength - 11);
    expect(() => readWinampPresets(badName)).toThrow();
  });

  it('rejects non-finite values when exporting and bounds preset names', () => {
    expect(() => writeWinampPreset({ name: 'Bad', bands: Array(10).fill(NaN), preamp: 0 })).toThrow();
    const result = readWinampPresets(writeWinampPreset({ name: 'a'.repeat(300), bands: Array(10).fill(0), preamp: 0 }));
    expect(result[0].name.length).toBe(80);
  });
});
