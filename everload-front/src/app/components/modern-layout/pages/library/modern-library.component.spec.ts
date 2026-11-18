import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { CdkVirtualScrollViewport, ScrollingModule } from '@angular/cdk/scrolling';
import { By } from '@angular/platform-browser';
import { MusicService } from '../../../../services/music.service';
import { NasService } from '../../../../services/nas.service';
import { AuthService } from '../../../../services/auth.service';
import { ModernStateService } from '../../modern-state.service';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { ModernLibraryComponent } from './modern-library.component';

describe('ModernLibraryComponent real virtual scrolling', () => {
  it('restores the full visible range after leaving and returning without a window resize', async () => {
    await TestBed.configureTestingModule({
      declarations: [ModernLibraryComponent],
      imports: [CommonModule, ScrollingModule],
      providers: [
        { provide: MusicService, useValue: { getPlaylists: () => of([]) } },
        { provide: NasService, useValue: {} },
        { provide: AuthService, useValue: {} },
        { provide: ModernStateService, useValue: {
          pathId$: new BehaviorSubject(1),
          getOverview: () => of({
            tracks: Array.from({ length: 4895 }, (_, i) => ({ path: `${i}.mp3`, title: `Track ${i}` })),
            indexing: false
          })
        } }
      ]
    }).overrideComponent(ModernLibraryComponent, { set: { template: `
      <div class="ml-page"><div class="ml-track-list" *ngIf="!loading">
        <cdk-virtual-scroll-viewport itemSize="56" class="ml-virtual-list">
          <div class="ml-track-row" *cdkVirtualFor="let t of tracks">{{t.title}}</div>
        </cdk-virtual-scroll-viewport>
      </div></div>` } }).compileComponents();
    const fixture = TestBed.createComponent(ModernLibraryComponent);
    const host = fixture.nativeElement as HTMLElement;
    // Keep the measured viewport independent of other fixtures' document layout.
    host.style.position = 'fixed';
    host.style.inset = '0 auto auto 0';
    host.style.width = '800px';
    host.style.height = '600px';
    document.body.appendChild(host);
    const settle = () => new Promise(resolve => setTimeout(resolve, 160));
    try {
      fixture.detectChanges();
      await settle();
      const viewport = fixture.debugElement.query(By.directive(CdkVirtualScrollViewport))
        .componentInstance as CdkVirtualScrollViewport;
      viewport.scrollToIndex(43);
      await settle();
      const offset = viewport.measureScrollOffset();
      for (let i = 0; i < 3; i++) {
        host.remove();
        window.dispatchEvent(new Event('resize'));
        await settle();
        document.body.appendChild(host);
        await settle();
        fixture.detectChanges();
        expect(viewport.getViewportSize()).toBeGreaterThan(500);
        const range = viewport.getRenderedRange();
        expect(range.end - range.start).toBeGreaterThan(8);
        expect(viewport.measureScrollOffset()).toBe(offset);
      }
    } finally {
      fixture.destroy();
      host.remove();
    }
  });
});

describe('ModernLibraryComponent viewport restoration', () => {
  let component: ModernLibraryComponent;
  let element: HTMLDivElement;
  let resized: () => void;
  let observer: jasmine.SpyObj<ResizeObserver>;
  let viewport: any;

  beforeEach(() => {
    observer = jasmine.createSpyObj('ResizeObserver', ['observe', 'disconnect']);
    spyOn(window, 'ResizeObserver').and.callFake(function(callback: ResizeObserverCallback) {
      resized = () => callback([], observer);
      return observer;
    });
    component = new ModernLibraryComponent({} as any, {} as any, {} as any, {} as any);
    element = document.createElement('div');
    document.body.appendChild(element);
    viewport = { elementRef: { nativeElement: element }, checkViewportSize: jasmine.createSpy() };
  });

  afterEach(() => {
    component.ngOnDestroy();
    element.remove();
  });

  it('remeasures a reused viewport when its visible height returns', fakeAsync(() => {
    const height = spyOnProperty(element, 'clientHeight', 'get').and.returnValue(0);
    component.libraryViewport = viewport;
    resized();
    tick(20);
    expect(viewport.checkViewportSize).not.toHaveBeenCalled();
    height.and.returnValue(600);
    resized();
    tick(20);
    expect(viewport.checkViewportSize).toHaveBeenCalledTimes(1);
    expect(observer.observe).toHaveBeenCalledWith(element);
  }));

  it('does not measure detached routes and coalesces resize notifications', fakeAsync(() => {
    spyOnProperty(element, 'clientHeight', 'get').and.returnValue(600);
    component.libraryViewport = viewport;
    resized();
    element.remove();
    tick(20);
    expect(viewport.checkViewportSize).not.toHaveBeenCalled();
    document.body.appendChild(element);
    resized();
    resized();
    tick(20);
    expect(viewport.checkViewportSize).toHaveBeenCalledTimes(1);
  }));

  it('disconnects and cancels pending work when loading removes the viewport', fakeAsync(() => {
    spyOnProperty(element, 'clientHeight', 'get').and.returnValue(600);
    component.libraryViewport = viewport;
    resized();
    component.libraryViewport = undefined;
    tick(20);
    expect(observer.disconnect).toHaveBeenCalled();
    expect(viewport.checkViewportSize).not.toHaveBeenCalled();
  }));

  it('disconnects and cancels pending work when destroyed', fakeAsync(() => {
    component.libraryViewport = viewport;
    resized();
    component.ngOnDestroy();
    tick(20);
    expect(observer.disconnect).toHaveBeenCalled();
    expect(viewport.checkViewportSize).not.toHaveBeenCalled();
  }));
});

