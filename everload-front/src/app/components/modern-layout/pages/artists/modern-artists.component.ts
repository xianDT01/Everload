import { Component, OnInit, OnDestroy } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { ArtistProfileDto, MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';
import { AuthService } from '../../../../services/auth.service';

interface ArtistGroup {
  artist: string;
  tracks: MusicMetadataDto[];
  cover: MusicMetadataDto | null;
  pathId: number;
  albumCount: number;
  profile?: ArtistProfileDto;
  imageUrl?: string;
  autoImageUrl?: string;
}

@Component({
  selector: 'app-modern-artists',
  templateUrl: './modern-artists.component.html',
  styleUrls: ['./modern-artists.component.css']
})
export class ModernArtistsComponent implements OnInit, OnDestroy {
  artists: ArtistGroup[] = [];
  loading = false;
  pathId: number | null = null;
  editorOpen = false;
  editing: ArtistGroup | null = null;
  editName = '';
  editAliases = '';
  editDescription = '';
  selectedImage: File | null = null;
  saving = false;
  error = '';
  bulkLoading = false;
  bulkStatus = '';
  selectedArtist: ArtistGroup | null = null;
  selectedArtistTracks: MusicMetadataDto[] = [];
  artistViewOrder: 'tracks' | 'albums' = (localStorage.getItem('mpl_artist_view') as any) || 'tracks';

  get artistAlbums(): { album: string; tracks: MusicMetadataDto[] }[] {
    const map = new Map<string, MusicMetadataDto[]>();
    for (const t of this.selectedArtistTracks) {
      const key = t.album?.trim() || 'Sin álbum';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(t);
    }
    return Array.from(map.entries())
      .map(([album, tracks]) => ({ album, tracks }))
      .sort((a, b) => a.album.localeCompare(b.album));
  }

  setArtistViewOrder(v: 'tracks' | 'albums') {
    this.artistViewOrder = v;
    localStorage.setItem('mpl_artist_view', v);
  }

  private sub!: Subscription;
  private artistRequestSub?: Subscription;
  private indexPoll?: ReturnType<typeof setTimeout>;
  private imageRetryTimer?: ReturnType<typeof setTimeout>;
  private pendingArtistName = '';

  constructor(public music: MusicService, private state: ModernStateService, private auth: AuthService) {}

  get canManageNas(): boolean {
    return this.auth.canManageNas();
  }

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      if (pid != null) this.load(pid);
    });
    this.artistRequestSub = this.state.selectedArtistName$.subscribe(name => {
      this.pendingArtistName = name || '';
      this.openRequestedArtist();
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    this.artistRequestSub?.unsubscribe();
    if (this.indexPoll) clearTimeout(this.indexPoll);
    if (this.imageRetryTimer) clearTimeout(this.imageRetryTimer);
  }

  private load(pathId: number) {
    this.loading = true;
    forkJoin({
      overview: this.state.getOverview(pathId),
      profiles: this.music.getArtistProfiles()
    }).subscribe({
      next: ({ overview, profiles }) => {
        const tracks = overview.tracks || [];
        if (this.indexPoll) clearTimeout(this.indexPoll);
        if (overview.indexing && tracks.length === 0) {
          this.indexPoll = setTimeout(() => this.load(pathId), 6000);
        }
        const map = new Map<string, ArtistGroup>();
        const profileByKey = new Map<string, ArtistProfileDto>();

        profiles.forEach(profile => {
          this.profileKeys(profile).forEach(key => profileByKey.set(key, profile));
        });

        tracks.forEach(t => {
          this.artistDisplayPartsForTrack(t).forEach(artistName => {
            const profile = this.findProfileForArtist(artistName, profileByKey);
            this.addTrackToGroup(map, pathId, profile?.name || artistName, t, profile);
          });
        });

        profiles.forEach(profile => {
          const key = this.key(profile.name);
          if (!map.has(key)) {
            map.set(key, {
              artist: profile.name,
              tracks: [],
              cover: null,
              pathId,
              albumCount: 0,
              profile,
              imageUrl: this.profileImage(profile)
            });
          }
        });

        const unresolvedProfiles = profiles.filter(profile => {
          const g = map.get(this.key(profile.name));
          return !g || g.tracks.length === 0;
        });

        if (!unresolvedProfiles.length) {
          this.finishLoad(map);
          return;
        }

        forkJoin(unresolvedProfiles.map(profile => this.searchProfileTracks(pathId, profile))).subscribe({
          next: resolvedLists => {
            resolvedLists.forEach((tracks, index) => {
              this.mergeProfileTracks(map, pathId, unresolvedProfiles[index], tracks);
            });
            this.finishLoad(map);
          },
          error: () => this.finishLoad(map)
        });
      },
      error: () => { this.loading = false; }
    });
  }

  private finishLoad(map: Map<string, ArtistGroup>) {
    map.forEach(group => {
      group.albumCount = new Set(group.tracks.map(t => t.album).filter(Boolean)).size;
    });
    this.artists = Array.from(map.values()).sort((a, b) => a.artist.localeCompare(b.artist));
    this.resolveAutoArtistImages();
    this.openRequestedArtist();
    this.loading = false;
  }

  private addTrackToGroup(
    map: Map<string, ArtistGroup>,
    pathId: number,
    artist: string,
    track: MusicMetadataDto,
    profile?: ArtistProfileDto
  ) {
    const displayName = artist.trim() || 'Desconocido';
    const key = this.key(displayName);
    if (!map.has(key)) {
      map.set(key, {
        artist: displayName,
        tracks: [track],
        cover: track,
        pathId,
        albumCount: track.album ? 1 : 0,
        profile,
        imageUrl: this.profileImage(profile)
      });
      return;
    }

    const group = map.get(key)!;
    const trackKey = this.key(track.title || track.name);
    if (group.tracks.some(existing => this.key(existing.title || existing.name) === trackKey)) return;
    group.tracks.push(track);
    if (!group.cover) group.cover = track;
    if (profile && !group.profile) {
      group.profile = profile;
      group.imageUrl = this.profileImage(profile);
    }
  }

  private searchProfileTracks(pathId: number, profile: ArtistProfileDto) {
    const aliases = (profile.aliases || '').split(/[\n,]+/).map(a => a.trim()).filter(Boolean);
    return this.music.getArtistTracks(pathId, profile.name, aliases, 1000);
  }

  private mergeProfileTracks(map: Map<string, ArtistGroup>, pathId: number, profile: ArtistProfileDto, tracks: MusicMetadataDto[]) {
    const key = this.key(profile.name);
    let group = map.get(key);
    if (!group) {
      group = {
        artist: profile.name,
        tracks: [],
        cover: null,
        pathId,
        albumCount: 0,
        profile,
        imageUrl: this.profileImage(profile)
      };
      map.set(key, group);
    }

    const existing = new Set(group.tracks.map(t => this.key(t.title || t.name)));
    tracks.forEach(track => {
      const trackKey = this.key(track.title || track.name);
      if (existing.has(trackKey)) return;
      existing.add(trackKey);
      group!.tracks.push(track);
      if (!group!.cover) group!.cover = track;
    });
    group.albumCount = new Set(group.tracks.map(t => t.album).filter(Boolean)).size;
  }

  cover(g: ArtistGroup): string {
    if (g.imageUrl) return g.imageUrl;
    const source = localStorage.getItem('mpl_artist_photo_source') || 'deezer';
    if (source === 'deezer' && g.autoImageUrl) return g.autoImageUrl;
    if (g.cover) return this.music.getCoverUrlWithCache(g.pathId, g.cover.path, g.cover.source);
    return g.autoImageUrl || '';
  }

  play(g: ArtistGroup) {
    if (!g.tracks.length) return;
    this.music.setQueue(g.pathId, g.tracks, 0);
  }

  openArtistSongs(g: ArtistGroup) {
    this.selectedArtist = g;
    this.selectedArtistTracks = [...g.tracks].sort((a, b) =>
      (a.album || '').localeCompare(b.album || '') || (a.title || a.name).localeCompare(b.title || b.name)
    );
  }

  closeArtistSongs() {
    this.selectedArtist = null;
    this.selectedArtistTracks = [];
  }

  private openRequestedArtist() {
    const key = this.key(this.pendingArtistName);
    if (!key || !this.artists.length) return;
    const requested = this.artists.find(artist => {
      const profileKeys = artist.profile ? this.profileKeys(artist.profile) : [];
      return this.key(artist.artist) === key || profileKeys.includes(key);
    });
    if (requested) {
      this.openArtistSongs(requested);
      this.pendingArtistName = '';
    }
  }

  playSelectedArtist(index = 0) {
    if (!this.selectedArtist || !this.selectedArtistTracks.length) return;
    this.music.setQueue(this.selectedArtist.pathId, this.selectedArtistTracks, index);
  }

  openCreate() {
    if (!this.canManageNas) return;
    this.editorOpen = true;
    this.editing = null;
    this.editName = '';
    this.editAliases = '';
    this.editDescription = '';
    this.selectedImage = null;
    this.error = '';
  }

  openEdit(g: ArtistGroup) {
    if (!this.canManageNas) return;
    this.editorOpen = true;
    this.editing = g;
    this.editName = g.profile?.name || g.artist;
    this.editAliases = g.profile?.aliases || '';
    this.editDescription = g.profile?.description || '';
    this.selectedImage = null;
    this.error = '';
  }

  closeEditor() {
    this.editorOpen = false;
    this.editing = null;
    this.editName = '';
    this.editAliases = '';
    this.editDescription = '';
    this.selectedImage = null;
    this.error = '';
  }

  onImageSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.selectedImage = input.files?.[0] || null;
  }

  saveArtist() {
    if (!this.canManageNas || !this.editName.trim() || this.pathId == null) return;
    this.saving = true;
    this.error = '';
    const existingId = this.editing?.profile?.id;
    const request = existingId
      ? this.music.updateArtistProfile(existingId, this.editName, this.editAliases, this.editDescription)
      : this.music.createArtistProfile(this.editName, this.editAliases, this.editDescription);

    request.subscribe({
      next: profile => {
        const finish = () => {
          this.saving = false;
          this.closeEditor();
          this.load(this.pathId!);
        };
        if (this.selectedImage) {
          this.music.uploadArtistImage(profile.id, this.selectedImage).subscribe({
            next: finish,
            error: err => {
              this.saving = false;
              this.error = err?.error?.error || 'No se pudo subir la imagen';
            }
          });
        } else {
          finish();
        }
      },
      error: err => {
        this.saving = false;
        this.error = err?.error?.error || 'No se pudo guardar el artista';
      }
    });
  }

  removeImage() {
    if (!this.canManageNas) return;
    const id = this.editing?.profile?.id;
    if (!id || this.pathId == null) return;
    this.music.removeArtistImage(id).subscribe(() => this.load(this.pathId!));
  }

  deleteArtist() {
    if (!this.canManageNas) return;
    const id = this.editing?.profile?.id;
    if (!id || this.pathId == null || !confirm('¿Eliminar artista manual?')) return;
    this.music.deleteArtistProfile(id).subscribe(() => {
      this.closeEditor();
      this.load(this.pathId!);
    });
  }

  fillMissingMetadata() {
    if (!this.canManageNas || this.pathId == null || this.bulkLoading) return;
    if (!confirm('Esto buscará metadatos en YouTube para canciones sin artista/álbum/título. Puede tardar. ¿Continuar?')) return;

    this.bulkLoading = true;
    this.bulkStatus = 'Buscando metadatos...';
    this.music.fillYoutubeMetadataBulk(this.pathId, '', 75, true).subscribe({
      next: result => {
        this.bulkLoading = false;
        this.bulkStatus = `Actualizadas ${result.updated || 0} de ${result.processed || 0}. Omitidas ${result.skipped || 0}. Errores ${result.failed || 0}.`;
        this.load(this.pathId!);
      },
      error: err => {
        this.bulkLoading = false;
        this.bulkStatus = err?.error?.error || 'No se pudieron actualizar los metadatos.';
      }
    });
  }

  private profileImage(profile?: ArtistProfileDto): string {
    if (!profile?.imageUrl) return '';
    return profile.imageUrl.startsWith('http') ? profile.imageUrl : `${this.music.BASE}${profile.imageUrl}`;
  }

  private resolveAutoArtistImages() {
    const candidates = this.artists
      .filter(a => !a.imageUrl && a.tracks.length > 0 && !this.isSuspiciousArtistName(a.artist))
      .sort((a, b) => b.tracks.length - a.tracks.length)
      .slice(0, 250);
    this.music.resolveArtistImages(candidates);
    this.scheduleImageRetry(candidates);
  }

  private scheduleImageRetry(candidates: ArtistGroup[]) {
    if (this.imageRetryTimer) clearTimeout(this.imageRetryTimer);
    this.imageRetryTimer = setTimeout(() => {
      const missing = candidates.filter(a => !a.imageUrl && !a.autoImageUrl);
      if (missing.length > 0) {
        this.music.clearArtistImageCacheFailed();
        this.music.resolveArtistImages(missing);
      }
    }, 12000);
  }

  private profileKeys(profile: ArtistProfileDto): string[] {
    const aliases = (profile.aliases || '').split(/[\n,]+/).map(a => a.trim()).filter(Boolean);
    return [profile.name, ...aliases].map(v => this.key(v)).filter(Boolean);
  }

  private findProfileForArtist(rawArtist: string, profileByKey: Map<string, ArtistProfileDto>): ArtistProfileDto | undefined {
    const exact = profileByKey.get(this.key(rawArtist));
    if (exact) return exact;

    const parts = rawArtist
      .split(/\s*(?:,|;|&|\+|\/|\bfeat\.?\b|\bft\.?\b|\bcon\b|\band\b| y )\s*/i)
      .map(part => this.key(part))
      .filter(Boolean);

    for (const part of parts) {
      const profile = profileByKey.get(part);
      if (profile) return profile;
    }

    return undefined;
  }

  private artistDisplayParts(value: string): string[] {
    const raw = (value || '').trim();
    if (!raw) return ['Desconocido'];
    const parts = raw
      .split(/\s*(?:,|;|&|\+|\/|\bfeat\.?\b|\bft\.?\b|\bcon\b|\band\b| y )\s*/i)
      .map(part => part.trim())
      .filter(Boolean);
    const unique = new Map<string, string>();
    (parts.length ? parts : [raw]).forEach(part => {
      const key = this.key(part);
      if (key && !this.isSuspiciousArtistName(part) && !unique.has(key)) unique.set(key, part);
    });
    const values = Array.from(unique.values());
    return values.length ? values : ['Desconocido'];
  }

  private artistDisplayPartsForTrack(track: MusicMetadataDto): string[] {
    const rawArtist = (track.artist || '').trim();
    if (rawArtist && !this.isUnknownArtistName(rawArtist)) {
      return this.artistDisplayParts(rawArtist);
    }
    const inferred = this.inferArtistFromTrackName(track);
    return inferred ? this.artistDisplayParts(inferred) : ['Desconocido'];
  }

  private inferArtistFromTrackName(track: MusicMetadataDto): string {
    const candidates = [track.title, track.name, track.path?.split(/[\\/]/).pop()]
      .map(v => this.cleanFilenameTitle(v || ''))
      .filter(Boolean);

    for (const candidate of candidates) {
      const match = candidate.match(/^(.+?)\s+-\s+(.+)$/);
      if (match?.[1]) {
        const artist = this.cleanInferredArtist(match[1]);
        if (artist && !this.isSuspiciousArtistName(artist)) return artist;
      }
    }

    return '';
  }

  private cleanFilenameTitle(value: string): string {
    return value
      .replace(/\.[a-z0-9]{2,5}$/i, '')
      .replace(/[_]+/g, ' ')
      .replace(/&amp;/gi, '&')
      .replace(/&#0*39;/gi, "'")
      .replace(/^\s*(?:\(?\d+\)?\s*[\.-]\s*)+/, '')
      .replace(/^\s*[-–—]+\s*/, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private cleanInferredArtist(value: string): string {
    return value
      .replace(/^\s*(?:\(?\d+\)?\s*[\.-]\s*)+/, '')
      .replace(/^\s*[-–—]+\s*/, '')
      .replace(/\s*\((?:audio|official|video|lyrics?|lyric video|cover audio)\)\s*$/i, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private isUnknownArtistName(value: string): boolean {
    const key = this.key(value);
    return !key || key === 'unknown' || key === 'desconocido' || key === 'interprete desconocido';
  }

  private isSuspiciousArtistName(value: string): boolean {
    const key = this.key(value);
    if (!key) return true;
    return /\b(clean edit|audio edit|extended edit|radio edit|lyrics?|lyric video)\b/.test(key)
      || /\b(vevo|official|topic|records|recordings|music tv|musictv|entertainment|official channel)\b/.test(key)
      || key === 'dj clean edit'
      || key === 'unknown'
      || key === 'desconocido';
  }

  private key(value: string): string {
    return (value || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }
}
