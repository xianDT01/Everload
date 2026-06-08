import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import {
  MusicMetadataDto,
  MusicService,
  YtMusicAlbumDto,
  YtMusicArtistDto,
  YtMusicDiscoverHomeDto,
  YtMusicDiscoverItemDto,
  YtMusicDiscoverShelfDto,
  YtMusicTrackDto
} from '../../../../services/music.service';

type ViewMode = 'discover' | 'search' | 'artist' | 'album' | 'playlist';

@Component({
  selector: 'app-modern-ytmusic',
  templateUrl: './modern-ytmusic.component.html',
  styleUrls: ['./modern-ytmusic.component.css']
})
export class ModernYtMusicComponent implements OnInit, OnDestroy {
  readonly pathId = -20;

  query = '';
  mode: ViewMode = 'discover';
  loading = false;
  loadingMore = false;
  error = '';

  shelves: YtMusicDiscoverShelfDto[] = [];
  continuation = '';
  results: MusicMetadataDto[] = [];
  history: MusicMetadataDto[] = [];

  album: YtMusicAlbumDto | null = null;
  artist: YtMusicArtistDto | null = null;
  playlist: { playlistId: string; title: string; thumbnailUrl?: string; tracks: YtMusicTrackDto[] } | null = null;

  private sub?: Subscription;
  private debounce?: ReturnType<typeof setTimeout>;

  constructor(public music: MusicService, private translate: TranslateService) {}

