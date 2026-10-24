export type DjDeck = 'A' | 'B';

export interface DeckPerformance {
  identity: string;
  taps: number[];
  bpm: number;
  anchor: number;
  quantize: boolean;
  loopBeats: number;
}

export function newDeckPerformance(identity = ''): DeckPerformance {
  return { identity, taps: [], bpm: 0, anchor: 0, quantize: false, loopBeats: 4 };
}

export function tapTempo(taps: number[], now: number): { taps: number[]; bpm: number } {
  const last = taps[taps.length - 1];
  if (!Number.isFinite(now)) return { taps, bpm: 0 };
  if (last !== undefined && now - last < 180) return { taps, bpm: 0 };
  const next = last === undefined || now - last > 2000 ? [now] : [...taps, now].slice(-9);
  if (next.length < 3) return { taps: next, bpm: 0 };
  const intervals = next.slice(1).map((value, i) => value - next[i]).sort((a, b) => a - b);
  const median = intervals[Math.floor(intervals.length / 2)];
  return { taps: next, bpm: Math.round(60000 / median * 10) / 10 };
}

export function snapToBeat(time: number, bpm: number, anchor: number): number {
  if (!(bpm > 0) || !Number.isFinite(time)) return time;
  const beat = 60 / bpm;
  return Math.max(0, anchor + Math.round((time - anchor) / beat) * beat);
}

export function loopRange(start: number, beats: number, bpm: number, duration: number): [number, number] | null {
  if (![start, beats, bpm, duration].every(Number.isFinite) || bpm <= 0 || beats <= 0 || duration <= 0) return null;
  const length = beats * 60 / bpm;
  const from = Math.max(0, Math.min(start, duration - length));
  const end = Math.min(duration, from + length);
  return end > from ? [from, end] : null;
}
