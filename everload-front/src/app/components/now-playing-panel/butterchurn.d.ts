declare module 'butterchurn' {
  export interface Visualizer {
    connectAudio(source: AudioNode): void;
    disconnectAudio(source: AudioNode): void;
    loadPreset(preset: unknown, blendSeconds: number): void;
    setRendererSize(width: number, height: number): void;
    render(): void;
  }
  const butterchurn: { createVisualizer(context: AudioContext, canvas: HTMLCanvasElement, options: Record<string, number>): Visualizer };
  export default butterchurn;
}
declare module 'butterchurn-presets/lib/butterchurnPresetsMinimal.min' {
  const presets: { getPresets(): Record<string, unknown> };
  export default presets;
}