  ngOnInit(): void {
    this.history = this.music.getYtMusicHistory();
    this.loadDiscover();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.debounce) clearTimeout(this.debounce);
  }

  onSearchInput(): void {
    if (this.debounce) clearTimeout(this.debounce);
    const q = this.query.trim();
    if (!q) {
      this.results = [];
      if (this.mode === 'search') this.mode = 'discover';
      return;
    }
    this.debounce = setTimeout(() => this.search(), 250);
  }

  search(): void {
    const q = this.query.trim();
    if (!q) return;
    this.run('search', () => {
      this.sub = this.music.searchYtMusic(q).subscribe({
        next: res => {
          this.results = this.music.toYtMusicQueue(res.items || []);
          this.loading = false;
        },
        error: err => this.fail(err)
      });
    });
  }

  loadDiscover(): void {
    this.mode = 'discover';
    this.clearDetails();
    this.run('discover', () => {
      this.sub = this.music.discoverYtMusicHome().subscribe({
        next: res => this.applyDiscover(res),
        error: err => this.fail(err)
      });
    });
  }

  loadMore(): void {
    if (!this.continuation || this.loadingMore) return;
    this.loadingMore = true;
    this.music.discoverYtMusicContinuation(this.continuation).subscribe({
      next: res => {
        this.shelves = [...this.shelves, ...(res.shelves || [])];
        this.continuation = res.continuation || '';
        this.loadingMore = false;
      },
      error: err => {
        this.error = this.errorText(err);
        this.loadingMore = false;
      }
    });
  }

  openItem(item: YtMusicDiscoverItemDto): void {
    if (item.type === 'SONG' && item.track) {
      this.playTracks([item.track], 0);
    } else if (item.type === 'ALBUM' && item.browseId) {
      this.loadAlbum(item.browseId);
    } else if (item.type === 'ARTIST' && item.channelId) {
      this.loadArtist(item.channelId);
    } else if (item.type === 'PLAYLIST' && item.playlistId) {
      this.loadPlaylist(item.playlistId);
    }
  }

  loadAlbum(browseId: string): void {
    this.run('album', () => {
      this.sub = this.music.getYtMusicAlbum(browseId).subscribe({
        next: album => {
          this.clearDetails();
          this.album = album;
          this.loading = false;
        },
        error: err => this.fail(err)
      });
    });
  }

  loadArtist(channelId: string): void {
    this.run('artist', () => {
      this.sub = this.music.getYtMusicArtist(channelId).subscribe({
        next: artist => {
          this.clearDetails();
          this.artist = artist;
          this.loading = false;
        },
        error: err => this.fail(err)
      });
    });
  }

  resolveArtist(name: string): void {
    const value = (name || '').trim();
    if (!value) return;
    this.loading = true;
    this.error = '';
    this.music.resolveYtMusicArtist(value).subscribe({
      next: res => {
        if (res.channelId) {
          this.loadArtist(res.channelId);
        } else {
          this.loading = false;
          this.error = this.translate.instant('MUSIC.YTMUSIC_ARTIST_NOT_FOUND');
        }
      },
      error: err => this.fail(err)
    });
  }

  loadPlaylist(playlistId: string): void {
    this.run('playlist', () => {
      this.sub = this.music.getYtMusicPlaylist(playlistId).subscribe({
        next: playlist => {
          this.clearDetails();
          this.playlist = playlist;
          this.loading = false;
        },
        error: err => this.fail(err)
      });
    });
  }

  startMix(track: YtMusicTrackDto | MusicMetadataDto): void {
    const videoId = 'videoId' in track ? track.videoId : track.path;
    if (!videoId) return;
    this.loading = true;
    this.error = '';
    this.music.startYtMusicMix(videoId).subscribe({
      next: res => {
        const tracks = res.items || [];
        if (tracks.length) this.playTracks(tracks, 0);
        this.loading = false;
      },
      error: err => this.fail(err)
    });
  }

  playTrack(track: MusicMetadataDto, list: MusicMetadataDto[]): void {
    const i = list.findIndex(t => t.path === track.path);
    this.music.setQueue(this.pathId, list, Math.max(0, i));
    this.history = this.music.getYtMusicHistory();
  }

  playTracks(tracks: YtMusicTrackDto[] | MusicMetadataDto[], index = 0): void {
    const queue = this.normalizeTracks(tracks);
    if (!queue.length) return;
    this.music.setQueue(this.pathId, queue, Math.min(Math.max(index, 0), queue.length - 1));
    this.history = this.music.getYtMusicHistory();
  }

  appendTrack(track: MusicMetadataDto | YtMusicTrackDto): void {
    const item = this.normalizeTracks([track])[0];
    if (!item) return;
    const q = this.music.queueSnapshot;
    const tracks = q.pathId === this.pathId ? [...q.tracks, item] : [item];
    const index = q.pathId === this.pathId && q.index >= 0 ? q.index : 0;
    if (q.pathId === this.pathId && q.tracks.length) {
      this.music.updateQueue(this.pathId, tracks, index);
    } else {
      this.music.setQueue(this.pathId, tracks, 0);
    }
  }

  clearHistory(): void {
    this.music.clearYtMusicHistory();
    this.history = [];
  }

  tracksForCurrentView(): MusicMetadataDto[] {
    if (this.mode === 'search') return this.results;
    if (this.mode === 'album' && this.album) return this.music.toYtMusicQueue(this.album.tracks || []);
    if (this.mode === 'playlist' && this.playlist) return this.music.toYtMusicQueue(this.playlist.tracks || []);
    if (this.mode === 'artist' && this.artist) return this.music.toYtMusicQueue(this.artist.topSongs || []);
    return [];
  }

  cover(track: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(this.pathId, track.path, track.source);
  }

  itemImage(item: YtMusicDiscoverItemDto): string {
    return item.thumbnailUrl || item.track?.thumbnailUrl || '';
  }

  fmt(seconds: number | undefined): string {
    const s = Number(seconds || 0);
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }

  trackByPath(_: number, track: MusicMetadataDto): string {
    return track.path;
  }

  private run(mode: ViewMode, start: () => void): void {
    this.sub?.unsubscribe();
    this.mode = mode;
    this.loading = true;
    this.error = '';
    start();
  }

  private applyDiscover(res: YtMusicDiscoverHomeDto): void {
    this.shelves = res.shelves || [];
    this.continuation = res.continuation || '';
    this.loading = false;
  }

  private clearDetails(): void {
    this.album = null;
    this.artist = null;
    this.playlist = null;
  }

  private normalizeTracks(tracks: Array<YtMusicTrackDto | MusicMetadataDto>): MusicMetadataDto[] {
    return (tracks || [])
      .map(t => 'videoId' in t ? this.music.toYtMusicTrack(t) : t)
      .filter(t => !!t.path);
  }

  private fail(err: any): void {
    this.error = this.errorText(err);
    this.loading = false;
  }

  private errorText(err: any): string {
    return err?.error?.error || err?.message || this.translate.instant('MUSIC.YTMUSIC_LOAD_ERROR');
  }
}
