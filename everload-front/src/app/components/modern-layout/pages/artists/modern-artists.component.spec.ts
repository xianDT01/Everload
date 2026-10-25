import { fakeAsync, tick } from '@angular/core/testing';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { ModernArtistsComponent } from './modern-artists.component';

describe('ModernArtistsComponent indexing', () => {
  it('keeps artists visible during refresh and stops polling after indexing completes', fakeAsync(() => {
    const refresh = new Subject<any>();
    const track = { path: 'song.mp3', title: 'Song', artist: 'Artist', album: 'Album' };
    const state: any = {
      pathId$: new BehaviorSubject(1), selectedArtistName$: of(''),
      getOverview: jasmine.createSpy().and.returnValues(of({ tracks: [track], indexing: true }), refresh)
    };
    const component = new ModernArtistsComponent({ getArtistProfiles: () => of([]) } as any, state, {} as any);
    spyOn<any>(component, 'resolveAutoArtistImages').and.stub();
    component.ngOnInit();
    expect(component.artists.length).toBe(1);
    tick(6000);
    expect(component.loading).toBeFalse();
    expect(component.artists[0].artist).toBe('Artist');
    refresh.next({ tracks: [track], indexing: false });
    refresh.complete();
    tick(12000);
    expect(state.getOverview).toHaveBeenCalledTimes(2);
    component.ngOnDestroy();
  }));
});
