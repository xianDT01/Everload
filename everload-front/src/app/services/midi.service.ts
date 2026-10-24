import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { findPreset } from './midi-presets';
import { decodeMidiMessage } from './midi-message';

export interface MidiDevice {
  id: string;
  name: string;
  manufacturer: string;
}

export interface MidiActionPayload {
  actionId: string;
  rawValue: number; // 0-127
  normalizedValue: number; // 0-1
  pressed?: boolean;
}

export interface MidiAutoDetectEvent {
  deviceName: string;
  presetName: string;
}

@Injectable({
  providedIn: 'root'
})
export class MidiService implements OnDestroy {

  public isSupported = false;
  public status$ = new BehaviorSubject<'IDLE' | 'CONNECTING' | 'READY' | 'DENIED' | 'ERROR' | 'UNSUPPORTED'>('IDLE');
  public lastMessage$ = new BehaviorSubject<string>('');
  private connecting: Promise<boolean> | null = null;
  private preferredDeviceId: string | null = null;
  private manuallyDisabled = false;
  private heldNotes = new Set<string>();
  private pendingActions = new Map<string, MidiActionPayload>();
  private actionTimer?: ReturnType<typeof setTimeout>;
  private lastDiagnosticAt = 0;
  private destroyed = false;

  private _midiAccess: any = null;
  private _inputs: any[] = [];

  public devices$ = new BehaviorSubject<MidiDevice[]>([]);
  public activeDeviceId$ = new BehaviorSubject<string | null>(null);

  public action$ = new Subject<MidiActionPayload>();

  public isLearning$ = new BehaviorSubject<boolean>(false);
  public learningActionId: string | null = null;

  /** Fires when a preset is auto-applied for a newly connected device. */
  public autoDetected$ = new Subject<MidiAutoDetectEvent>();

  // {"deviceId": {"status-data1": "actionId"}}
  private mappings: Record<string, Record<string, string>> = {};

  // Track which device IDs have already had preset auto-detection run.
  private autoDetectedIds = new Set<string>();

  constructor(private ngZone: NgZone) {
    this.loadMappings();
    this.isSupported = typeof navigator !== 'undefined' && typeof (navigator as any).requestMIDIAccess === 'function';
    if (!this.isSupported) this.status$.next('UNSUPPORTED');
  }

  public requestAccess(): Promise<boolean> {
    if (this.destroyed || !this.isSupported) return Promise.resolve(false);
    if (this.connecting) return this.connecting;
    this.status$.next('CONNECTING');
    this.connecting = Promise.resolve().then(() => (navigator as any).requestMIDIAccess({ sysex: false }))
      .then(access => {
        if (this.destroyed) return false;
        this.ngZone.run(() => { this.onMidiSuccess(access); this.status$.next('READY'); });
        return true;
      }).catch(error => {
        this.ngZone.run(() => this.status$.next(error?.name === 'NotAllowedError' || error?.name === 'SecurityError' ? 'DENIED' : 'ERROR'));
        return false;
      }).finally(() => this.connecting = null);
    return this.connecting;
  }

  private onMidiSuccess(access: any) {
    if (this._midiAccess) this._midiAccess.onstatechange = null;
    this._midiAccess = access;

    this._midiAccess.onstatechange = (e: any) => {
      this.ngZone.run(() => this.updateDevices());
    };

    this.updateDevices();
  }

  private updateDevices() {
    if (!this._midiAccess) return;

    const inputs: any[] = [];
    const devices: MidiDevice[] = [];

    const iterator = this._midiAccess.inputs.values();
    let result = iterator.next();

    while (!result.done) {
      const input = result.value;
      if (input.state === 'disconnected') { result = iterator.next(); continue; }
      inputs.push(input);
      devices.push({
        id: input.id,
        name: input.name || 'Dispositivo MIDI',
        manufacturer: input.manufacturer || ''
      });
      result = iterator.next();
    }

    for (const oldInput of this._inputs) oldInput.onmidimessage = null;
    this._inputs = inputs;
    this.devices$.next(devices);

    // Auto-detect presets for newly connected devices
    for (const device of devices) {
      this.tryAutoDetect(device);
    }

    const next = this.manuallyDisabled ? null : devices.find(d => d.id === this.preferredDeviceId)
      ?? devices.find(d => d.id === this.activeDeviceId$.value) ?? devices[0];
    this.bindInput(next?.id ?? null);
  }

