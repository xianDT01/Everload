import { creator, parser } from 'winamp-eqf';

export interface WinampPreset { name: string; bands: number[]; preamp: number; }
const keys = ['hz60', 'hz170', 'hz310', 'hz600', 'hz1000', 'hz3000', 'hz6000', 'hz12000', 'hz14000', 'hz16000'];
const headerSize = 'Winamp EQ library file v1.1'.length + 4;
const recordSize = 257 + 11;

export function readWinampPresets(buffer: ArrayBuffer): WinampPreset[] {
  if (buffer.byteLength < headerSize + recordSize || buffer.byteLength > 65536 ||
      (buffer.byteLength - headerSize) % recordSize !== 0) throw new Error('Invalid EQF size');
  const bytes = new Uint8Array(buffer);
  // Validate bounded records before handing untrusted files to the upstream parser.
  for (let start = headerSize; start < bytes.length; start += recordSize) {
    if (!bytes.slice(start, start + 257).includes(0) ||
        bytes.slice(start + 257, start + recordSize).some(value => value > 63)) throw new Error('Invalid EQF record');
  }
  const toDb = (value: number) => {
    const raw = 64 - value;
    return (32 - raw) * 12 / (raw <= 32 ? 32 : 31);
  };
  return parser(buffer).presets.map(preset => ({
    name: String(preset['name']).slice(0, 80), bands: keys.map(key => toDb(preset[key])), preamp: toDb(preset['preamp'])
  }));
}

export function writeWinampPreset(preset: WinampPreset): ArrayBuffer {
  if (preset.bands.length !== 10 || ![...preset.bands, preset.preamp].every(Number.isFinite)) throw new Error('Invalid EQ');
  const fromDb = (value: number) => {
    const db = Math.max(-12, Math.min(12, value));
    return 64 - Math.round(32 - db * (db >= 0 ? 32 : 31) / 12);
  };
  return creator({ presets: [{ name: preset.name.replace(/[^\x20-\x7e]/g, '').slice(0, 80) || 'EverLoad',
    ...Object.fromEntries(keys.map((key, i) => [key, fromDb(preset.bands[i])])), preamp: fromDb(preset.preamp) }] });
}
