import { DeckPlayer, MusicMetadataDto } from './music.service';

describe('DeckPlayer YouTube first playback', () => {
  let player: DeckPlayer;
  let previousYt: any;
  let instances: Array<{ api: any; options: any }>;
  const track = (path: string, source = 'ytmusic') => ({ path, source } as MusicMetadataDto);

  beforeEach(() => {
    previousYt = (window as any).YT;
    instances = [];
    spyOn<any>(DeckPlayer.prototype, 'setupAudioNodes').and.stub();
    (window as any).YT = {
      Player: function (_id: string, options: any) {
        const api = jasmine.createSpyObj('YouTubePlayer', ['playVideo', 'pauseVideo', 'stopVideo', 'setVolume', 'getDuration', 'loadVideoById', 'destroy']);
        api.getDuration.and.returnValue(180);
        instances.push({ api, options });
        return api;
      }
    };
    player = new DeckPlayer({} as any, 'first-play-test');
  });

  afterEach(() => {
    (player as any).stopAll();
    document.getElementById('yt-player-first-play-test')?.remove();
    (window as any).YT = previousYt;
  });

  it('keeps the first request until the iframe is ready without another song click', async () => {
    await player.load(track('first'), -20);
    player.play();
    expect(instances[0].api.playVideo).not.toHaveBeenCalled();
    instances[0].options.events.onReady();
    expect(instances[0].api.playVideo).toHaveBeenCalledTimes(1);
    expect(player.state.duration).toBe(180);
  });

  it('does not start a track that was only loaded, without a play request', async () => {
    await player.load(track('cue', 'youtube'), -20);
    instances[0].options.events.onReady();
    expect(instances[0].api.playVideo).not.toHaveBeenCalled();
  });

  it('cancels a pending request when paused before readiness', async () => {
    await player.load(track('first'), -20);
    player.play();
    player.pause();
    instances[0].options.events.onReady();
    expect(instances[0].api.playVideo).not.toHaveBeenCalled();
    player.play();
    expect(instances[0].api.playVideo).toHaveBeenCalledTimes(1);
  });

  it('ignores readiness from a replaced player and starts only the latest song', async () => {
    await player.load(track('first'), -20);
    player.play();
    await player.load(track('second'), -20);
    player.play();
    instances[0].options.events.onReady();
    expect(instances[0].api.playVideo).not.toHaveBeenCalled();
    instances[1].options.events.onReady();
    expect(instances[1].api.playVideo).toHaveBeenCalledTimes(1);
    expect(player.state.currentTrack?.path).toBe('second');
  });

  it('reuses a ready iframe for subsequent songs', async () => {
    await player.load(track('first'), -20);
    player.play();
    instances[0].options.events.onReady();
    await player.load(track('second'), -20);
    player.play();
    expect(instances.length).toBe(1);
    expect(instances[0].api.loadVideoById).toHaveBeenCalledWith('second');
    expect(instances[0].api.playVideo).toHaveBeenCalledTimes(2);
  });

  it('does not replay YouTube if a local track replaces the pending song', async () => {
    await player.load(track('first'), -20);
    player.play();
    await player.load(track('', 'local'), 0);
    instances[0].options.events.onReady();
    expect(instances[0].api.playVideo).not.toHaveBeenCalled();
  });
});
