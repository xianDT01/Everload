import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MusicService, PlayerState } from './music.service';
import { AuthService } from './auth.service';
import { ApiBaseService } from './api-base.service';

class TestNotification {
  static permission: NotificationPermission = 'granted';
  static requestPermission: jasmine.Spy;
  static instances: TestNotification[] = [];
  static fail = false;
  onclick: ((event: Event) => void) | null = null;
  onclose: (() => void) | null = null;
  close = jasmine.createSpy('close').and.callFake(() => this.onclose?.());
  constructor(public title: string, public options: NotificationOptions) {
    if (TestNotification.fail) throw new TypeError('Unsupported notification constructor');
    TestNotification.instances.push(this);
  }
}

class TestImage {
  static instances: TestImage[] = [];
  onerror: (() => void) | null = null;
  src = '';
  constructor() { TestImage.instances.push(this); }
}

describe('MusicService playback notifications and media session', () => {
  let service: MusicService;
  let session: any;
  let actions: Map<MediaSessionAction, MediaSessionActionHandler>;
  let notificationDescriptor: PropertyDescriptor | undefined;
  let imageDescriptor: PropertyDescriptor | undefined;
  let sessionDescriptor: PropertyDescriptor | undefined;
  const song = (path = 'song.mp3', title = 'Song') => ({ path, title, name: path,
    artist: 'Artist', album: 'Album', source: 'nas', hasCover: false } as any);
  const patch = (state: Partial<PlayerState>) => (service.mainPlayer as any).patch(state);

  beforeEach(() => {
    localStorage.clear();
    notificationDescriptor = Object.getOwnPropertyDescriptor(window, 'Notification');
    imageDescriptor = Object.getOwnPropertyDescriptor(window, 'Image');
    sessionDescriptor = Object.getOwnPropertyDescriptor(navigator, 'mediaSession');
    TestNotification.permission = 'granted';
    TestNotification.instances = [];
    TestNotification.fail = false;
    TestNotification.requestPermission = jasmine.createSpy().and.resolveTo('granted');
    TestImage.instances = [];
    Object.defineProperty(window, 'Notification', { configurable: true, value: TestNotification });
    Object.defineProperty(window, 'Image', { configurable: true, value: TestImage });
    actions = new Map();
    session = { metadata: null, playbackState: 'none', setPositionState: jasmine.createSpy('position'),
      setActionHandler: jasmine.createSpy('action').and.callFake((action, handler) => actions.set(action, handler)) };
    Object.defineProperty(navigator, 'mediaSession', { configurable: true, value: session });
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule], providers: [MusicService,
      { provide: AuthService, useValue: { getToken: () => 'test-token', getCurrentUser: () => null } },
      { provide: ApiBaseService, useValue: { backendUrl: 'http://test-backend' } }] });
    service = TestBed.inject(MusicService);
  });

  afterEach(() => {
    patch({ currentTrack: null, playing: false });
    TestBed.inject(HttpTestingController).verify();
    for (const [target, name, descriptor] of [[window, 'Notification', notificationDescriptor],
      [window, 'Image', imageDescriptor], [navigator, 'mediaSession', sessionDescriptor]] as const) {
      if (descriptor) Object.defineProperty(target, name, descriptor);
      else Reflect.deleteProperty(target, name);
    }
    localStorage.clear();
  });

  it('uses the app logo as icon, with a separate cover and branded artist/album text', fakeAsync(() => {
    service.coverOverrideMap.set('song.mp3', 'https://images.example.test/cover.jpg');
    patch({ currentTrack: song(), pathId: 1, playing: true });
    const notification = TestNotification.instances[0];
    expect(notification.title).toBe('Song');
    expect(notification.options.icon).toBe(new URL('icons/icon-192x192.png', document.baseURI).href);
    expect(notification.options.badge).toBe(new URL('icons/icon-96x96.png', document.baseURI).href);
    expect((notification.options as any).image).toBe('https://images.example.test/cover.jpg');
    expect(notification.options.body).toBe('Everload\nArtist \u00b7 Album');
    expect(notification.options.silent).toBeTrue();
    tick(7000);
    expect(notification.close).toHaveBeenCalled();
  }));

  it('uses logo artwork when no cover exists and does not rebuild metadata on every time update', fakeAsync(() => {
    patch({ currentTrack: song(), pathId: 1, playing: true });
    const metadata = session.metadata;
    expect(metadata.artwork[0].src).toContain('/icons/icon-512x512.png');
    expect((TestNotification.instances[0].options as any).image).toBeUndefined();
    patch({ currentTime: 10, volume: 0.5 });
    expect(session.metadata).toBe(metadata);
    expect(TestNotification.instances.length).toBe(1);
    tick(7000);
  }));

  it('distinguishes identical paths from different NAS libraries', fakeAsync(() => {
    patch({ currentTrack: song(), pathId: 1, playing: true });
    patch({ currentTrack: song(), pathId: 2, playing: true });
    expect(TestNotification.instances.length).toBe(2);
    expect(TestNotification.instances[0].close).toHaveBeenCalled();
    tick(7000);
  }));

  it('focuses the app and requests the player view when the notification is clicked', fakeAsync(() => {
    spyOn(window, 'focus');
    const requested = jasmine.createSpy('requested');
    service.playerOpenRequested$.subscribe(requested);
    patch({ currentTrack: song(), pathId: 1, playing: true });
    const notification = TestNotification.instances[0];
    const event = new Event('click', { cancelable: true });
    notification.onclick!(event);
    expect(event.defaultPrevented).toBeTrue();
    expect(window.focus).toHaveBeenCalled();
    expect(requested).toHaveBeenCalledTimes(1);
    expect(notification.close).toHaveBeenCalled();
  }));

  it('never interrupts playback when notifications are denied or unsupported', fakeAsync(() => {
    TestNotification.permission = 'denied';
    patch({ currentTrack: song(), pathId: 1, playing: true });
    expect(TestNotification.instances.length).toBe(0);
    TestNotification.permission = 'granted';
    TestNotification.fail = true;
    patch({ currentTrack: song('other.mp3'), pathId: 1, playing: true });
    tick();
    expect(service.mainPlayer.state.playing).toBeTrue();
    expect(TestNotification.instances.length).toBe(0);
  }));

  it('does not show an old song if notification permission arrives after the track changes', fakeAsync(() => {
    TestNotification.permission = 'default';
    let resolve!: (permission: NotificationPermission) => void;
    TestNotification.requestPermission.and.returnValue(new Promise<NotificationPermission>(done => resolve = done));
    patch({ currentTrack: song('first.mp3', 'First'), pathId: 1, playing: true });
    patch({ currentTrack: song('second.mp3', 'Second'), pathId: 1, playing: true });
    TestNotification.permission = 'granted';
    resolve('granted');
    flushMicrotasks();
    expect(TestNotification.instances.map(notification => notification.title)).toEqual(['Second']);
    tick(7000);
  }));

  it('falls back from failed covers without applying a previous song artwork callback', () => {
    service.coverOverrideMap.set('first.mp3', 'https://images.example.test/first.jpg');
    service.coverOverrideMap.set('second.mp3', 'https://images.example.test/second.jpg');
    patch({ currentTrack: song('first.mp3'), pathId: 1 });
    const oldError = TestImage.instances[0].onerror!;
    patch({ currentTrack: song('second.mp3'), pathId: 1 });
    oldError();
    expect(session.metadata.artwork[0].src).toBe('https://images.example.test/second.jpg');
    TestImage.instances[1].onerror!();
    expect(session.metadata.artwork[0].src).toContain('/icons/icon-512x512.png');
    patch({ currentTrack: null });
    expect(session.metadata).toBeNull();
    expect(session.playbackState).toBe('none');
  });

  it('refreshes fetched covers and edited metadata while preserving artwork', () => {
    const track = song();
    patch({ currentTrack: track, pathId: 1 });
    service.coverOverrideMap.set('song.mp3', 'https://images.example.test/new.jpg');
    service.coverReady$.next('song.mp3');
    expect(session.metadata.artwork[0].src).toBe('https://images.example.test/new.jpg');
    track.title = 'Edited title';
    service.refreshMediaSessionMetadata();
    expect(session.metadata.title).toBe('Edited title');
    expect(session.metadata.artwork[0].src).toBe('https://images.example.test/new.jpg');
  });

  it('reports progress and clamps skip controls to the track timeline', () => {
    patch({ currentTrack: song(), pathId: 1, duration: 200, currentTime: 50 });
    expect(session.setPositionState).toHaveBeenCalledWith({ duration: 200, position: 50, playbackRate: 1 });
    const seek = spyOn(service.mainPlayer, 'seek');
    actions.get('seekbackward')!({ action: 'seekbackward', seekOffset: 80 });
    actions.get('seekforward')!({ action: 'seekforward', seekOffset: 1000 });
    expect(seek.calls.allArgs()).toEqual([[0], [200]]);
    actions.get('seekto')!({ action: 'seekto', seekTime: NaN });
    expect(seek).toHaveBeenCalledTimes(2);
  });

  it('keeps supported media controls when one browser action is unavailable', () => {
    session.setActionHandler.and.callFake((action: MediaSessionAction, handler: MediaSessionActionHandler) => {
      if (action === 'seekforward') throw new DOMException('Not supported', 'NotSupportedError');
      actions.set(action, handler);
    });
    expect(() => (service as any).setupMediaSession()).not.toThrow();
    expect(actions.has('pause')).toBeTrue();
    expect(actions.has('nexttrack')).toBeTrue();
  });
});
