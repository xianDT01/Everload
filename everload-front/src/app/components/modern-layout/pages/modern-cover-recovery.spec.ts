import { TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../services/music.service';
import { NasService } from '../../../services/nas.service';
import { AuthService } from '../../../services/auth.service';
import { ModernStateService } from '../modern-state.service';
import { ModernLibraryComponent } from './library/modern-library.component';
import { ModernAlbumsComponent } from './albums/modern-albums.component';

describe('Modern cover recovery', () => {
  const pixel = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
  let music: any;
  let tracks: MusicMetadataDto[];

  beforeEach(async () => {
    tracks = [{ path: 'first.mp3', title: 'First', album: 'Album', artist: 'Artist', nasPathId: 7, hasCover: false } as MusicMetadataDto];
    music = {
      getPlaylists: () => of([]), mainPlayer: { state: { currentTrack: null } },
      coverOverrideMap: new Map<string, string>(),
      getCoverUrlWithCache: jasmine.createSpy().and.callFake((_id: number, path: string) => music.coverOverrideMap.get(path) || pixel),
      hasCoverToShow: (track: MusicMetadataDto) => track.hasCover || music.coverOverrideMap.has(track.path)
    };
    await TestBed.configureTestingModule({
      declarations: [ModernLibraryComponent, ModernAlbumsComponent],
      imports: [CommonModule, FormsModule, ScrollingModule, TranslateModule.forRoot()],
      providers: [
        { provide: MusicService, useValue: music },
        { provide: NasService, useValue: {} },
        { provide: AuthService, useValue: {} },
        { provide: ModernStateService, useValue: {
          pathId$: new BehaviorSubject(1), getOverview: () => of({ tracks, indexing: false })
        } }
      ]
    }).compileComponents();
  });

  it('restores a library thumbnail after an error and a new cover URL', async () => {
    const fixture = TestBed.createComponent(ModernLibraryComponent);
    const host = fixture.nativeElement as HTMLElement;
    host.style.height = '600px';
    document.body.appendChild(host);
    try {
      fixture.detectChanges();
      await new Promise(resolve => setTimeout(resolve, 160));
      fixture.detectChanges();
      const image = host.querySelector('.ml-track-cover img') as HTMLImageElement;
      expect(image).toBeTruthy();
      image.dispatchEvent(new Event('error'));
      expect(image.style.opacity).toBe('0');
      expect(getComputedStyle(image).display).not.toBe('none');
      music.coverOverrideMap.set('first.mp3', pixel + '#found');
      fixture.detectChanges();
      expect(image.src).toBe(pixel + '#found');
      image.dispatchEvent(new Event('load'));
      expect(image.style.opacity).toBe('1');
      expect(music.getCoverUrlWithCache).toHaveBeenCalledWith(7, 'first.mp3', undefined);
    } finally {
      fixture.destroy();
      host.remove();
    }
  });

  it('restores both the album grid and detail artwork after a failed image', () => {
    const fixture = TestBed.createComponent(ModernAlbumsComponent);
    fixture.detectChanges();
    const verifyRecovery = (selector: string) => {
      const image = fixture.nativeElement.querySelector(selector) as HTMLImageElement;
      image.dispatchEvent(new Event('error'));
      expect(image.style.opacity).toBe('0');
      expect(image.style.display).not.toBe('none');
      image.dispatchEvent(new Event('load'));
      expect(image.style.opacity).toBe('1');
    };
    verifyRecovery('.ma-img');
    fixture.componentInstance.openDetail(fixture.componentInstance.albums[0]);
    fixture.detectChanges();
    verifyRecovery('.ma-detail-art img');
  });

  it('uses artwork found for another song in the album without fetching new artwork', () => {
    tracks.push({ ...tracks[0], path: 'second.mp3' });
    const fixture = TestBed.createComponent(ModernAlbumsComponent);
    fixture.detectChanges();
    music.coverOverrideMap.set('second.mp3', pixel + '#album');
    fixture.detectChanges();
    const image = fixture.nativeElement.querySelector('.ma-img') as HTMLImageElement;
    expect(image.src).toBe(pixel + '#album');
    expect(music.getCoverUrlWithCache).toHaveBeenCalledWith(7, 'second.mp3', undefined);
  });
});
