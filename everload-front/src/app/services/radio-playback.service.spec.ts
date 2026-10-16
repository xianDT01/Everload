import { fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { RadioPlaybackService, RadioStation } from './radio-playback.service';

class TestRadioAudio extends EventTarget {
  static instances: TestRadioAudio[] = [];
  static nativeHls = '';
  src = '';
  preload = '';
  volume = 1;
  rejectPlay?: (error: unknown) => void;
  play = jasmine.createSpy('play').and.callFake(() => new Promise<void>((_, reject) => this.rejectPlay = reject));
  pause = jasmine.createSpy('pause');
  load = jasmine.createSpy('load');
  canPlayType = () => TestRadioAudio.nativeHls;
  removeAttribute = (name: string) => { if (name === 'src') this.src = ''; };
  constructor() { super(); TestRadioAudio.instances.push(this); }
}

class TestRadioHls {
  static Events = { ERROR: 'error', MANIFEST_PARSED: 'manifest', DESTROYING: 'destroy' };
  static supported = true;
  static instances: TestRadioHls[] = [];
  static isSupported = () => TestRadioHls.supported;
  handlers = new Map<string, (event: string, data?: any) => void>();
  on = (event: string, callback: (event: string, data?: any) => void) => this.handlers.set(event, callback);
  emit = (event: string, data?: any) => this.handlers.get(event)?.(event, data);
  attachMedia = jasmine.createSpy('attach');
  loadSource = jasmine.createSpy('source');
  destroy = jasmine.createSpy('destroy').and.callFake(() => this.emit('destroy'));
  constructor() { TestRadioHls.instances.push(this); }
}

describe('RadioPlaybackService stream resilience', () => {
  let service: RadioPlaybackService;
  let descriptor: PropertyDescriptor | undefined;
  const station = (urls = ['https://radio.example/stream.mp3']): RadioStation => ({
    stationuuid: 'station', name: 'Station', url: urls[0], url_resolved: urls[0],
    fallbackUrls: urls.slice(1), homepage: '', favicon: '', tags: '', country: '',
    countrycode: '', state: '', language: '', codec: 'MP3', bitrate: 128,
    votes: 0, clickcount: 0, lastcheckok: 1
  });
  const audio = () => TestRadioAudio.instances[TestRadioAudio.instances.length - 1];

  beforeEach(() => {
    descriptor = Object.getOwnPropertyDescriptor(window, 'Audio');
    TestRadioAudio.instances = [];
    TestRadioAudio.nativeHls = '';
    Object.defineProperty(window, 'Audio', { configurable: true, value: TestRadioAudio });
    service = new RadioPlaybackService({ instant: (key: string) => key } as any);
    TestRadioHls.supported = true;
    TestRadioHls.instances = [];
    spyOn<any>(service, 'loadHlsConstructor').and.resolveTo(TestRadioHls);
  });

  afterEach(() => {
    service.stop();
    if (descriptor) Object.defineProperty(window, 'Audio', descriptor);
    else Reflect.deleteProperty(window, 'Audio');
  });

  it('deduplicates URLs and uses both the original and resolved streams', fakeAsync(() => {
    service.playStation({ ...station(), url_resolved: 'https://radio.example/other.mp3',
      fallbackUrls: ['https://radio.example/other.mp3', 'https://radio.example/last.mp3'] });
    audio().dispatchEvent(new Event('error'));
    expect(audio().src).toBe('https://radio.example/other.mp3');
    audio().dispatchEvent(new Event('error'));
    expect(audio().src).toBe('https://radio.example/last.mp3');
    audio().dispatchEvent(new Event('error'));
    expect(TestRadioAudio.instances.length).toBe(3);
    expect(service.snapshot.buffering).toBeFalse();
    expect(service.snapshot.error).toBe('RADIO.ERROR_STREAM_FAILED');
  }));

  it('does not skip a fallback when an error event and rejected play arrive together', fakeAsync(() => {
    service.playStation(station(['https://radio.example/a.mp3', 'https://radio.example/b.mp3', 'https://radio.example/c.mp3']));
    const previous = audio();
    previous.dispatchEvent(new Event('error'));
    previous.rejectPlay?.(new Error('Failed'));
    flushMicrotasks();
    expect(audio().src).toBe('https://radio.example/b.mp3');
    expect(TestRadioAudio.instances.length).toBe(2);
    service.stop();
  }));

  it('times out silent connections and stops after exhausting alternatives', fakeAsync(() => {
    service.playStation(station(['https://radio.example/a.mp3', 'https://radio.example/b.mp3']));
    tick(12000);
    expect(audio().src).toBe('https://radio.example/b.mp3');
    tick(12000);
    expect(service.snapshot.error).toBe('RADIO.ERROR_STREAM_FAILED');
    tick(24000);
    expect(TestRadioAudio.instances.length).toBe(2);
  }));

  it('cancels the startup timeout when audio plays and times out a later stall', fakeAsync(() => {
    service.playStation(station());
    audio().dispatchEvent(new Event('playing'));
    tick(13000);
    expect(service.snapshot.playing).toBeTrue();
    audio().dispatchEvent(new Event('waiting'));
    tick(6000);
    audio().dispatchEvent(new Event('waiting'));
    tick(6000);
    expect(service.snapshot.error).toBe('RADIO.ERROR_STREAM_FAILED');
  }));

  it('ignores late events and promise rejections after switching stations', fakeAsync(() => {
    service.playStation(station());
    const previous = audio();
    service.playDirect('https://new.example/live.mp3');
    previous.dispatchEvent(new Event('error'));
    previous.dispatchEvent(new Event('playing'));
    previous.rejectPlay?.(new Error('Aborted'));
    flushMicrotasks();
    expect(audio().src).toBe('https://new.example/live.mp3');
    expect(service.snapshot.playing).toBeFalse();
    expect(service.snapshot.error).toBe('');
    expect(TestRadioAudio.instances.length).toBe(2);
    service.stop();
  }));

  it('pause and stop cancel buffering and prevent background fallback attempts', fakeAsync(() => {
    service.playStation(station());
    const previous = audio();
    service.pause();
    previous.dispatchEvent(new Event('error'));
    tick(24000);
    expect(TestRadioAudio.instances.length).toBe(1);
    expect(service.snapshot.buffering).toBeFalse();
    expect(previous.src).toBe('');
    service.stop();
    expect(service.snapshot.error).toBe('');
  }));

  it('reports autoplay permission separately without trying other servers', fakeAsync(() => {
    service.playStation(station(['https://radio.example/a.mp3', 'https://radio.example/b.mp3']));
    audio().rejectPlay?.(new DOMException('Blocked', 'NotAllowedError'));
    flushMicrotasks();
    tick(24000);
    expect(TestRadioAudio.instances.length).toBe(1);
    expect(service.snapshot.error).toBe('RADIO.ERROR_PLAY_PERMISSION');
  }));

  it('uses native HLS playback when available', fakeAsync(() => {
    TestRadioAudio.nativeHls = 'maybe';
    TestRadioHls.supported = false;
    service.playDirect('https://radio.example/live.m3u8?station=1');
    flushMicrotasks();
    expect(audio().src).toBe('https://radio.example/live.m3u8?station=1');
    expect(audio().play).toHaveBeenCalled();
    service.stop();
  }));

  it('prefers hls.js over advertised native support and cleans it up when paused', fakeAsync(() => {
    TestRadioAudio.nativeHls = 'maybe';
    service.playDirect('https://radio.example/live.m3u8');
    flushMicrotasks();
    const hls = TestRadioHls.instances[0];
    expect(hls.loadSource).toHaveBeenCalledWith('https://radio.example/live.m3u8');
    expect(hls.attachMedia).toHaveBeenCalledWith(audio());
    expect(audio().play).not.toHaveBeenCalled();
    hls.emit('manifest');
    expect(audio().play).toHaveBeenCalled();
    service.pause();
    flushMicrotasks();
    expect(hls.destroy).toHaveBeenCalledTimes(1);
  }));

  it('falls back to MP3 after a fatal HLS error and ignores old HLS events', fakeAsync(() => {
    service.playStation({ ...station(['https://radio.example/live.m3u8', 'https://radio.example/live.mp3']), hls: 1 });
    flushMicrotasks();
    const hls = TestRadioHls.instances[0];
    hls.emit('error', { fatal: false });
    expect(TestRadioAudio.instances.length).toBe(1);
    hls.emit('error', { fatal: true });
    flushMicrotasks();
    expect(audio().src).toBe('https://radio.example/live.mp3');
    expect(hls.destroy).toHaveBeenCalled();
    hls.emit('error', { fatal: true });
    hls.emit('manifest');
    expect(TestRadioAudio.instances.length).toBe(2);
    service.stop();
    flushMicrotasks();
  }));

  it('rejects malformed URLs and unsafe protocols without starting audio', () => {
    service.playStation(station(['javascript:alert(1)', 'not-a-url', 'file:///tmp/radio']));
    expect(TestRadioAudio.instances.length).toBe(0);
    expect(service.snapshot.error).toBe('RADIO.ERROR_STREAM_FAILED');
  });

  it('clamps volume and preserves it across stations', () => {
    service.setVolume(4);
    service.playStation(station());
    expect(audio().volume).toBe(1);
    service.setVolume(-1);
    expect(audio().volume).toBe(0);
    service.setVolume(NaN);
    expect(service.snapshot.volume).toBe(0);
  });
});
