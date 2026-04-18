import { Injectable, NgZone } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { findPreset } from './midi-presets';

export interface MidiDevice {
  id: string;
  name: string;
  manufacturer: string;
}

export interface MidiActionPayload {
  actionId: string;
  rawValue: number; // 0-127
  normalizedValue: number; // 0-1
}

export interface MidiAutoDetectEvent {
  deviceName: string;
  presetName: string;
}

@Injectable({
  providedIn: 'root'
})
export class MidiService {

  public isSupported = false;

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
    this.initMidi();
  }

  private initMidi() {
    if (typeof navigator !== 'undefined' && (navigator as any).requestMIDIAccess) {
      this.isSupported = true;
      (navigator as any).requestMIDIAccess().then(
        (access: any) => this.onMidiSuccess(access),
        (err: any) => console.error('Error accediendo a Web MIDI API', err)
      );
    }
  }

  private onMidiSuccess(access: any) {
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
      inputs.push(input);
      devices.push({
        id: input.id,
        name: input.name || 'Dispositivo MIDI',
        manufacturer: input.manufacturer || ''
      });
      result = iterator.next();
    }

    this._inputs = inputs;
    this.devices$.next(devices);

    // Auto-detect presets for newly connected devices
    for (const device of devices) {
      this.tryAutoDetect(device);
    }

    if (!this.activeDeviceId$.value && devices.length > 0) {
      this.setActiveDevice(devices[0].id);
    } else if (this.activeDeviceId$.value) {
      const stillConnected = devices.find(d => d.id === this.activeDeviceId$.value);
      if (!stillConnected) {
        this.setActiveDevice(null);
      } else {
        this.setActiveDevice(this.activeDeviceId$.value);
      }
    }
  }

  /**
   * Checks if a device matches a known preset and applies its mappings when:
   * 1. We have not yet run auto-detection for this device ID, and
   * 2. The device has no existing user mappings stored.
   */
  private tryAutoDetect(device: MidiDevice) {
    if (this.autoDetectedIds.has(device.id)) return;
    this.autoDetectedIds.add(device.id);

    const label = `${device.manufacturer} ${device.name}`.trim();
    const preset = findPreset(label) ?? findPreset(device.name);
    if (!preset) return;

    // Only auto-apply when the user has not manually mapped this device
    const existing = this.mappings[device.id];
    if (existing && Object.keys(existing).length > 0) return;

    this.mappings[device.id] = { ...preset.mappings };
    this.persistMappings();
    this.autoDetected$.next({ deviceName: device.name, presetName: preset.name });
  }

  public setActiveDevice(id: string | null) {
    this.activeDeviceId$.next(id);

    this._inputs.forEach(input => {
      input.onmidimessage = null;
    });

    if (id) {
      const activeInput = this._inputs.find(i => i.id === id);
      if (activeInput) {
        activeInput.onmidimessage = (message: any) => {
          this.ngZone.run(() => this.onMidiMessage(message));
        };
      }
    }
  }

  private onMidiMessage(message: any) {
    const data = message.data;
    const status = data[0];
    const data1 = data[1];
    const data2 = data.length > 2 ? data[2] : 0;

    if (status >= 248) return;

    const messageId = `${status}-${data1}`;

    if (this.isLearning$.value && this.learningActionId) {
      this.saveMapping(messageId, this.learningActionId);
      this.isLearning$.next(false);
      this.learningActionId = null;
      return;
    }

    const deviceId = this.activeDeviceId$.value;
    if (!deviceId) return;

    const deviceMappings = this.mappings[deviceId] || {};
    const actionId = deviceMappings[messageId];

    if (actionId) {
      this.action$.next({
        actionId,
        rawValue: data2,
        normalizedValue: data2 / 127
      });
    }
  }

  public startLearning(actionId: string) {
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
        this.mappings = JSON.parse(stored);
      }
    } catch (e) {}
  }
}
