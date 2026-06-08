import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { NO_ERRORS_SCHEMA, Pipe, PipeTransform } from '@angular/core';
import { of } from 'rxjs';

import { HomeComponent } from './home.component';
import { TranslateService } from '@ngx-translate/core';
import { AuthService, AuthResponse } from '../../services/auth.service';
import { ChatService } from '../../services/chat.service';
import { MusicService } from '../../services/music.service';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let chatSpy: jasmine.SpyObj<ChatService>;
  let musicSpy: jasmine.SpyObj<MusicService>;

  const USER: AuthResponse = {
    token: 'test-token', username: 'brais', email: 'brais@example.com', role: 'BASIC_USER', status: 'ACTIVE'
  };

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['getAvatarUrl', 'isAdmin', 'hasNasAccess'],
      { currentUser$: of(USER) }
    );
    authSpy.getAvatarUrl.and.returnValue(null);
    authSpy.isAdmin.and.returnValue(false);
    authSpy.hasNasAccess.and.returnValue(false);

    chatSpy = jasmine.createSpyObj<ChatService>('ChatService', [], { unreadCount$: of(0) });

    musicSpy = jasmine.createSpyObj<MusicService>('MusicService', ['getRandomTracks']);
    musicSpy.getRandomTracks.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [HomeComponent, FakeTranslatePipe],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: TranslateService, useValue: jasmine.createSpyObj<TranslateService>('TranslateService', ['use', 'instant']) },
        { provide: AuthService, useValue: authSpy },
        { provide: ChatService, useValue: chatSpy },
        { provide: MusicService, useValue: musicSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('subscribes to the current user and loads their avatar URL', () => {
    expect(component.currentUser).toEqual(USER);
    expect(authSpy.getAvatarUrl).toHaveBeenCalled();
  });

  it('tracks unread chat count via ChatService', () => {
    expect(component.unreadCount).toBe(0);
  });

  it('does not load random tracks when the user lacks NAS access', () => {
    expect(musicSpy.getRandomTracks).not.toHaveBeenCalled();
  });

  it('toggles and closes the side menu', () => {
    expect(component.menuOpen).toBeFalse();

    component.toggleMenu();
    expect(component.menuOpen).toBeTrue();

    component.closeMenu();
    expect(component.menuOpen).toBeFalse();
  });
});
