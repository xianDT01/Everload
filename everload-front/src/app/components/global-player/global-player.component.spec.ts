import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { GlobalPlayerComponent } from './global-player.component';
import { MusicService, PlayerState, MusicMetadataDto } from '../../services/music.service';

describe('GlobalPlayerComponent island', () => {
  let state: BehaviorSubject<PlayerState>;
  let music: any;
  let router: jasmine.SpyObj<Router>;
  const track = { path: 'song.mp3', title: 'One More Time', artist: 'Daft Punk', source: 'nas' } as MusicMetadataDto;

  beforeEach(async () => {
    localStorage.removeItem('everload.island-position.v1');
    state = new BehaviorSubject<PlayerState>({ currentTrack: track, pathId: 1, playing: true,
      currentTime: 40, duration: 180, volume: 0.8, error: null } as PlayerState);
    music = {
      mainPlayer: { state$: state.asObservable(), togglePlay: jasmine.createSpy(), seek: jasmine.createSpy(), setVolume: jasmine.createSpy() },
      shuffle$: of(false), repeat$: of('none'), checkFavorite: jasmine.createSpy().and.returnValue(of({ isFavorite: false })),
      fetchCoverIfNeeded: jasmine.createSpy(), hasCoverToShow: () => true, getCoverUrlWithCache: () => '/cover.png',
      playNextMain: jasmine.createSpy(), playPrevMain: jasmine.createSpy(), toggleShuffle: jasmine.createSpy(),
      toggleFavorite: jasmine.createSpy().and.returnValue(of({ isFavorite: true }))
    };
    router = jasmine.createSpyObj('Router', ['navigate']);
    await TestBed.configureTestingModule({
      declarations: [GlobalPlayerComponent], imports: [CommonModule, MatIconModule, TranslateModule.forRoot()],
      providers: [{ provide: MusicService, useValue: music }, { provide: Router, useValue: router }]
    }).compileComponents();
  });

  afterEach(() => localStorage.removeItem('everload.island-position.v1'));

  it('moves with the drag handle without expanding or interrupting playback', async () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    const player = fixture.componentInstance;
    player.mode = 'island'; fixture.detectChanges();
    fixture.autoDetectChanges();
    const handle = fixture.nativeElement.querySelector('.island-drag') as HTMLElement;
    spyOn(handle, 'setPointerCapture');
    handle.dispatchEvent(new PointerEvent('pointerdown', { pointerId: 1, button: 0, clientX: 200, clientY: 30, bubbles: true }));
    TestBed.inject(NgZone).runOutsideAngular(() => {
      document.dispatchEvent(new PointerEvent('pointermove', { pointerId: 1, clientX: 260, clientY: 180 }));
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.player-island').style.top).toBe(`${player.islandPosition!.y}px`);
    document.dispatchEvent(new PointerEvent('pointerup', { pointerId: 1 }));
    fixture.detectChanges();
    expect(player.islandPosition).not.toBeNull();
    expect(player.islandExpanded).toBeFalse();
    expect(music.mainPlayer.togglePlay).not.toHaveBeenCalled();
    expect(JSON.parse(localStorage.getItem('everload.island-position.v1')!).y).toBe(player.islandPosition!.y);
    fixture.destroy();
  });

  it('pins the island, blocks movement and restores the saved preference', () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    const player = fixture.componentInstance;
    player.mode = 'island'; fixture.detectChanges();
    player.toggleIslandLock();
    const position = { ...player.islandPosition! };
    player.moveIslandWithKeyboard(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    expect(player.islandPosition).toEqual(position);
    fixture.destroy();
    const restored = TestBed.createComponent(GlobalPlayerComponent);
    restored.componentInstance.mode = 'island'; restored.detectChanges();
    expect(restored.componentInstance.islandLocked).toBeTrue();
    expect(restored.componentInstance.islandPosition).toEqual(position);
    restored.destroy();
  });

  it('keeps expanded and resized positions inside the viewport and can reset them', () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    const player = fixture.componentInstance;
    player.mode = 'island'; fixture.detectChanges();
    player.islandPosition = { x: 99999, y: 99999 };
    player.toggleIsland(); fixture.detectChanges(); player.onIslandViewportResize();
    expect(fixture.nativeElement.querySelector('.player-island').style.top).toBe(`${player.islandPosition!.y}px`);
    expect(player.islandPosition!.x).toBeLessThanOrEqual(window.innerWidth - Math.min(460, window.innerWidth - 32) - 12);
    expect(player.islandPosition!.y).toBeLessThan(window.innerHeight);
    player.toggleIslandLock(); player.resetIslandPosition();
    expect(player.islandPosition).toBeNull();
    expect(player.islandLocked).toBeFalse();
    expect(localStorage.getItem('everload.island-position.v1')).toBeNull();
    fixture.destroy();
  });

  it('allows keyboard movement and ignores malformed saved positions', () => {
    localStorage.setItem('everload.island-position.v1', '{bad json');
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    const player = fixture.componentInstance;
    player.mode = 'island'; fixture.detectChanges();
    expect(player.islandPosition).toBeNull();
    player.moveIslandWithKeyboard(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    expect(player.islandPosition).not.toBeNull();
    expect(player.islandPosition!.x).toBeGreaterThanOrEqual(12);
    fixture.destroy();
  });

  it('expands in place without navigating or restarting playback', () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    fixture.componentInstance.mode = 'island';
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.island-summary').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.island-details')).not.toBeNull();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(music.mainPlayer.togglePlay).not.toHaveBeenCalled();
    fixture.nativeElement.querySelector('.island-play').click();
    expect(music.mainPlayer.togglePlay).toHaveBeenCalledTimes(1);
    fixture.componentInstance.collapseIsland();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.island-details')).toBeNull();
    fixture.destroy();
  });

  it('uses the same transport, seek, volume and favorite controls', () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    const player = fixture.componentInstance;
    player.mode = 'island';
    fixture.detectChanges();
    player.toggleIsland();
    fixture.detectChanges();
    const seek = fixture.nativeElement.querySelector('.island-progress input') as HTMLInputElement;
    seek.value = '90';
    seek.dispatchEvent(new Event('input'));
    const volume = fixture.nativeElement.querySelector('.island-volume input') as HTMLInputElement;
    volume.value = '0.4';
    volume.dispatchEvent(new Event('input'));
    player.next(); player.prev(); player.toggleLike(new Event('click'));
    expect(music.mainPlayer.seek).toHaveBeenCalledOnceWith(90);
    expect(music.mainPlayer.setVolume).toHaveBeenCalledOnceWith(0.4);
    expect(music.playNextMain).toHaveBeenCalledTimes(1);
    expect(music.playPrevMain).toHaveBeenCalledTimes(1);
    expect(player.isLiked).toBeTrue();
    fixture.destroy();
  });

  it('closes on outside click, route mode change and Escape', () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    const player = fixture.componentInstance;
    player.mode = 'island'; player.toggleIsland();
    player.closeIslandOutside({ target: document.body } as unknown as MouseEvent);
    expect(player.islandExpanded).toBeFalse();
    player.toggleIsland(); player.collapseIsland();
    expect(player.islandExpanded).toBeFalse();
    player.toggleIsland(); player.mode = 'mini';
    expect(player.islandExpanded).toBeFalse();
    fixture.destroy();
  });

  it('does not show the island without a track and falls back if artwork fails', () => {
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    fixture.componentInstance.mode = 'island'; fixture.detectChanges();
    fixture.nativeElement.querySelector('.island-art img').dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.island-art mat-icon')).not.toBeNull();
    state.next({ ...state.value, currentTrack: null }); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.player-island')).toBeNull();
    fixture.destroy();
  });

  it('ignores stale favorite lookups when a track changes', () => {
    const oldFavorite = new Subject<{ isFavorite: boolean }>();
    music.checkFavorite.and.returnValues(oldFavorite, of({ isFavorite: false }));
    const fixture = TestBed.createComponent(GlobalPlayerComponent);
    fixture.detectChanges();
    state.next({ ...state.value, currentTrack: { ...track, path: 'other.mp3' } });
    oldFavorite.next({ isFavorite: true });
    expect(fixture.componentInstance.isLiked).toBeFalse();
    fixture.destroy();
  });
});
