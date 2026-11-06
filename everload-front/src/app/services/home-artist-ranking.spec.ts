import { homeArtistNames, homePersonalization, rankHomeArtists } from './home-artist-ranking';

describe('Shared home artist ranking', () => {
  const artists = [{ artist: 'Collection', tracks: [1, 2, 3] }, { artist: 'Favorite', tracks: [1] }];
  it('keeps established listeners personal even when the community prefers another artist', () => {
    const state = homePersonalization('2025-01-01', 1, [{ artist: 'Favorite', playCount: 20 }], 0);
    const result = rankHomeArtists(artists, { plays: [{ artist: 'Favorite', playCount: 20 }], history: [], favorites: [], community: [{ artist: 'Collection', playCount: 1000 }], ...state });
    expect(result[0].artist).toBe('Favorite');
    expect(artists[0].artist).toBe('Collection');
  });
  it('starts new listeners with community recommendations', () => {
    const state = homePersonalization('2026-11-01', 0, [], 0);
    expect(rankHomeArtists(artists, { plays: [], history: [], favorites: [], community: [{ artist: 'Favorite', playCount: 10 }], ...state })[0].artist).toBe('Favorite');
  });
  it('filters channel names and splits collaborations like Modern', () => {
    expect(homeArtistNames('Singer feat. Guest & Official Channel')).toEqual(['Singer', 'Guest']);
  });
});
