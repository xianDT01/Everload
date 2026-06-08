import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { Pipe, PipeTransform } from '@angular/core';

import { SpotifyDownloadsComponent } from './spotify-downloads.component';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';
import { NotificationService } from '../../services/notification.service';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

describe('SpotifyDownloadsComponent', () => {
  let component: SpotifyDownloadsComponent;
  let fixture: ComponentFixture<SpotifyDownloadsComponent>;
  let httpMock: HttpTestingController;
  let translateSpy: jasmine.SpyObj<TranslateService>;

  beforeEach(async () => {
    translateSpy = jasmine.createSpyObj<TranslateService>('TranslateService', ['use', 'instant']);
    translateSpy.instant.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, HttpClientTestingModule, FormsModule],
      declarations: [SpotifyDownloadsComponent, FakeTranslatePipe],
      providers: [
        { provide: TranslateService, useValue: translateSpy },
        { provide: AuthService, useValue: jasmine.createSpyObj<AuthService>('AuthService', ['hasNasAccess']) },
        { provide: ApiBaseService, useValue: { backendUrl: '', isNativePlatform: () => false, withBackend: (s: string) => s } },
        { provide: NotificationService, useValue: jasmine.createSpyObj<NotificationService>('NotificationService', ['showToast']) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SpotifyDownloadsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('descargarListaCanciones()', () => {
    it('rejects a blank playlist URL without issuing a request', () => {
      component.playlistUrl = '   ';

      component.descargarListaCanciones();

      expect(component.error).toBe('EMPTY_URL_ERROR_Spotify');
      expect(component.cargando).toBeFalse();
      httpMock.expectNone('/api/spotify/playlist');
    });

    it('loads the playlist tracks on success', () => {
      component.playlistUrl = 'https://open.spotify.com/playlist/abc123';

      component.descargarListaCanciones();

      expect(component.cargando).toBeTrue();
      httpMock.expectOne('/api/spotify/playlist').flush([
        { title: 'Song A', youtubeUrl: 'https://youtube.com/watch?v=xyz' },
        { title: 'Song B', youtubeUrl: null },
      ]);

      expect(component.cargando).toBeFalse();
      expect(component.buscado).toBeTrue();
      expect(component.tracks).toEqual([
        { title: 'Song A', youtubeUrl: 'https://youtube.com/watch?v=xyz', status: 'idle', progress: 0 },
        { title: 'Song B', youtubeUrl: null, status: 'idle', progress: 0 },
      ]);
    });

    it('surfaces a translated error message when the search fails', () => {
      component.playlistUrl = 'https://open.spotify.com/playlist/abc123';

      component.descargarListaCanciones();

      httpMock.expectOne('/api/spotify/playlist').flush({}, { status: 500, statusText: 'Server Error' });

      expect(component.error).toBe('DOWNLOAD_Spotify_FAILED');
      expect(component.buscado).toBeTrue();
      expect(component.cargando).toBeFalse();
    });
  });

  describe('descargarTodas()', () => {
    it('only triggers downloads for idle tracks that have a YouTube URL', () => {
      component.tracks = [
        { title: 'A', youtubeUrl: 'https://youtube.com/watch?v=aaa', status: 'idle', progress: 0 },
        { title: 'B', youtubeUrl: null, status: 'idle', progress: 0 },
        { title: 'C', youtubeUrl: 'https://youtube.com/watch?v=ccc', status: 'completed', progress: 100 },
      ];
      spyOn(component, 'descargarCancion');

      component.descargarTodas();

      expect(component.descargarCancion).toHaveBeenCalledTimes(1);
      expect(component.descargarCancion).toHaveBeenCalledWith(component.tracks[0]);
    });
  });

  it('persists the chosen language preference', () => {
    spyOn(localStorage, 'setItem');

    component.changeLanguage('en');

    expect(translateSpy.use).toHaveBeenCalledWith('en');
    expect(localStorage.setItem).toHaveBeenCalledWith('language', 'en');
  });
});
