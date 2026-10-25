import { fakeAsync, tick } from '@angular/core/testing';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { ModernLibraryComponent } from './modern-library.component';

describe('ModernLibraryComponent indexing', () => {
  it('preserves the visible list, search and sorting while an index refresh is pending', fakeAsync(() => {
    const refresh = new Subject<any>();
    const state: any = {
      pathId$: new BehaviorSubject(1),
      getOverview: jasmine.createSpy().and.returnValues(
        of({ tracks: [{ path: 'first.mp3', title: 'First' }], indexing: true }), refresh
      )
    };
    const component = new ModernLibraryComponent({ getPlaylists: () => of([]) } as any, state, {} as any);
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
    const component = new ModernLibraryComponent({ getPlaylists: () => of([]) } as any, state, {} as any);
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
    const component = new ModernLibraryComponent({ getPlaylists: () => of([]) } as any, state, {} as any);
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
