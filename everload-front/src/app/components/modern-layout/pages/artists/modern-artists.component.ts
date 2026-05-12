import { Component, OnInit, OnDestroy } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { ArtistProfileDto, MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

interface ArtistGroup {
  artist: string;
  tracks: MusicMetadataDto[];
  cover: MusicMetadataDto | null;
  pathId: number;
  albumCount: number;
  profile?: ArtistProfileDto;
  imageUrl?: string;
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
  private sub!: Subscription;
  private indexPoll?: ReturnType<typeof setTimeout>;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      if (pid != null) this.load(pid);
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    if (this.indexPoll) clearTimeout(this.indexPoll);
  }

  private load(pathId: number) {
    this.loading = true;
    forkJoin({
      overview: this.music.getLibraryOverview(pathId, 5000),
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
          const rawArtist = (t.artist || '').trim() || 'Desconocido';
          const profile = this.findProfileForArtist(rawArtist, profileByKey);
          const displayName = profile?.name || rawArtist;
          const key = this.key(displayName);
          if (!map.has(key)) {
            map.set(key, {
              artist: displayName,
              tracks: [t],
              cover: t,
              pathId,
              albumCount: t.album ? 1 : 0,
              profile,
              imageUrl: this.profileImage(profile)
            });
          } else {
            const g = map.get(key)!;
            g.tracks.push(t);
            if (t.album && !g.tracks.some(x => x.album === t.album)) g.albumCount++;
          }
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
    this.loading = false;
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

    const existing = new Set(group.tracks.map(t => t.path));
    tracks.forEach(track => {
      if (existing.has(track.path)) return;
      existing.add(track.path);
      group!.tracks.push(track);
      if (!group!.cover) group!.cover = track;
    });
    group.albumCount = new Set(group.tracks.map(t => t.album).filter(Boolean)).size;
  }

  cover(g: ArtistGroup): string {
    if (g.imageUrl) return g.imageUrl;
    return g.cover ? this.music.getCoverUrlWithCache(g.pathId, g.cover.path, g.cover.source) : '';
  }

  play(g: ArtistGroup) {
    if (!g.tracks.length) return;
    this.music.setQueue(g.pathId, g.tracks, 0);
  }

  openCreate() {
    this.editorOpen = true;
    this.editing = null;
    this.editName = '';
    this.editAliases = '';
    this.editDescription = '';
    this.selectedImage = null;
    this.error = '';
  }

  openEdit(g: ArtistGroup) {
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
    if (!this.editName.trim() || this.pathId == null) return;
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
    const id = this.editing?.profile?.id;
    if (!id || this.pathId == null) return;
    this.music.removeArtistImage(id).subscribe(() => this.load(this.pathId!));
  }

  deleteArtist() {
    const id = this.editing?.profile?.id;
    if (!id || this.pathId == null || !confirm('¿Eliminar artista manual?')) return;
    this.music.deleteArtistProfile(id).subscribe(() => {
      this.closeEditor();
      this.load(this.pathId!);
    });
  }

  fillMissingMetadata() {
    if (this.pathId == null || this.bulkLoading) return;
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
