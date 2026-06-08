import { ComponentFixture, TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { Pipe, PipeTransform } from '@angular/core';
import { of, throwError } from 'rxjs';

import { InstagramDownloadsComponent } from './instagram-downloads.component';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NasService, NasPath } from '../../services/nas.service';
import { MusicService } from '../../services/music.service';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

describe('InstagramDownloadsComponent', () => {
  let component: InstagramDownloadsComponent;
  let fixture: ComponentFixture<InstagramDownloadsComponent>;
  let httpMock: HttpTestingController;
  let nasSpy: jasmine.SpyObj<NasService>;
  let musicSpy: jasmine.SpyObj<MusicService>;
  let translateSpy: jasmine.SpyObj<TranslateService>;

  const NAS_PATHS: NasPath[] = [
    { id: 1, name: 'Música', path: '/music', writable: true } as NasPath,
    { id: 2, name: 'Solo lectura', path: '/ro', writable: false } as NasPath,
    { id: 3, name: 'Vídeos', path: '/videos', writable: true } as NasPath,
  ];

  beforeEach(async () => {
    translateSpy = jasmine.createSpyObj<TranslateService>('TranslateService', ['use', 'instant']);
    translateSpy.instant.and.callFake((key: string) => key);
    nasSpy = jasmine.createSpyObj<NasService>('NasService', ['getPaths']);
    nasSpy.getPaths.and.returnValue(of(NAS_PATHS));
    musicSpy = jasmine.createSpyObj<MusicService>('MusicService', ['ytDlpQueueUrl', 'ytDlpJobStatus']);

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule],
      declarations: [InstagramDownloadsComponent, FakeTranslatePipe],
      providers: [
        { provide: TranslateService, useValue: translateSpy },
        { provide: AuthService, useValue: jasmine.createSpyObj<AuthService>('AuthService', ['hasNasAccess']) },
        { provide: NasService, useValue: nasSpy },
        { provide: MusicService, useValue: musicSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InstagramDownloadsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('download() URL validation', () => {
    it('rejects URLs that are not instagram.com links', () => {
      component.videoUrl = 'https://example.com/video';

      component.download();

      expect(component.error).toBe('INVALID_INSTAGRAM_URL');
      expect(component.loading).toBeFalse();
      httpMock.expectNone(req => req.url.includes('/api/downloadInstagram'));
    });

    it('accepts instagram.com URLs and issues the download request', () => {
      component.videoUrl = 'https://www.instagram.com/reel/abc123/';

      component.download();

      expect(component.error).toBeNull();
      expect(component.loading).toBeTrue();
      httpMock.expectOne(req => req.url.startsWith('/api/downloadInstagram')).flush(new Blob(['x']), {
        status: 200, statusText: 'OK'
      });
      expect(component.loading).toBeFalse();
    });
  });

  describe('NAS picker', () => {
    it('loads only writable NAS paths and opens the picker', () => {
      component.openNasPicker();

      expect(nasSpy.getPaths).toHaveBeenCalled();
      expect(component.nasPaths).toEqual([NAS_PATHS[0], NAS_PATHS[2]]);
      expect(component.selectedNasPathId).toBe(1);
      expect(component.showNasPicker).toBeTrue();
    });
  });

  describe('saveToNas()', () => {
    it('does nothing without a URL or a selected NAS path', () => {
      component.videoUrl = '';
      component.selectedNasPathId = 1;
      component.saveToNas();
      expect(musicSpy.ytDlpQueueUrl).not.toHaveBeenCalled();

      component.videoUrl = 'https://www.instagram.com/reel/abc123/';
      component.selectedNasPathId = null;
      component.saveToNas();
      expect(musicSpy.ytDlpQueueUrl).not.toHaveBeenCalled();
    });

    it('queues the job, closes the picker and starts polling its status', fakeAsync(() => {
      component.videoUrl = 'https://www.instagram.com/reel/abc123/';
      component.selectedNasPathId = 1;
      component.nasSubPath = '  Reels  ';
      component.showNasPicker = true;

      musicSpy.ytDlpQueueUrl.and.returnValue(of({ jobId: 'job-1' }));
      musicSpy.ytDlpJobStatus.and.returnValue(of({ status: 'RUNNING', progress: 50 }));

      component.saveToNas();

      expect(musicSpy.ytDlpQueueUrl).toHaveBeenCalledWith('https://www.instagram.com/reel/abc123/', '', 1, 'Reels');
      expect(component.showNasPicker).toBeFalse();
      expect(component.nasJob).toEqual({ status: 'QUEUED', progress: 0 });

      tick(3000);
      expect(musicSpy.ytDlpJobStatus).toHaveBeenCalledWith('job-1');
      expect(component.nasJob).toEqual({ status: 'RUNNING', progress: 50 });

      discardPeriodicTasks();
    }));

    it('surfaces an error state when queueing fails', () => {
      component.videoUrl = 'https://www.instagram.com/reel/abc123/';
      component.selectedNasPathId = 1;
      musicSpy.ytDlpQueueUrl.and.returnValue(throwError(() => new Error('boom')));

      component.saveToNas();

      expect(component.nasJob.status).toBe('ERROR');
    });
  });
});