  /**
   * Checks if a device matches a known preset and applies its mappings when:
   * 1. We have not yet run auto-detection for this device ID, and
   * 2. The preset is verified for that exact model, and
   * 3. The device has no existing user mappings stored.
   */
  private tryAutoDetect(device: MidiDevice) {
    if (this.autoDetectedIds.has(device.id)) return;
    this.autoDetectedIds.add(device.id);

    const label = `${device.manufacturer} ${device.name}`.trim();
    const preset = findPreset(label) ?? findPreset(device.name);
    if (!preset?.verified) return;

    // Only auto-apply when the user has not manually mapped this device
    const existing = this.mappings[device.id];
    if (existing && Object.keys(existing).length > 0) return;

    this.mappings[device.id] = { ...preset.mappings };
    this.persistMappings();
    this.autoDetected$.next({ deviceName: device.name, presetName: preset.name });
  }

  public setActiveDevice(id: string | null) {
    this.preferredDeviceId = id;
    this.manuallyDisabled = id === null;
    this.bindInput(id);
  }

  private bindInput(id: string | null) {
    if (id !== this.activeDeviceId$.value) {
      this.stopLearning();
      this.heldNotes.clear();
      this.pendingActions.clear();
    }
    this.activeDeviceId$.next(id);

    this._inputs.forEach(input => {
      input.onmidimessage = null;
    });

    if (id) {
      const activeInput = this._inputs.find(i => i.id === id);
      if (activeInput) {
        activeInput.onmidimessage = (message: any) => {
          if (this.activeDeviceId$.value !== activeInput.id) return;
          this.ngZone.runOutsideAngular(() => this.onMidiMessage(message));
        };
      }
    }
  }

  private onMidiMessage(message: any) {
    const decoded = decodeMidiMessage(message?.data);
    if (!decoded) return;
    const messageId = decoded.id;
    const diagnosticAt = Date.now();
    if (diagnosticAt - this.lastDiagnosticAt >= 100) {
      this.lastDiagnosticAt = diagnosticAt;
      this.ngZone.run(() => this.lastMessage$.next(`${messageId} : ${decoded.rawValue}`));
    }

    if (this.isLearning$.value && this.learningActionId) {
      if (decoded.pressed === false) return;
      this.ngZone.run(() => { this.saveMapping(messageId, this.learningActionId!); this.stopLearning(); });
      if (decoded.kind === 'note') this.heldNotes.add(messageId);
      return;
    }

    const deviceId = this.activeDeviceId$.value;
    if (!deviceId) return;

    const deviceMappings = this.mappings[deviceId] || {};
    const actionId = deviceMappings[messageId];

    if (decoded.kind === 'note') {
      if (decoded.pressed && this.heldNotes.has(messageId)) return;
      if (decoded.pressed) this.heldNotes.add(messageId); else this.heldNotes.delete(messageId);
    }
    if (actionId) {
      const payload: MidiActionPayload = {
        actionId,
        rawValue: decoded.rawValue,
        normalizedValue: decoded.normalizedValue,
        pressed: decoded.pressed
      };
      if (/^(VOL_|EQ_|PITCH_|FILTER_|ECHO_|CROSSFADER)/.test(actionId)) {
        this.pendingActions.set(actionId, payload);
        if (!this.actionTimer) this.actionTimer = setTimeout(() => {
          this.actionTimer = undefined;
          const pending = [...this.pendingActions.values()];
          this.pendingActions.clear();
          this.ngZone.run(() => pending.forEach(action => this.action$.next(action)));
        }, 16);
      } else this.ngZone.run(() => this.action$.next(payload));
    }
  }

