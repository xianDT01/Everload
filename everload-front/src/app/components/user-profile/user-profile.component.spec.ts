import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { UserProfileComponent } from './user-profile.component';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';
import { PlayerPreferencesService } from '../../services/player-preferences.service';
import { MusicService } from '../../services/music.service';

describe('UserProfileComponent', () => {
  let http: HttpTestingController;
  let preferences: any;
  let auth: any;
  const profile = { id: 1, username: 'demo', email: 'demo@example.test', role: 'NAS_USER', status: 'ACTIVE', floatingPlayerStyle: 'bar', showLastSeen: true };
  beforeEach(async () => {
    preferences = { style$: new BehaviorSubject(null), accept: jasmine.createSpy(), save: jasmine.createSpy() };
    preferences.accept.and.callFake((style: any) => preferences.style$.next(style));
    auth = { getCurrentUser: () => profile, updateStoredUser: jasmine.createSpy(), updateToken: jasmine.createSpy(),
      uploadAvatar: jasmine.createSpy().and.returnValue(of({ avatarUrl: '/avatar.png' })), removeAvatar: jasmine.createSpy().and.returnValue(of({})) };
    await TestBed.configureTestingModule({
      declarations: [UserProfileComponent], imports: [FormsModule, MatIconModule, HttpClientTestingModule, RouterTestingModule, TranslateModule.forRoot()],
      providers: [{ provide: AuthService, useValue: auth }, { provide: ApiBaseService, useValue: { backendUrl: '' } },
        { provide: PlayerPreferencesService, useValue: preferences },
        { provide: MusicService, useValue: { mainPlayer: { state$: of({ currentTrack: null, volume: 1, duration: 0, currentTime: 0 }) } } }]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function create() {
    const fixture = TestBed.createComponent(UserProfileComponent);
    fixture.detectChanges(); http.expectOne('/api/user/profile').flush(profile); fixture.detectChanges();
    return fixture;
  }
  it('opens the approved playback design with the account preference selected', async () => {
    const fixture = create();
    await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.componentInstance.activeSection).toBe('reproduccion');
    expect(fixture.componentInstance.playerStyle).toBe('bar');
    expect(fixture.nativeElement.querySelectorAll('.player-option').length).toBe(2);
    expect(fixture.nativeElement.querySelector('input[value="bar"]').checked).toBeTrue();
    fixture.destroy();
  });
  it('changes the preview without applying a draft and can cancel it', () => {
    const fixture = create(); const component = fixture.componentInstance;
    component.playerStyle = 'island'; fixture.detectChanges();
    expect(preferences.style$.value).toBe('bar');
    expect(fixture.nativeElement.querySelector('.bar-preview')).toBeNull();
    component.cancelPlayback();
    expect(component.playerStyle).toBe('bar');
    fixture.destroy();
  });
  it('guards duplicate saves and shows a save failure without changing the preference', () => {
    const fixture = create(); const component = fixture.componentInstance;
    const save = new Subject<any>(); preferences.save.and.returnValue(save);
    component.playerStyle = 'island'; component.savePlayback(); component.savePlayback();
    expect(preferences.save).toHaveBeenCalledOnceWith('island');
    save.error(new Error('Unavailable')); fixture.detectChanges();
    expect(component.savingPlayback).toBeFalse(); expect(preferences.style$.value).toBe('bar');
    expect(fixture.nativeElement.querySelector('.msg-error')).not.toBeNull();
    fixture.destroy();
  });
  it('retains account editing and token renewal', () => {
    const fixture = create(); const component = fixture.componentInstance;
    component.scrollTo('datos'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[autocomplete="email"]')).not.toBeNull();
    component.editUsername = 'newdemo'; component.saveProfile();
    const request = http.expectOne('/api/user/profile');
    expect(request.request.body).toEqual({ username: 'newdemo', email: 'demo@example.test' });
    request.flush({ user: { ...profile, username: 'newdemo' }, newToken: 'test-token' });
    expect(auth.updateToken).toHaveBeenCalledWith('test-token'); fixture.destroy();
  });
  it('keeps privacy errors separate from success messages', () => {
    const fixture = create(); const component = fixture.componentInstance;
    component.scrollTo('privacidad'); component.showLastSeen = false; component.savePrivacy();
    const request = http.expectOne('/api/user/profile'); expect(request.request.body).toEqual({ showLastSeen: false });
    request.flush({}, { status: 500, statusText: 'Failed' }); fixture.detectChanges();
    expect(component.privacyError).toBeTruthy(); expect(component.privacyMsg).toBe(''); fixture.destroy();
  });
  it('keeps password changes and avatar uploads available', () => {
    const fixture = create(); const component = fixture.componentInstance;
    component.scrollTo('seguridad'); component.currentPassword = 'old-test'; component.newPassword = component.confirmPassword = 'new-test';
    component.changePassword(); http.expectOne('/api/user/profile/password').flush({});
    expect(component.currentPassword).toBe('');
    component.onAvatarFileSelected({ target: { files: [new File(['test'], 'photo.png', { type: 'image/png' })], value: 'photo.png' } } as any);
    expect(component.profile?.avatarUrl).toBe('/avatar.png'); fixture.destroy();
  });
});
