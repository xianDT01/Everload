import { fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { MidiService, MidiActionPayload } from './midi.service';

describe('MidiService controller connection', () => {
  let service: MidiService;
  let request: jasmine.Spy;
  let original: PropertyDescriptor | undefined;
  let input: any;
  let access: any;
  let actions: MidiActionPayload[];

  beforeEach(() => {
    localStorage.removeItem('everload_midi_mappings');
    original = Object.getOwnPropertyDescriptor(navigator, 'requestMIDIAccess');
    input = { id: 'test-controller', name: 'Test Controller', state: 'connected', manufacturer: 'Test', onmidimessage: null };
    access = { inputs: new Map([[input.id, input]]), onstatechange: null };
    request = jasmine.createSpy('requestMIDIAccess').and.callFake(() => Promise.resolve(access));
    Object.defineProperty(navigator, 'requestMIDIAccess', { configurable: true, value: request });
    service = new MidiService(new NgZone({ enableLongStackTrace: false }));
    actions = [];
    service.action$.subscribe(action => actions.push(action));
  });

  afterEach(() => {
    service.ngOnDestroy();
    if (original) Object.defineProperty(navigator, 'requestMIDIAccess', original);
    else delete (navigator as any).requestMIDIAccess;
    localStorage.removeItem('everload_midi_mappings');
  });

  function connect(): void { void service.requestAccess(); flushMicrotasks(); }
  function send(bytes: number[]): void { input.onmidimessage?.({ data: new Uint8Array(bytes) }); }

  it('does not request permission until the user connects', fakeAsync(() => {
    expect(request).not.toHaveBeenCalled();
    connect();
    expect(request).toHaveBeenCalledOnceWith({ sysex: false });
    expect(service.activeDeviceId$.value).toBe(input.id);
    expect(service.status$.value).toBe('READY');
  }));

  it('allows retrying a denied permission', fakeAsync(() => {
    request.and.callFake(() => Promise.reject({ name: 'NotAllowedError' }));
    connect();
    expect(service.status$.value).toBe('DENIED');
    request.and.callFake(() => Promise.resolve(access));
    connect();
    expect(service.status$.value).toBe('READY');
  }));

  it('shares an in-flight permission request', fakeAsync(() => {
    const first = service.requestAccess();
    expect(service.requestAccess()).toBe(first);
    flushMicrotasks();
    expect(request).toHaveBeenCalledTimes(1);
  }));

  it('ignores note release during learning and never plays while learning', fakeAsync(() => {
    connect(); service.startLearning('PLAY_A');
    send([128, 11, 64]);
    expect(service.isLearning$.value).toBeTrue();
    send([144, 11, 127]);
    expect(service.getMappingForAction('PLAY_A')).toBe('144-11');
    expect(actions).toEqual([]);
    send([128, 11, 64]); send([144, 11, 127]); send([144, 11, 127]);
    expect(actions.filter(action => action.pressed).length).toBe(1);
  }));

  it('coalesces faders while preserving their final zero value', fakeAsync(() => {
    connect(); service.startLearning('VOL_A'); send([176, 7, 100]);
    send([176, 7, 64]); send([176, 7, 0]);
    expect(actions.length).toBe(0);
    tick(17);
    expect(actions.length).toBe(1);
    expect(actions[0].normalizedValue).toBe(0);
  }));

  it('learns a stable 14-bit pitch control across all positions', fakeAsync(() => {
    connect(); service.startLearning('PITCH_A'); send([224, 0, 64]);
    expect(service.getMappingForAction('PITCH_A')).toBe('224-pitch');
    send([224, 127, 127]); tick(17);
    expect(actions[0].normalizedValue).toBe(1);
    send([224, 12, 64]); tick(17);
    expect(actions[1].normalizedValue).toBeGreaterThan(0.5);
  }));

  it('disconnects and reconnects inputs without retaining stale handlers', fakeAsync(() => {
    connect();
    input.state = 'disconnected'; access.onstatechange({});
    expect(service.devices$.value).toEqual([]);
    expect(input.onmidimessage).toBeNull();
    expect(service.activeDeviceId$.value).toBeNull();
    input.state = 'connected'; access.onstatechange({});
    expect(service.activeDeviceId$.value).toBe(input.id);
    expect(typeof input.onmidimessage).toBe('function');
  }));

  it('respects an explicitly disabled controller on device changes', fakeAsync(() => {
    connect(); service.setActiveDevice(null); access.onstatechange({});
    expect(service.activeDeviceId$.value).toBeNull();
    expect(input.onmidimessage).toBeNull();
  }));

  it('does not apply manufacturer-wide unverified mappings automatically', fakeAsync(() => {
    input.name = 'Pioneer DDJ-FLX4';
    connect();
    expect(service.getMappingForAction('PLAY_A')).toBeNull();
    service.resetToPreset();
    expect(service.getMappingForAction('PLAY_A')).toBeTruthy();
  }));

  it('clears pending actions and port handlers on destruction', fakeAsync(() => {
    connect(); service.startLearning('VOL_A'); send([176, 7, 100]); send([176, 7, 20]);
    service.ngOnDestroy(); tick(17);
    expect(actions).toEqual([]);
    expect(input.onmidimessage).toBeNull();
    expect(access.onstatechange).toBeNull();
  }));

  it('ignores MIDI clock and does not capture it as a mapping', fakeAsync(() => {
    connect(); service.startLearning('PLAY_A'); send([248]);
    expect(service.isLearning$.value).toBeTrue();
    expect(service.getMappingForAction('PLAY_A')).toBeNull();
  }));
});
