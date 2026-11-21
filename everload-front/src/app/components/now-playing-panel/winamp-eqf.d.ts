declare module 'winamp-eqf' {
  export function parser(buffer: ArrayBuffer): { presets: Array<Record<string, any>> };
  export function creator(data: { presets: Array<Record<string, any>> }): ArrayBuffer;
}
