import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA, Pipe, PipeTransform } from '@angular/core';
import { of } from 'rxjs';

import { AdminConfigComponent } from './admin-config.component';
import { TranslateService } from '@ngx-translate/core';
import { NasService } from '../../services/nas.service';
import { ApiBaseService } from '../../services/api-base.service';
import { AuthService } from '../../services/auth.service';
import { AndroidReleaseService } from '../../services/android-release.service';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

const BACKEND = 'http://test-backend';

describe('AdminConfigComponent', () => {
  let component: AdminConfigComponent;
  let fixture: ComponentFixture<AdminConfigComponent>;
  let httpMock: HttpTestingController;
  let translateSpy: jasmine.SpyObj<TranslateService>;

  beforeEach(async () => {
    translateSpy = jasmine.createSpyObj<TranslateService>('TranslateService', ['setDefaultLang', 'use', 'instant']);
    translateSpy.instant.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, HttpClientTestingModule],
      declarations: [AdminConfigComponent, FakeTranslatePipe],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: TranslateService, useValue: translateSpy },
        { provide: NasService, useValue: jasmine.createSpyObj<NasService>('NasService', ['getPaths']) },
        { provide: ApiBaseService, useValue: { backendUrl: BACKEND, isNativePlatform: () => false, withBackend: (s: string) => s } },
        { provide: AuthService, useValue: jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin', 'hasNasAccess']) },
        { provide: AndroidReleaseService, useValue: jasmine.createSpyObj<AndroidReleaseService>('AndroidReleaseService', ['getRelease', 'downloadApk', 'uploadRelease', 'deleteRelease']) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminConfigComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne(`${BACKEND}/api/admin/dashboard`).flush({ downloadsByDay: [], recentDownloads: [] });
  });

  afterEach(() => httpMock.verify());

  function flushConfigRequest() {
    httpMock.expectOne(`${BACKEND}/api/admin/config`).flush({
      clientId: '', clientSecret: '', apiKey: '', acoustidApiKey: '', githubToken: '', authHeroImages: ''
    });
  }

  it('should create', () => {
    flushConfigRequest();
    expect(component).toBeTruthy();
  });

  it('loads the admin config on init and merges it into the local state', () => {
    httpMock.expectOne(`${BACKEND}/api/admin/config`).flush({
      clientId: 'cid', clientSecret: 'secret', apiKey: 'key', acoustidApiKey: 'acoustid', githubToken: 'gh', authHeroImages: 'img1\nimg2'
    });

    expect(component.config.clientId).toBe('cid');
    expect(component.config.authHeroImages).toBe('img1\nimg2');
  });

  it('falls back to the default hero images when none are configured', () => {
    httpMock.expectOne(`${BACKEND}/api/admin/config`).flush({
      clientId: '', clientSecret: '', apiKey: '', acoustidApiKey: '', githubToken: '', authHeroImages: ''
    });

    expect(component.config.authHeroImages).toBe(component.defaultAuthHeroImages.join('\n'));
  });

  it('shows a translated error message when loading the config fails', () => {
    httpMock.expectOne(`${BACKEND}/api/admin/config`).error(new ProgressEvent('error'));

    expect(component.mensaje).toBe('❌ ADMIN.FORM_LOAD_ERROR');
  });

  it('persists the chosen language preference', () => {
    flushConfigRequest();
    spyOn(localStorage, 'setItem');

    component.changeLanguage('en');

    expect(translateSpy.use).toHaveBeenCalledWith('en');
    expect(localStorage.setItem).toHaveBeenCalledWith('language', 'en');
  });

  it('loads dashboard metrics on init and records the refresh time', () => {
    flushConfigRequest();

    expect(component.dashboard).toEqual({ downloadsByDay: [], recentDownloads: [] } as any);
    expect(component.dashboardUpdatedAt).toEqual(jasmine.any(Date));
    expect(component.dashboardLoading).toBeFalse();
  });

  it('exposes retry after the dashboard request fails and can load again', () => {
    flushConfigRequest();
    component.loadDashboard();
    httpMock.expectOne(`${BACKEND}/api/admin/dashboard`).error(new ProgressEvent('error'));

    expect(component.dashboardError).toBeTrue();
    expect(component.dashboardLoading).toBeFalse();

    component.loadDashboard();
    httpMock.expectOne(`${BACKEND}/api/admin/dashboard`).flush({ downloadsByDay: [], recentDownloads: [] });
    expect(component.dashboardError).toBeFalse();
  });

  it('does not start duplicate dashboard requests while one is in flight', () => {
    flushConfigRequest();
    component.dashboardLoading = true;

    component.loadDashboard();

    expect(component.dashboardLoading).toBeTrue();
    httpMock.expectNone(`${BACKEND}/api/admin/dashboard`);
  });

  it('normalizes chart heights and formats storage values safely', () => {
    flushConfigRequest();
    component.dashboard = {
      totalUsers: 0, activeUsers: 0, pendingUsers: 0, totalDownloads: 0,
      downloadsToday: 0, downloadsLast7Days: 0, databaseSizeBytes: 0,
      volumeTotalBytes: 1000, volumeUsableBytes: 250, processCpuPercent: null,
      jvmHeapUsedBytes: 0, jvmHeapMaxBytes: 0, jvmHeapPercent: 0,
      downloadsByDay: [{ day: '2026-10-10', count: 10 }, { day: '2026-10-11', count: 0 }],
      recentDownloads: []
    };

    expect(component.dashboardBarHeight(10)).toBe(100);
    expect(component.dashboardBarHeight(0)).toBe(2);
    expect(component.dashboardVolumePercent).toBe(75);
    expect(component.formatDashboardBytes(0)).toBe('0 B');
    expect(component.formatDashboardBytes(-1)).toBe('0 B');
    expect(component.formatDashboardBytes(1024)).toBe('1 KB');
    expect(component.formatDashboardBytes(1024 * 1024)).toBe('1.0 MB');
    expect(component.dashboardDayLabel('2026-10-10')).toBe(
      new Date(2026, 9, 10).toLocaleDateString(undefined, { weekday: 'short' })
    );
  });

  it('clamps volume percentage and handles empty chart data', () => {
    flushConfigRequest();
    component.dashboard = {
      totalUsers: 0, activeUsers: 0, pendingUsers: 0, totalDownloads: 0,
      downloadsToday: 0, downloadsLast7Days: 0, databaseSizeBytes: 0,
      volumeTotalBytes: 100, volumeUsableBytes: 150, processCpuPercent: null,
      jvmHeapUsedBytes: 0, jvmHeapMaxBytes: 0, jvmHeapPercent: 0,
      downloadsByDay: [], recentDownloads: []
    };

    expect(component.dashboardVolumePercent).toBe(0);
    expect(component.dashboardBarHeight(12)).toBe(100);

    component.dashboard.volumeUsableBytes = -50;
    expect(component.dashboardVolumePercent).toBe(100);
  });

  it('loads only rejected accounts in the inactive users list', () => {
    flushConfigRequest();
    component.loadInactiveUsers();
    httpMock.expectOne(`${BACKEND}/api/admin/users`).flush([
      { id: 1, username: 'active', role: 'BASIC_USER', status: 'ACTIVE' },
      { id: 2, username: 'pending', role: 'BASIC_USER', status: 'PENDING' },
      { id: 3, username: 'inactive', role: 'NAS_USER', status: 'REJECTED' }
    ]);

    expect(component.inactiveUsers.map(user => user.username)).toEqual(['inactive']);
  });

  it('reactivates an inactive user with the existing role and reloads user lists', () => {
    flushConfigRequest();
    const user = { id: 3, username: 'inactive', role: 'NAS_USER', status: 'REJECTED' } as any;

    component.reactivateUser(user);

    const update = httpMock.expectOne(`${BACKEND}/api/admin/users/3`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ role: 'NAS_USER', status: 'ACTIVE' });
    update.flush({});

    httpMock.expectOne(`${BACKEND}/api/admin/users/pending`).flush([]);
    httpMock.expectOne(`${BACKEND}/api/admin/users/active`).flush([]);
    httpMock.expectOne(`${BACKEND}/api/admin/users`).flush([]);
    expect(component.userMsg).toBe('ADMIN.USER_REACTIVATED');
  });

  it('clears a group chat history without loading message content', () => {
    flushConfigRequest();
    spyOn(window, 'confirm').and.returnValue(true);
    const group = { id: 8, name: 'Community', type: 'GROUP', createdAt: '', memberCount: 4, messageCount: 20 };

    component.clearChatGroupMessages(group);

    const request = httpMock.expectOne(`${BACKEND}/api/admin/chat/groups/8/messages`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    expect(group.messageCount).toBe(0);
    expect(component.chatMsg).toContain('ADMIN.CHAT_GROUP_CLEARED');
    httpMock.expectNone(`${BACKEND}/api/admin/chat/groups/8/messages?includeContent=true`);
  });
});
