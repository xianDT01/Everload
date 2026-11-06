import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { MusicService } from '../../../services/music.service';
import { LibraryHomeComponent } from './library-home.component';
import { PlaylistArtworkComponent } from '../playlist-artwork/playlist-artwork.component';

describe('EverWave home layout', () => {
  let fixture: ComponentFixture<LibraryHomeComponent>;
  let component: LibraryHomeComponent;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [LibraryHomeComponent], imports: [CommonModule, TranslateModule.forRoot(), PlaylistArtworkComponent],
      providers: [{ provide: MusicService, useValue: { getCoverUrlWithCache: () => '' } }]
    }).compileComponents();
    fixture = TestBed.createComponent(LibraryHomeComponent);
    component = fixture.componentInstance;
    component.albums = Array.from({ length: 6 }, (_, i) => ({ album: `Album ${i}`, artist: 'Artist', pathId: 1, track: { path: `${i}.mp3` } as any }));
    component.continueAlbums = component.albums;
    component.artists = [{ artist: 'Artist', pathId: 1, tracks: [component.albums[0].track] }];
    component.recent = [{ name: 'Recent', path: 'recent.mp3', nasPathId: 1, duration: 90 } as any];
    component.playlists = [{ id: 8, name: 'My playlist', tracks: [] }];
    fixture.detectChanges();
  });
  const clickFilter = (fixture: ComponentFixture<LibraryHomeComponent>, index: number) => {
    fixture.nativeElement.querySelectorAll('.ew-filters button')[index].click();
    fixture.detectChanges();
  };

  it('shows four albums, artists and recent songs in the overview', () => {
    expect(fixture.nativeElement.querySelectorAll('.ew-album').length).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('.ew-artist').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('.ew-track').length).toBe(1);
  });
  it('filters albums, artists and playlists without navigation or playback', () => {
    const play = spyOn(component.albumPlay, 'emit');
    clickFilter(fixture, 1);
    expect(fixture.nativeElement.querySelectorAll('.ew-album').length).toBe(6);
    expect(fixture.nativeElement.querySelector('.ew-recent')).toBeNull();
    clickFilter(fixture, 2);
    expect(fixture.nativeElement.querySelectorAll('.ew-artist').length).toBe(1);
    clickFilter(fixture, 3);
    expect(fixture.nativeElement.querySelector('.ew-album').textContent).toContain('My playlist');
    expect(play).not.toHaveBeenCalled();
  });
  it('emits the actual album, artist, playlist and recent track on activation', () => {
    const album = spyOn(component.albumPlay, 'emit');
    const artist = spyOn(component.artistPlay, 'emit');
    const track = spyOn(component.trackPlay, 'emit');
    const playlist = spyOn(component.playlistOpen, 'emit');
    fixture.nativeElement.querySelector('.ew-album').click();
    fixture.nativeElement.querySelector('.ew-artist').click();
    fixture.nativeElement.querySelector('.ew-track').click();
    clickFilter(fixture, 3);
    fixture.nativeElement.querySelector('.ew-album').click();
    expect(album).toHaveBeenCalledWith(component.albums[0]);
    expect(artist).toHaveBeenCalledWith(component.artists[0]);
    expect(track).toHaveBeenCalledWith(component.recent[0]);
    expect(playlist).toHaveBeenCalledWith(component.playlists[0]);
  });
  it('keeps a placeholder when a cover fails', () => {
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.ew-artwork img');
    image.dispatchEvent(new Event('error'));
    expect(image.hidden).toBeTrue();
    expect(fixture.nativeElement.querySelector('.ew-artwork .ew-placeholder')).not.toBeNull();
    image.dispatchEvent(new Event('load'));
    expect(image.hidden).toBeFalse();
  });
  it('exposes folder navigation independently from filters', () => {
    const folders = spyOn(component.foldersOpen, 'emit');
    fixture.nativeElement.querySelector('.ew-folder-button').click();
    expect(folders).toHaveBeenCalled();
    expect(component.filter).toBe('all');
  });
  it('formats missing and valid durations consistently', () => {
    expect(component.duration(0)).toBe('--:--');
    expect(component.duration(NaN)).toBe('--:--');
    expect(component.duration(185)).toBe('3:05');
  });
  it('does not truncate the overview to five artists and opens the complete artist view', () => {
    fixture.componentRef.setInput('artists', Array.from({ length: 65 }, (_, i) => ({ artist: `Artist ${i}`, pathId: 1, tracks: [component.albums[0].track] })));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.ew-artists-overview .ew-artist').length).toBe(48);
    fixture.nativeElement.querySelector('.ew-see-all').click();
    fixture.detectChanges();
    expect(component.filter).toBe('artists');
    expect(fixture.nativeElement.querySelector('.ew-artists-overview')).toBeNull();
    fixture.nativeElement.querySelector('.ew-more').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.ew-artist').length).toBe(65);
  });
});
