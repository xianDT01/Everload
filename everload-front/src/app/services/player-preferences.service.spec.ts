import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiBaseService } from './api-base.service';
import { floatingPlayerMode, PlayerPreferencesService } from './player-preferences.service';

describe('PlayerPreferencesService', () => {
  let service: PlayerPreferencesService;
  let http: HttpTestingController;
  let users: BehaviorSubject<any>;
  let auth: any;
  beforeEach(() => {
    users = new BehaviorSubject({ username: 'demo' });
    auth = { currentUser$: users.asObservable(), getCurrentUser: () => users.value, updateToken: jasmine.createSpy() };
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule], providers: [
      { provide: AuthService, useValue: auth }, { provide: ApiBaseService, useValue: { withBackend: (url: string) => url } }
    ] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PlayerPreferencesService);
  });
  afterEach(() => { http.verify(); service.ngOnDestroy(); });

  it('loads the account preference and does not reload on unrelated auth updates', () => {
    http.expectOne('/api/user/profile').flush({ floatingPlayerStyle: 'island' });
    expect(service.style$.value).toBe('island');
    users.next({ username: 'demo', avatarUrl: '/new.jpg' });
    http.expectNone('/api/user/profile');
  });

  it('applies a preference only after the save succeeds', () => {
    http.expectOne('/api/user/profile').flush({ floatingPlayerStyle: 'bar' });
    service.save('island').subscribe();
    expect(service.style$.value).toBe('bar');
    const request = http.expectOne('/api/user/profile');
    expect(request.request.body).toEqual({ floatingPlayerStyle: 'island' });
    request.flush({ user: { floatingPlayerStyle: 'island' }, newToken: 'test-token' });
    expect(service.style$.value).toBe('island');
    expect(auth.updateToken).toHaveBeenCalledWith('test-token');
  });

  it('keeps the last saved preference if saving fails', () => {
    http.expectOne('/api/user/profile').flush({ floatingPlayerStyle: 'bar' });
    service.save('island').subscribe({ error: () => {} });
    http.expectOne('/api/user/profile').flush({}, { status: 500, statusText: 'Failed' });
    expect(service.style$.value).toBe('bar');
  });

  it('cancels old-account loads and resets on logout', () => {
    const first = http.expectOne('/api/user/profile');
    users.next({ username: 'second' });
    expect(first.cancelled).toBeTrue();
    expect(service.style$.value).toBeNull();
    http.expectOne('/api/user/profile').flush({ floatingPlayerStyle: 'bar' });
    users.next(null);
    expect(service.style$.value).toBeNull();
  });

  it('does not apply an old-account save after logout', () => {
    http.expectOne('/api/user/profile').flush({ floatingPlayerStyle: 'bar' });
    service.save('island').subscribe();
    const request = http.expectOne('/api/user/profile');
    users.next(null);
    request.flush({ user: { floatingPlayerStyle: 'island' }, newToken: 'test-token' });
    expect(service.style$.value).toBeNull();
    expect(auth.updateToken).not.toHaveBeenCalled();
  });

  it('does not let a stale startup response replace a newer profile preference', () => {
    const request = http.expectOne('/api/user/profile');
    service.accept('island');
    request.flush({ floatingPlayerStyle: 'bar' });
    expect(service.style$.value).toBe('island');
  });

  it('falls back safely for unknown styles or a failed profile request', () => {
    http.expectOne('/api/user/profile').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(service.style$.value).toBeNull();
    service.accept('unexpected');
    expect(service.style$.value).toBeNull();
  });
});

describe('floatingPlayerMode', () => {
  it('preserves existing defaults until a user makes a choice', () => {
    expect(floatingPlayerMode('/chat?group=1', null)).toBe('island');
    expect(floatingPlayerMode('/profile', null)).toBe('mini');
    expect(floatingPlayerMode('/audio-tools', null)).toBe('mini');
  });
  it('applies both choices across ordinary pages', () => {
    for (const url of ['/chat', '/profile', '/', '/youtube-downloads', '/audio-tools', '/admin-config']) {
      expect(floatingPlayerMode(url, 'island')).toBe('island');
      expect(floatingPlayerMode(url, 'bar')).toBe('mini');
    }
  });
  it('does not replace Modern, the DJ decks or the full NAS player', () => {
    expect(floatingPlayerMode('/modern/library', 'island')).toBe('hidden');
    expect(floatingPlayerMode('/nas-music?mode=deck', 'island')).toBe('hidden');
    expect(floatingPlayerMode('/nas-music', 'island')).toBe('full');
  });
});