describe('ModernLibraryComponent indexing', () => {
  it('blocks direct access to bulk metadata editing without NAS permissions', () => {
    const nas = jasmine.createSpyObj('NasService', ['updateMetadata']);
    const component = new ModernLibraryComponent({} as any, {} as any, nas, { canManageNas: () => false } as any);
    component.pathId = 1;
    component.selectedPaths.add('song.mp3');
    (component as any).allTracks = [{ path: 'song.mp3' }];
    component.bulkFields.artist = true;
    component.bulkValues.artist = 'Artist';
    component.openMetadataEditor();
    component.saveBulkMetadata();
    expect(component.metadataEditorOpen).toBeFalse();
    expect(nas.updateMetadata).not.toHaveBeenCalled();
  });
  it('preserves the visible list, search and sorting while an index refresh is pending', fakeAsync(() => {
    const refresh = new Subject<any>();
    const state: any = {
      pathId$: new BehaviorSubject(1),
      getOverview: jasmine.createSpy().and.returnValues(
        of({ tracks: [{ path: 'first.mp3', title: 'First' }], indexing: true }), refresh
      )
    };
    const component = new ModernLibraryComponent({ getPlaylists: () => of([]) } as any, state, {} as any, {} as any);
    component.ngOnInit();
    component.query = 'First';
    component.sortCol = 'title';
    component.sortDir = 'desc';
    tick(6000);
    expect(component.loading).toBeFalse();
    expect(component.tracks[0].path).toBe('first.mp3');
    refresh.next({ tracks: [{ path: 'first.mp3', title: 'First' }, { path: 'second.mp3', title: 'Second' }], indexing: false });
    refresh.complete();
    expect(component.query).toBe('First');
    expect(component.sortDir).toBe('desc');
    expect(component.tracks.map(track => track.path)).toEqual(['first.mp3']);
    component.ngOnDestroy();
  }));

  it('refreshes a nonempty partial library and stops polling when indexing finishes', fakeAsync(() => {
    const state: any = {
      pathId$: new BehaviorSubject(1),
      getOverview: jasmine.createSpy().and.returnValues(
        of({ tracks: [{ path: 'first.mp3' }], indexing: true }),
        of({ tracks: [{ path: 'first.mp3' }, { path: 'second.mp3' }], indexing: false })
      )
    };
    const component = new ModernLibraryComponent({ getPlaylists: () => of([]) } as any, state, {} as any, {} as any);
    component.ngOnInit();
    expect(component.tracks.length).toBe(1);
    tick(6000);
    expect(component.tracks.length).toBe(2);
    tick(12000);
    expect(state.getOverview).toHaveBeenCalledTimes(2);
    component.ngOnDestroy();
  }));

  it('cancels requests and polling when changing NAS paths or destroying the view', fakeAsync(() => {
    const first = new Subject<any>();
    const second = new Subject<any>();
    const paths = new BehaviorSubject<number | null>(1);
    const state: any = { pathId$: paths, getOverview: jasmine.createSpy().and.returnValues(first, second) };
    const component = new ModernLibraryComponent({ getPlaylists: () => of([]) } as any, state, {} as any, {} as any);
    component.ngOnInit();
    paths.next(2);
    first.next({ tracks: [{ path: 'old.mp3' }], indexing: true });
    expect(component.tracks.length).toBe(0);
    second.next({ tracks: [{ path: 'new.mp3' }], indexing: true });
    component.ngOnDestroy();
    tick(12000);
    expect(component.tracks[0].path).toBe('new.mp3');
    expect(state.getOverview).toHaveBeenCalledTimes(2);
  }));
});
