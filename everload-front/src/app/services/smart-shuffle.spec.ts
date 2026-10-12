import { smartShuffle } from './smart-shuffle';

describe('smartShuffle', () => {
  it('keeps every track and does not mutate the input', () => {
    const tracks = [{ artist: 'A', id: 1 }, { artist: 'A', id: 2 }, { artist: 'B', id: 3 }];
    const shuffled = smartShuffle(tracks, () => 0);

    expect(shuffled.map(track => track.id).sort()).toEqual([1, 2, 3]);
    expect(tracks.map(track => track.id)).toEqual([1, 2, 3]);
  });

  it('separates normalized artist names where possible', () => {
    const tracks = [
      { artist: 'Beyoncé', id: 1 },
      { artist: ' Beyonce ', id: 2 },
      { artist: 'Other', id: 3 }
    ];

    const shuffled = smartShuffle(tracks, () => 0);

    expect(shuffled[0].id).not.toBe(shuffled[1].id);
    expect(shuffled.map(track => track.id).sort()).toEqual([1, 2, 3]);
  });

  it('handles empty, singleton, and missing artist values', () => {
    expect(smartShuffle([])).toEqual([]);
    expect(smartShuffle([{ artist: '', id: 1 }], () => 0)).toEqual([{ artist: '', id: 1 }]);
    expect(smartShuffle([{ artist: '', id: 1 }, { artist: '', id: 2 }], () => 0).length).toBe(2);
  });
});