  public startLearning(actionId: string) {
    if (!this.activeDeviceId$.value) return;
    this.learningActionId = actionId;
    this.isLearning$.next(true);
  }

  public stopLearning() {
    this.learningActionId = null;
    this.isLearning$.next(false);
  }

  private saveMapping(messageId: string, actionId: string) {
    const deviceId = this.activeDeviceId$.value;
    if (!deviceId) return;

    if (!this.mappings[deviceId]) {
      this.mappings[deviceId] = {};
    }

    for (const key in this.mappings[deviceId]) {
      if (this.mappings[deviceId][key] === actionId) {
        delete this.mappings[deviceId][key];
      }
    }

    this.mappings[deviceId][messageId] = actionId;
    this.persistMappings();
  }

  public getMappingForAction(actionId: string): string | null {
    const deviceId = this.activeDeviceId$.value;
    if (!deviceId || !this.mappings[deviceId]) return null;

    for (const key in this.mappings[deviceId]) {
      if (this.mappings[deviceId][key] === actionId) {
        return key;
      }
    }
    return null;
  }

  public clearMapping(actionId: string) {
    const deviceId = this.activeDeviceId$.value;
    if (!deviceId || !this.mappings[deviceId]) return;

    for (const key in this.mappings[deviceId]) {
      if (this.mappings[deviceId][key] === actionId) {
        delete this.mappings[deviceId][key];
      }
    }
    this.persistMappings();
  }

  /** Resets mappings for the active device to the detected preset (if any). */
  public resetToPreset() {
    const deviceId = this.activeDeviceId$.value;
    if (!deviceId) return;

    const device = this.devices$.value.find(d => d.id === deviceId);
    if (!device) return;

    const label = `${device.manufacturer} ${device.name}`.trim();
    const preset = findPreset(label) ?? findPreset(device.name);
    if (!preset) return;

    this.mappings[deviceId] = { ...preset.mappings };
    this.persistMappings();
  }

  /** Returns preset name for the currently active device, or null. */
  public getActivePresetName(): string | null {
    const deviceId = this.activeDeviceId$.value;
    if (!deviceId) return null;
    const device = this.devices$.value.find(d => d.id === deviceId);
    if (!device) return null;
    const label = `${device.manufacturer} ${device.name}`.trim();
    const preset = findPreset(label) ?? findPreset(device.name);
    return preset?.name ?? null;
  }

  private persistMappings() {
    try {
      localStorage.setItem('everload_midi_mappings', JSON.stringify(this.mappings));
    } catch (e) {}
  }

  private loadMappings() {
    try {
      const stored = localStorage.getItem('everload_midi_mappings');
      if (stored) {
        const parsed = JSON.parse(stored);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return;
        for (const [deviceId, values] of Object.entries(parsed)) {
          if (!values || typeof values !== 'object' || Array.isArray(values)) continue;
          const clean: Record<string, string> = {};
          for (const [key, action] of Object.entries(values)) {
            if (!/^\d{3}-(\d{1,3}|pitch)$/.test(key) || typeof action !== 'string' || !/^[A-Z][A-Z0-9_]{0,50}$/.test(action)) continue;
            const [statusText, control] = key.split('-');
            const status = Number(statusText);
            const canonical = status >= 224 && status <= 239 ? `${status}-pitch`
              : status >= 128 && status <= 143 ? `${status + 16}-${control}` : key;
            clean[canonical] = action;
          }
          Object.defineProperty(this.mappings, deviceId, { value: clean, writable: true, enumerable: true, configurable: true });
        }
      }
    } catch (e) {}
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    clearTimeout(this.actionTimer);
    this.pendingActions.clear();
    if (this._midiAccess) this._midiAccess.onstatechange = null;
    for (const input of this._inputs) input.onmidimessage = null;
    this.stopLearning();
  }
}
