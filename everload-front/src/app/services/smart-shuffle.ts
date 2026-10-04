/** Keeps every entry and spreads repeated artists across the queue. */
export function smartShuffle<T extends { artist?: string }>(tracks: T[], random = Math.random): T[] {
  const groups = new Map<string, T[]>();
  tracks.forEach((track, index) => {
    const artist = track.artist?.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase() || `unknown:${index}`;
    const group = groups.get(artist) || [];
    group.push(track);
    groups.set(artist, group);
  });
  for (const group of groups.values()) {
    for (let i = group.length - 1; i > 0; i--) {
      const j = Math.floor(random() * (i + 1));
      [group[i], group[j]] = [group[j], group[i]];
    }
  }
  const result: T[] = [];
  let previous = '';
  while (groups.size) {
    let candidates = [...groups.keys()].filter(key => key !== previous);
    if (!candidates.length) candidates = [...groups.keys()];
    const largest = Math.max(...candidates.map(key => groups.get(key)!.length));
    candidates = candidates.filter(key => groups.get(key)!.length === largest);
    const chosen = candidates[Math.floor(random() * candidates.length)];
    const group = groups.get(chosen)!;
    result.push(group.pop()!);
    if (!group.length) groups.delete(chosen);
    previous = chosen;
  }
  return result;
}
