import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MusicMetadataDto, MusicService } from '../../../services/music.service';

export interface LibraryHomeAlbum {
  album: string;
  artist: string;
  track: MusicMetadataDto;
  pathId: number;
  tracks?: MusicMetadataDto[];
}

export interface LibraryHomeArtist {
  artist: string;
  pathId: number;
  tracks: MusicMetadataDto[];
  imageUrl?: string;
  autoImageUrl?: string;
}

type HomeFilter = 'all' | 'albums' | 'artists' | 'playlists';

@Component({
  selector: 'app-library-home',
  templateUrl: './library-home.component.html',
  styleUrls: ['./library-home.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LibraryHomeComponent {
  @Input() albums: LibraryHomeAlbum[] = [];
  @Input() continueAlbums: LibraryHomeAlbum[] = [];
  @Input() artists: LibraryHomeArtist[] = [];
  @Input() recent: MusicMetadataDto[] = [];
  @Input() playlists: any[] = [];
  @Input() loading = false;
  @Input() failed = false;
  @Input() playingPath = '';
  @Input() coverRevision = 0;
  @Input() pathId = 0;
  @Output() albumPlay = new EventEmitter<LibraryHomeAlbum>();
  @Output() artistPlay = new EventEmitter<LibraryHomeArtist>();
  @Output() trackPlay = new EventEmitter<MusicMetadataDto>();
  @Output() playlistOpen = new EventEmitter<any>();
  @Output() foldersOpen = new EventEmitter<void>();
  @Output() retry = new EventEmitter<void>();

  filter: HomeFilter = 'all';
  limit = 48;
  readonly filters: HomeFilter[] = ['all', 'albums', 'artists', 'playlists'];
  constructor(private music: MusicService) {}

  selectFilter(filter: HomeFilter): void { this.filter = filter; this.limit = 48; }
  cover(track: MusicMetadataDto, pathId = track.nasPathId ?? 0): string {
    return this.music.getCoverUrlWithCache(pathId, track.path, track.source);
  }
  imageFailed(event: Event): void { (event.target as HTMLImageElement).hidden = true; }
  duration(seconds: number): string {
    if (!Number.isFinite(seconds) || seconds <= 0) return '--:--';
    return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
  }
  albumKey(_: number, album: LibraryHomeAlbum): string { return `${album.pathId}:${album.artist}:${album.album}`; }
  artistKey(_: number, artist: LibraryHomeArtist): string { return `${artist.pathId}:${artist.artist}`; }
  trackKey(_: number, track: MusicMetadataDto): string { return `${track.nasPathId}:${track.path}`; }
  playlistKey(_: number, playlist: any): number { return playlist.id; }
}
