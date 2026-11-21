import { WinampVisualizerComponent } from './winamp-visualizer.component';

describe('Winamp visualizer lifecycle', () => {
  let component: any;
  let renderer: jasmine.SpyObj<any>;
  beforeEach(() => {
    component = Object.create(WinampVisualizerComponent.prototype);
    renderer = jasmine.createSpyObj('visualizer', ['render', 'loadPreset', 'disconnectAudio', 'setRendererSize']);
    Object.assign(component, { renderer, destroyed: false, lastFrame: 0, reducedMotion: false,
      music: { mainPlayer: { state: { playing: true }, supportsAudioEffects: true } },
      zone: { run: (callback: () => void) => callback() },
      presets: { one: { value: 1 }, two: { value: 2 } }, names: ['one', 'two'], selected: 'one' });
    spyOn(window, 'requestAnimationFrame').and.returnValue(12);
  });

  it('renders at a bounded rate and does not animate paused or inaccessible audio', () => {
    component.draw(34); component.draw(40);
    expect(renderer.render).toHaveBeenCalledTimes(1);
    component.music.mainPlayer.state.playing = false;
    component.draw(100);
    component.music.mainPlayer.state.playing = true;
    component.music.mainPlayer.supportsAudioEffects = false;
    component.draw(150);
    expect(renderer.render).toHaveBeenCalledTimes(1);
  });

  it('keeps hidden tabs idle and honors reduced motion', () => {
    component.reducedMotion = true;
    component.draw(40); expect(renderer.render).not.toHaveBeenCalled();
    component.draw(100); expect(renderer.render).toHaveBeenCalledTimes(1);
    spyOnProperty(document, 'hidden', 'get').and.returnValue(true);
    component.draw(400); expect(renderer.render).toHaveBeenCalledTimes(1);
  });

  it('cycles presets without accepting unknown selection values', () => {
    component.nextPreset(); expect(component.selected).toBe('two');
    expect(renderer.loadPreset).toHaveBeenCalledWith(component.presets.two, 1.2);
    component.selected = 'unknown'; component.changePreset();
    expect(renderer.loadPreset).toHaveBeenCalledTimes(1);
  });

  it('releases its own graph branch and GPU context, never the player context', () => {
    const loseContext = jasmine.createSpy('loseContext');
    const disconnect = jasmine.createSpy('disconnect');
    const cancel = spyOn(window, 'cancelAnimationFrame');
    Object.assign(component, { frame: 12, observer: { disconnect }, source: {},
      canvas: { nativeElement: { getContext: () => ({ getExtension: () => ({ loseContext }) }) } } });
    component.ngOnDestroy();
    expect(cancel).toHaveBeenCalledWith(12);
    expect(renderer.disconnectAudio).toHaveBeenCalledOnceWith(component.source);
    expect(disconnect).toHaveBeenCalled(); expect(loseContext).toHaveBeenCalled();
    component.draw(100); expect(renderer.render).not.toHaveBeenCalled();
  });

  it('caps the render buffer when a large window is resized', () => {
    component.surface = { nativeElement: { getBoundingClientRect: () => ({ width: 2560, height: 1440 }) } };
    component.canvas = { nativeElement: { width: 300, height: 150 } };
    component.resize();
    expect(renderer.setRendererSize).toHaveBeenCalledWith(1280, 720);
  });
});
