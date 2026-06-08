import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA, Pipe, PipeTransform } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { YoutubeDownloadsComponent } from './youtube-downloads.component';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { ChatService } from '../../services/chat.service';
import { ApiBaseService } from '../../services/api-base.service';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

describe('YoutubeDownloadsComponent', () => {
  let component: YoutubeDownloadsComponent;
  let fixture: ComponentFixture<YoutubeDownloadsComponent>;
  let translateSpy: jasmine.SpyObj<TranslateService>;

  beforeEach(async () => {
    translateSpy = jasmine.createSpyObj<TranslateService>('TranslateService', ['setDefaultLang', 'use', 'instant']);
    translateSpy.instant.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, HttpClientTestingModule, FormsModule],
      declarations: [YoutubeDownloadsComponent, FakeTranslatePipe],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: TranslateService, useValue: translateSpy },
        { provide: AuthService, useValue: jasmine.createSpyObj<AuthService>('AuthService', ['hasNasAccess']) },
        { provide: NotificationService, useValue: jasmine.createSpyObj<NotificationService>('NotificationService', ['showToast']) },
        { provide: ChatService, useValue: jasmine.createSpyObj<ChatService>('ChatService', ['getGroups', 'getActiveUsers']) },
        { provide: ApiBaseService, useValue: { backendUrl: '', isNativePlatform: () => false, withBackend: (s: string) => s } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(new Map()) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(YoutubeDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('persists the chosen language preference', () => {
    spyOn(localStorage, 'setItem');

    component.changeLanguage('en');

    expect(translateSpy.use).toHaveBeenCalledWith('en');
    expect(localStorage.setItem).toHaveBeenCalledWith('language', 'en');
  });

  describe('addToQueue()', () => {
    it('alerts and skips when the URL is blank', () => {
      spyOn(window, 'alert');
      component.videoUrl = '   ';

      component.addToQueue('video');

      expect(window.alert).toHaveBeenCalledWith('PLEASE_ENTER_YOUTUBE_LINK');
      expect(component.queue.length).toBe(0);
    });

    it('alerts and skips when the URL has no recognizable video id', () => {
      spyOn(window, 'alert');
      component.videoUrl = 'https://example.com/not-a-video';

      component.addToQueue('video');

      expect(window.alert).toHaveBeenCalledWith('INVALID_YOUTUBE_LINK');
      expect(component.queue.length).toBe(0);
    });

    it('queues a valid video URL with the chosen resolution and reveals the queue panel', () => {
      component.videoUrl = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
      component.resolution = '1080';

      component.addToQueue('video');

      expect(component.queue.length).toBe(1);
      expect(component.queue[0].videoId).toBe('dQw4w9WgXcQ');
      expect(component.queue[0].type).toBe('video');
      expect(component.queue[0].resolution).toBe('1080');
      // processQueue() picks up the item immediately and flips it to 'downloading'
      expect(component.queue[0].status).toBe('downloading');
      expect(component.showQueue).toBeTrue();
    });

    it('queues a music download without a resolution', () => {
      component.videoUrl = 'https://youtu.be/dQw4w9WgXcQ';

      component.addToQueue('music');

      expect(component.queue.length).toBe(1);
      expect(component.queue[0].type).toBe('music');
      expect(component.queue[0].resolution).toBeUndefined();
    });
  });

  it('exposes isLoading based on whether any queue item is downloading', () => {
    expect(component.isLoading).toBeFalse();

    component.queue = [{ id: '1', videoId: 'abc', title: 'abc', type: 'video', status: 'downloading', progress: 10 } as any];

    expect(component.isLoading).toBeTrue();
  });
});
