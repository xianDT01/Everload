export interface DecodedMidiMessage {
  id: string;
  rawValue: number;
  normalizedValue: number;
  kind: 'note' | 'cc' | 'pitch';
  pressed?: boolean;
}

export function decodeMidiMessage(data: ArrayLike<number> | undefined): DecodedMidiMessage | null {
  if (!data || data.length !== 3) return null;
  const [status, first, second] = [data[0], data[1], data[2]];
  if (![status, first, second].every(Number.isInteger) || first < 0 || first > 127 || second < 0 || second > 127) return null;
  const type = status & 0xf0;
  const channel = status & 0x0f;
  if (status < 128 || status >= 240) return null;
  if (type === 0x80 || type === 0x90) {
    const pressed = type === 0x90 && second > 0;
    return { id: `${0x90 | channel}-${first}`, rawValue: pressed ? second : 0,
      normalizedValue: pressed ? second / 127 : 0, kind: 'note', pressed };
  }
  if (type === 0xb0 && first < 120) {
    return { id: `${status}-${first}`, rawValue: second, normalizedValue: second / 127, kind: 'cc' };
  }
  if (type === 0xe0) {
    const value = first | second << 7;
    const normalizedValue = value <= 8192 ? value / 16384 : 0.5 + (value - 8192) / 16382;
    return { id: `${status}-pitch`, rawValue: Math.round(normalizedValue * 127), normalizedValue, kind: 'pitch' };
  }
  return null;
}
