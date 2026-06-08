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
});
