import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { MusicService } from '../../../services/music.service';

@Component({
  selector: 'app-playlist-artwork',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="mosaic" [attr.data-count]="covers.length">
    <span class="initials" *ngIf="!covers.length">{{ initials }}</span>
    <span class="tile" *ngFor="let cover of covers"><span>{{ initials }}</span><img [src]="cover" alt="" loading="lazy" (error)="hide($event)" (load)="show($event)"></span>
  </span>`,
  styles: [`
    :host { display:block; width:100%; height:100%; overflow:hidden; border-radius:inherit; }
    .mosaic { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); width:100%; height:100%; background:var(--sp-hover,#282828); }
    .mosaic[data-count="1"], .mosaic[data-count="0"] { grid-template-columns:1fr; }
    .tile { position:relative; min-height:0; overflow:hidden; display:grid; place-items:center; }
    .tile img { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; }
    .tile img[hidden] { display:none; }
    .mosaic[data-count="3"] .tile:last-child { grid-column:span 2; }
    .initials { display:grid; place-items:center; font-size:2em; font-weight:700; }
    .tile span,.initials { color:var(--sp-text,#fff); opacity:.8; }
  `]
})
export class PlaylistArtworkComponent implements OnChanges {
  @Input() playlist: any;
  @Input() pathId = 0;
  @Input() coverRevision = 0;
  covers: string[] = [];
  initials = '';
  constructor(private music: MusicService) {}
  ngOnChanges(): void {
    this.initials = (this.playlist?.name || '').trim().split(/\s+/).slice(0, 2).map((word: string) => word[0]).join('').toUpperCase();
    const covers = new Set<string>();
    for (const track of this.playlist?.tracks || []) {
      if (track.available === false || !(track.trackPath || track.path)) continue;
      covers.add(this.music.getCoverUrlWithCache(track.nasPathId ?? this.pathId, track.trackPath || track.path, track.source || 'nas'));
      if (covers.size === 4) break;
    }
    this.covers = [...covers];
  }
  hide(event: Event): void { (event.target as HTMLImageElement).hidden = true; }
  show(event: Event): void { (event.target as HTMLImageElement).hidden = false; }
}
