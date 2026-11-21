import { DeckPlayer } from './music.service';

describe('Winamp audio processing', () => {
  let player: DeckPlayer;
  let gains: jasmine.Spy[];
  let preamp: jasmine.Spy;
  beforeEach(() => {
    player = Object.create(DeckPlayer.prototype);
    gains = Array.from({ length: 10 }, () => jasmine.createSpy('gain'));
    preamp = jasmine.createSpy('preamp');
    Object.assign(player, { audioCtx: { currentTime: 1 }, winampBands: gains.map(setTargetAtTime => ({ gain: { setTargetAtTime } })),
      winampPreamp: { gain: { setTargetAtTime: preamp } }, channelMode: 'stereo', balance: 0 });
  });
  it('applies each of the ten frequencies separately without grouping pairs', () => {
    player.setWinampEq([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], -6, true);
    gains.forEach((gain, i) => expect(gain).toHaveBeenCalledWith(i + 1, 1, 0.02));
    expect(preamp).toHaveBeenCalledWith(Math.pow(10, -6 / 20), 1, 0.02);
  });
  it('bypasses both filters and preamp when disabled', () => {
    player.setWinampEq(Array(10).fill(8), 12, false);
    gains.forEach(gain => expect(gain).toHaveBeenCalledWith(0, 1, 0.02));
    expect(preamp).toHaveBeenCalledWith(1, 1, 0.02);
  });
  it('rejects malformed settings and clamps extreme finite gains', () => {
    player.setWinampEq(Array(9).fill(0), 0, true);
    player.setWinampEq(Array(10).fill(NaN), 0, true);
    expect(preamp).not.toHaveBeenCalled();
    player.setWinampEq(Array(10).fill(100), -100, true);
    expect(gains[0]).toHaveBeenCalledWith(12, 1, 0.02);
    expect(preamp).toHaveBeenCalledWith(Math.pow(10, -12 / 20), 1, 0.02);
  });
  it('balances actual output channels while preserving channel-mode routing', () => {
    const routing = Array.from({ length: 4 }, () => jasmine.createSpy('route'));
    (player as any).channelModeGains = routing.map(setTargetAtTime => ({ gain: { setTargetAtTime } }));
    player.setBalance(1);
    expect(routing.map(spy => spy.calls.mostRecent().args[0])).toEqual([0, 0, 0, 1]);
    player.setChannelMode('mono');
    expect(routing.map(spy => spy.calls.mostRecent().args[0])).toEqual([0, 0, 0.5, 0.5]);
    player.setBalance(0);
    expect(routing.map(spy => spy.calls.mostRecent().args[0])).toEqual([0.5, 0.5, 0.5, 0.5]);
  });
});
