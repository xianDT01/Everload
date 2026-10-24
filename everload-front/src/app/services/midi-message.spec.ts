import { decodeMidiMessage } from './midi-message';

describe('MIDI message decoding', () => {
  it('normalizes note off to the matching note on with a zero value', () => {
    expect(decodeMidiMessage([128, 11, 64])).toEqual({ id: '144-11', rawValue: 0, normalizedValue: 0, kind: 'note', pressed: false });
  });
  it('handles zero velocity note on as a release', () => {
    expect(decodeMidiMessage([145, 11, 0])?.pressed).toBeFalse();
  });
  it('retains MIDI channel identity', () => {
    expect(decodeMidiMessage([145, 11, 127])?.id).toBe('145-11');
  });
  it('decodes zero CC values instead of treating them as note releases', () => {
    expect(decodeMidiMessage([176, 7, 0])).toEqual({ id: '176-7', rawValue: 0, normalizedValue: 0, kind: 'cc' });
  });
  it('uses both pitch bend bytes and a stable control identity', () => {
    expect(decodeMidiMessage([224, 0, 64])?.normalizedValue).toBe(0.5);
    expect(decodeMidiMessage([224, 127, 127])?.normalizedValue).toBe(1);
    expect(decodeMidiMessage([224, 0, 0])?.normalizedValue).toBe(0);
    expect(decodeMidiMessage([224, 17, 64])?.id).toBe('224-pitch');
  });
  it('ignores clock, sysex, pressure, channel mode and invalid bytes', () => {
    for (const data of [[248], [240, 1, 2], [208, 12], [176, 123, 0], [144, 200, 1], [144, 1, -1], [144, 1], [144.5, 1, 0]]) {
      expect(decodeMidiMessage(data)).toBeNull();
    }
    expect(decodeMidiMessage(undefined)).toBeNull();
  });
});
