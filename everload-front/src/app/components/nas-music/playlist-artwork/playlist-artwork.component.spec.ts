import { PlaylistArtworkComponent } from './playlist-artwork.component';

describe('PlaylistArtworkComponent', () => {
  let component: PlaylistArtworkComponent;
  let cover: jasmine.Spy;
  beforeEach(() => {
    cover = jasmine.createSpy().and.callFake((id: number, path: string, source: string) => `${source}/${id}/${path}`);
    component = new PlaylistArtworkComponent({ getCoverUrlWithCache: cover } as any);
    component.pathId = 9;
  });
  it('uses up to four distinct available covers and preserves track source and NAS path', () => {
    component.playlist = { name: 'My Mix', tracks: [
      { trackPath: 'one', nasPathId: 2, source: 'youtube' },
      { trackPath: 'one', nasPathId: 2, source: 'youtube' },
      { trackPath: 'missing', available: false },
      ...['two', 'three', 'four', 'five'].map(trackPath => ({ trackPath }))
    ] };
    component.ngOnChanges();
    expect(component.covers).toEqual(['youtube/2/one', 'nas/9/two', 'nas/9/three', 'nas/9/four']);
    expect(component.initials).toBe('MM');
  });
  it('handles empty playlists and restores a failed image after a successful reload', () => {
    component.ngOnChanges();
    expect(component.covers).toEqual([]);
    const img = document.createElement('img');
    component.hide({ target: img } as unknown as Event);
    expect(img.hidden).toBeTrue();
    component.show({ target: img } as unknown as Event);
    expect(img.hidden).toBeFalse();
  });
});
