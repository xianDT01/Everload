import { AfterViewInit, Component, ElementRef, EventEmitter, NgZone, OnDestroy, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { MusicService } from '../../services/music.service';
import type { Visualizer } from 'butterchurn';

@Component({
  selector: 'app-winamp-visualizer', standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, MatIconModule, TranslateModule],
  templateUrl: './winamp-visualizer.component.html', styleUrls: ['./winamp-visualizer.component.css']
})
export class WinampVisualizerComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas', { static: true }) canvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('surface', { static: true }) surface!: ElementRef<HTMLElement>;
  @ViewChild('window', { static: true }) window!: ElementRef<HTMLElement>;
  @Output() closed = new EventEmitter<void>();
  names: string[] = [];
  groups = [
    { label: 'WINAMP.VIS_FLUID', names: ['Flexi, martin + geiss - dedicated to the sherwin maxawow', 'Geiss - Reaction Diffusion 2', 'martin - extreme heat', 'Flexi - alien fish pond'] },
    { label: 'WINAMP.VIS_GEOMETRY', names: ['Eo.S. + Phat - cubetrace - v2', 'martin - reflections on black tiles', 'Idiot - Star Of Annon'] },
    { label: 'WINAMP.VIS_FRACTALS', names: ['martin - mandelbox explorer - high speed demo version'] },
    { label: 'WINAMP.VIS_TUNNELS', names: ['Unchained & Rovastar - Wormhole Pillars (Hall of Shadows mix)', 'yin - 191 - Temporal singularities'] },
    { label: 'WINAMP.VIS_SPIRALS', names: ['Krash + Illusion - Spiral Movement'] },
    { label: 'WINAMP.VIS_NEON', names: ['Martin - acid wiring'] }
  ];
  selected = '';
  loading = true;
  failed = false;
  private renderer?: Visualizer;
  private source?: AudioNode;
  private presets: Record<string, unknown> = {};
  private observer?: ResizeObserver;
  private frame = 0;
  private lastFrame = 0;
  private destroyed = false;
  private reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor(public music: MusicService, private zone: NgZone) {}

  async ngAfterViewInit(): Promise<void> {
    try {
      const [engine, pack] = await Promise.all([import('butterchurn'), import('butterchurn-presets/lib/butterchurnPresetsMinimal.min')]);
      if (this.destroyed) return;
      const audio = this.music.mainPlayer.visualizationAudio;
      if (!audio) { this.loading = false; return; }
      this.presets = pack.default.getPresets();
      this.groups = this.groups.map(group => ({ ...group, names: group.names.filter(name => this.presets[name]) })).filter(group => group.names.length);
      this.names = this.groups.flatMap(group => group.names);
      this.selected = this.names[0];
      this.canvas.nativeElement.width = 800;
      this.canvas.nativeElement.height = 500;
      this.renderer = engine.default.createVisualizer(audio.context, this.canvas.nativeElement, { width: 800, height: 500, pixelRatio: 1, textureRatio: 1, meshWidth: 40, meshHeight: 30 });
      this.source = audio.source;
      this.renderer.connectAudio(this.source);
      this.renderer.loadPreset(this.presets[this.selected], 0);
      this.observer = new ResizeObserver(() => this.resize());
      this.observer.observe(this.surface.nativeElement);
      this.resize();
      this.loading = false;
      this.zone.runOutsideAngular(() => this.frame = requestAnimationFrame(time => this.draw(time)));
    } catch { if (!this.destroyed) { this.failed = true; this.loading = false; } }
  }

  changePreset(): void {
    if (this.presets[this.selected]) this.renderer?.loadPreset(this.presets[this.selected], this.reducedMotion ? 0 : 1.2);
  }

  nextPreset(): void {
    this.selected = this.names[(this.names.indexOf(this.selected) + 1) % this.names.length];
    this.changePreset();
  }

  async fullscreen(): Promise<void> {
    try {
      if (document.fullscreenElement === this.window.nativeElement) await document.exitFullscreen();
      else await this.window.nativeElement.requestFullscreen();
    } catch { /* Fullscreen may be blocked by an embedded browser. The window stays usable. */ }
  }

  private resize(): void {
    const bounds = this.surface.nativeElement.getBoundingClientRect();
    const scale = Math.min(1, 1280 / Math.max(1, bounds.width), 720 / Math.max(1, bounds.height));
    const width = Math.max(2, Math.round(bounds.width * scale));
    const height = Math.max(2, Math.round(bounds.height * scale));
    if (this.canvas.nativeElement.width === width && this.canvas.nativeElement.height === height) return;
    this.canvas.nativeElement.width = width;
    this.canvas.nativeElement.height = height;
    this.renderer?.setRendererSize(width, height);
  }

  private draw(time: number): void {
    if (this.destroyed) return;
    if (!document.hidden && this.music.mainPlayer.state.playing && this.music.mainPlayer.supportsAudioEffects && time - this.lastFrame >= (this.reducedMotion ? 100 : 33)) {
      try { this.renderer?.render(); this.lastFrame = time; }
      catch { this.zone.run(() => this.failed = true); return; }
    }
    this.frame = requestAnimationFrame(next => this.draw(next));
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    cancelAnimationFrame(this.frame);
    this.observer?.disconnect();
    if (this.source) this.renderer?.disconnectAudio(this.source);
    this.canvas.nativeElement.getContext('webgl2')?.getExtension('WEBGL_lose_context')?.loseContext();
  }
}
