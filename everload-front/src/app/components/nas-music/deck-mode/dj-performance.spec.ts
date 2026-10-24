import { loopRange, newDeckPerformance, snapToBeat, tapTempo } from './dj-performance';

describe('DJ performance timing', () => {
  it('calculates 120 BPM after three taps', () => {
    expect(tapTempo([1000, 1500], 2000).bpm).toBe(120);
  });
  it('ignores double taps without corrupting the previous tempo', () => {
    expect(tapTempo([1000, 1500], 1510)).toEqual({ taps: [1000, 1500], bpm: 0 });
  });
  it('starts a new sequence after a pause', () => {
    expect(tapTempo([1000, 1500], 5000)).toEqual({ taps: [5000], bpm: 0 });
  });
  it('bounds the tap history', () => {
    expect(tapTempo(Array.from({ length: 9 }, (_, i) => i * 500), 4500).taps.length).toBe(9);
  });
  it('snaps to a beat grid with a non-zero origin', () => {
    expect(snapToBeat(3.61, 120, 0.2)).toBeCloseTo(3.7);
  });
  it('does not quantize when tempo is unknown', () => {
    expect(snapToBeat(3.61, 0, 0)).toBe(3.61);
  });
  it('never snaps before track start', () => {
    expect(snapToBeat(-1, 120, 0.2)).toBe(0);
  });
  it('computes loop length in source seconds', () => {
    expect(loopRange(10, 8, 120, 200)).toEqual([10, 14]);
  });
  it('keeps loops within the end of the track', () => {
    expect(loopRange(199, 8, 120, 200)).toEqual([196, 200]);
  });
  it('refuses loops without tempo or duration', () => {
    expect(loopRange(10, 8, 0, 200)).toBeNull();
    expect(loopRange(10, 8, 120, 0)).toBeNull();
    expect(loopRange(NaN, 8, 120, 200)).toBeNull();
  });
  it('does not share mutable tap history between decks', () => {
    const a = newDeckPerformance('a');
    const b = newDeckPerformance('b');
    a.taps.push(500);
    expect(b.taps).toEqual([]);
  });
});
