import { of, Subject } from 'rxjs';
import { ModernStateService } from './modern-state.service';

describe('ModernStateService library cache', () => {
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
