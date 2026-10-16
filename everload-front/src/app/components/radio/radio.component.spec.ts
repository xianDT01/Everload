import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { RadioComponent } from './radio.component';
import { RadioStation } from '../../services/radio-playback.service';

describe('RadioComponent catalog resilience', () => {
  let component: RadioComponent;
  let http: HttpTestingController;
  const params = new HttpParams();
  const fetchStations = () => (component as any).fetchStations(params);
  const url = (index = 0) => `${component.apiBases[index]}/json/stations/search`;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    http = TestBed.inject(HttpTestingController);
    component = new RadioComponent(TestBed.inject(HttpClient), {} as any, {} as any,
      { instant: (key: string) => key } as any,
      { state$: of({ selectedStation: null, playing: false, buffering: false, error: '', volume: 0.78 }) } as any);
  });
  afterEach(() => { component.ngOnDestroy(); http.verify(); });

  it('fails over after timeout and retries the primary server on a new search', fakeAsync(() => {
    fetchStations();
    const first = http.expectOne(url());
    tick(6000);
    expect(first.cancelled).toBeTrue();
    http.expectOne(url(1)).flush([]);
    fetchStations();
    http.expectOne(url()).flush([]);
  }));

  it('falls back to saved stations after all catalog servers fail', () => {
    fetchStations();
    component.apiBases.forEach((_, index) => http.expectOne(url(index))
      .flush('Unavailable', { status: 503, statusText: 'Unavailable' }));
    expect(component.stations.length).toBeGreaterThan(0);
    expect(component.loading).toBeFalse();
    expect(component.error).toBe('RADIO.ERROR_CATALOG_FALLBACK');
  });

  it('prefers current catalog streams for pinned stations and retains saved alternatives', () => {
    const saved = component.fallbackStations.find(station => station.name === 'Rock FM')!;
    const fresh: RadioStation = { ...saved, stationuuid: 'fresh', name: 'ROCKFM',
      url: 'https://radio.example/current.m3u8', url_resolved: 'https://radio.example/resolved.m3u8', hls: 1 };
    const merged: RadioStation[] = (component as any).withPinnedStations([fresh]);
    const pinned = merged.find(station => station.stationuuid === saved.stationuuid)!;
    expect(pinned.url).toBe(fresh.url);
    expect(pinned.hls).toBe(1);
    expect(pinned.fallbackUrls).toContain(saved.url);
    expect(saved.url).not.toBe(fresh.url);
  });

  it('excludes broken and duplicate catalog stations', () => {
    const saved = component.fallbackStations[0];
    const result = (component as any).cleanStations([saved, saved, { ...saved, lastcheckok: 0 },
      { ...saved, url: '', url_resolved: '' }]);
    expect(result).toEqual([saved]);
  });

  it('cancels an old search when filters change', () => {
    fetchStations();
    const old = http.expectOne(url());
    fetchStations();
    expect(old.cancelled).toBeTrue();
    http.expectOne(url()).flush([]);
  });
});
