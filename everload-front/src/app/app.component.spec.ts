import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, Subject } from 'rxjs';

import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';
import { ChatService } from './services/chat.service';
import { MusicService } from './services/music.service';
import { NotificationService } from './services/notification.service';
import { MaintenanceService } from './services/maintenance.service';
import { PwaUpdateService } from './services/pwa-update.service';
import { ApiBaseService } from './services/api-base.service';
import { Router, NavigationEnd } from '@angular/router';
import { PlayerPreferencesService } from './services/player-preferences.service';

describe('AppComponent', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let chatSpy: jasmine.SpyObj<ChatService>;
  let maintenanceSpy: jasmine.SpyObj<MaintenanceService>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin'], { currentUser$: of(null) });
    authSpy.isAdmin.and.returnValue(false);

    chatSpy = jasmine.createSpyObj<ChatService>(
      'ChatService',
      ['startGlobalPolling', 'stopGlobalPolling'],
      { newMessageAlert$: of() }
    );

    maintenanceSpy = jasmine.createSpyObj<MaintenanceService>(
      'MaintenanceService',
      ['checkInitial'],
      { maintenance$: of({ active: false, message: '' }) }
    );

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, HttpClientTestingModule],
      declarations: [AppComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: ChatService, useValue: chatSpy },
        {
          provide: MusicService,
          useValue: { mainPlayer: { state: { currentTrack: null } }, globalPlayerHidden: false,
            playerOpenRequested$: new Subject<void>(), nowPlayingPanelOpen: false }
        },
        { provide: NotificationService, useValue: jasmine.createSpyObj<NotificationService>('NotificationService', ['showToast']) },
        { provide: MaintenanceService, useValue: maintenanceSpy },
        { provide: PwaUpdateService, useValue: jasmine.createSpyObj<PwaUpdateService>('PwaUpdateService', ['init']) },
        {
          provide: ApiBaseService,
          useValue: { isNativePlatform: () => false, withBackend: (s: string) => s, backendUrl: '' }
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('uses the island only in the standalone chat', () => {
    const app = TestBed.createComponent(AppComponent).componentInstance;
    const events = TestBed.inject(Router).events as Subject<any>;
    events.next(new NavigationEnd(1, '/chat?group=2', '/chat?group=2'));
    expect(app.playerMode).toBe('island');
    events.next(new NavigationEnd(2, '/modern', '/modern'));
    expect(app.playerMode).toBe('hidden');
    events.next(new NavigationEnd(3, '/', '/'));
    expect(app.playerMode).toBe('mini');
  });

  it('floats the island without reserving space above the page', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const music = TestBed.inject(MusicService);
    music.mainPlayer.state.currentTrack = { path: 'test.mp3', name: 'Test' } as any;
    fixture.componentInstance.currentUrl = '/youtube-downloads';
    TestBed.inject(PlayerPreferencesService).accept('island');
    fixture.detectChanges();
    const layout = fixture.nativeElement.querySelector('.main-content-layout') as HTMLElement;
    expect(getComputedStyle(layout).paddingTop).toBe('0px');
    expect(getComputedStyle(layout).getPropertyValue('--floating-player-clearance').trim()).toBe('0px');
    TestBed.inject(PlayerPreferencesService).accept('bar');
    fixture.detectChanges();
    expect(getComputedStyle(layout).getPropertyValue('--floating-player-clearance').trim()).toBe('0px');
    fixture.destroy();
  });

  it('opens the classic player from a notification without opening it over Modern', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const music = TestBed.inject(MusicService);
    music.playerOpenRequested$.next();
    expect(music.nowPlayingPanelOpen).toBeTrue();
    music.nowPlayingPanelOpen = false;
    fixture.componentInstance.currentUrl = '/modern/library';
    music.playerOpenRequested$.next();
    expect(music.nowPlayingPanelOpen).toBeFalse();
    fixture.destroy();
    fixture.componentInstance.currentUrl = '/';
    music.playerOpenRequested$.next();
    expect(music.nowPlayingPanelOpen).toBeFalse();
  });

  it(`should have as title 'everload-front'`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('everload-front');
  });

  it('hides the maintenance overlay when maintenance mode is inactive', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.maintenance-overlay')).toBeNull();
  });

  it('remounts user views after maintenance and leaves admin access available', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    fixture.componentInstance.maintenanceState = { active: true, message: 'Working' };
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.main-content-layout')).toBeNull();
    authSpy.isAdmin.and.returnValue(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.main-content-layout')).not.toBeNull();
    authSpy.isAdmin.and.returnValue(false);
    fixture.componentInstance.maintenanceState = { active: false, message: '' };
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.main-content-layout')).not.toBeNull();
  });

  it('starts global chat polling and stops it again as the auth state changes', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(chatSpy.stopGlobalPolling).toHaveBeenCalled();
    expect(maintenanceSpy.checkInitial).toHaveBeenCalled();
  });
});
