export interface ArtistPlayCount { artist: string; playCount: number; }
interface ArtistSignal { artist?: string; available?: boolean; }
interface ArtistCandidate { artist: string; tracks: unknown[]; }

export function homeArtistKey(value: string): string {
  return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .trim().toLowerCase().replace(/[^a-z0-9]+/g, ' ').replace(/\s+/g, ' ').trim();
}

export function suspiciousHomeArtist(value: string): boolean {
  const key = homeArtistKey(value);
  return !key || /\b(clean edit|audio edit|extended edit|radio edit|lyrics?|lyric video)\b/.test(key)
    || /\b(vevo|official|topic|records|recordings|music tv|musictv|entertainment|official channel)\b/.test(key)
    || key === 'dj clean edit' || key === 'unknown' || key === 'desconocido';
}

export function homeArtistNames(value: string): string[] {
  const parts = (value || '').trim().split(/\s*(?:,|;|&|\+|\/|\bfeat\b\.?|\bft\b\.?|\bcon\b|\band\b| y )\s*/i);
  const unique = new Map<string, string>();
  parts.map(part => part.trim()).filter(Boolean).forEach(part => {
    const key = homeArtistKey(part);
    if (!suspiciousHomeArtist(part) && !unique.has(key)) unique.set(key, part);
  });
  return [...unique.values()];
}

export function homePersonalization(createdAt: string | undefined, historyCount: number, plays: ArtistPlayCount[], favoritesCount: number) {
  const existingAccount = !createdAt || createdAt < '2026-09-25T00:00:00';
  const signals = Math.max(historyCount, plays.reduce((total, entry) => total + (entry.playCount || 0), 0)) + favoritesCount * 2;
  return { existingAccount, personalWeight: existingAccount ? 1 : Math.min(1, signals / 15) };
}

export function rankHomeArtists<T extends ArtistCandidate>(artists: T[], options: {
  plays: ArtistPlayCount[]; history: ArtistSignal[]; favorites: ArtistSignal[];
  community: ArtistPlayCount[]; existingAccount: boolean; personalWeight: number;
}): T[] {
  const own = new Map<string, number>();
  options.plays.forEach(entry => own.set(homeArtistKey(entry.artist), entry.playCount));
  if (!options.existingAccount) {
    options.history.filter(item => item.available !== false).forEach(item => homeArtistNames(item.artist || '').forEach(name => {
      const key = homeArtistKey(name);
      if (!own.has(key)) own.set(key, 1);
    }));
    options.favorites.filter(item => item.available !== false).forEach(item => homeArtistNames(item.artist || '').forEach(name => {
      const key = homeArtistKey(name);
      own.set(key, (own.get(key) || 0) + 2);
    }));
  }
  const global = new Map<string, number>();
  options.community.forEach(entry => homeArtistNames(entry.artist).forEach(name => {
    const key = homeArtistKey(name);
    global.set(key, (global.get(key) || 0) + entry.playCount);
  }));
  const maxOwn = Math.max(0, ...own.values());
  const maxGlobal = Math.max(0, ...global.values());
  const score = (artist: T) => {
    const key = homeArtistKey(artist.artist);
    return (maxOwn ? (own.get(key) || 0) / maxOwn : 0) * options.personalWeight
      + (maxGlobal ? (global.get(key) || 0) / maxGlobal : 0) * (1 - options.personalWeight);
  };
  return [...artists].sort((a, b) => {
    const difference = options.existingAccount
      ? (own.get(homeArtistKey(b.artist)) || 0) - (own.get(homeArtistKey(a.artist)) || 0)
      : score(b) - score(a);
    return difference || b.tracks.length - a.tracks.length || a.artist.localeCompare(b.artist);
  });
}
