import { of, Subject } from 'rxjs';
import { ModernStateService } from './modern-state.service';

describe('ModernStateService library cache', () => {
  it('invalidates the selected path before subscribers request its new overview', () => {
    const original = localStorage.getItem('modern_path_id');
    try {
      const music: any = { getLibraryOverview: jasmine.createSpy().and.returnValues(
        of({ tracks: [{ path: 'old.mp3' }], indexing: false }),
        of({ tracks: [{ path: 'new.mp3' }], indexing: false })
      ) };
      const state = new ModernStateService({ getPaths: () => of([]) } as any, music);
      state.getOverview(1, 400).subscribe();
      let path = '';
      const sub = state.pathId$.subscribe(id => {
        if (id !== null) state.getOverview(id, 400).subscribe(result => path = result.tracks[0].path);
      });
      state.selectPath(1);
      expect(path).toBe('new.mp3');
      expect(music.getLibraryOverview).toHaveBeenCalledTimes(2);
      sub.unsubscribe();
    } finally {
      if (original === null) localStorage.removeItem('modern_path_id');
      else localStorage.setItem('modern_path_id', original);
    }
  });

  it('shares a full paginated library but never caches an unfinished index for two minutes', () => {
    const partial = new Subject<any>();
    const complete = new Subject<any>();
    const music: any = { getFullLibraryOverview: jasmine.createSpy().and.returnValues(partial, complete) };
    const state = new ModernStateService({ getPaths: () => of([]) } as any, music);
    const first = state.getOverview(1);
    expect(state.getOverview(1)).toBe(first);
    first.subscribe();
    partial.next({ tracks: [{ path: 'song.mp3' }], indexing: true });
    partial.complete();
    const second = state.getOverview(1);
    expect(second).not.toBe(first);
    second.subscribe();
    complete.next({ tracks: [{ path: 'song.mp3' }], indexing: false });
    complete.complete();
    expect(state.getOverview(1)).toBe(second);
    expect(music.getFullLibraryOverview).toHaveBeenCalledTimes(2);
  });

  it('keeps a bounded home prefetch separate from the complete library', () => {
    const music: any = {
      getFullLibraryOverview: jasmine.createSpy().and.returnValue(of({ tracks: [], indexing: false })),
      getLibraryOverview: jasmine.createSpy().and.returnValue(of({ tracks: [], indexing: false }))
    };
    const state = new ModernStateService({ getPaths: () => of([]) } as any, music);
    state.getOverview(1, 400).subscribe();
    state.getOverview(1).subscribe();
    expect(music.getLibraryOverview).toHaveBeenCalledOnceWith(1, 400);
    expect(music.getFullLibraryOverview).toHaveBeenCalledOnceWith(1);
  });
});
