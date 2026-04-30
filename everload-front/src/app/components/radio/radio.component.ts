import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription, catchError, of } from 'rxjs';
import { MusicService } from '../../services/music.service';
import { TranslateService } from '@ngx-translate/core';
import { RadioPlaybackService, RadioStation } from '../../services/radio-playback.service';

type RadioBrowserStation = RadioStation;

type RadioScope = 'ES' | 'WORLD';

@Component({
  selector: 'app-radio',
  templateUrl: './radio.component.html',
  styleUrls: ['./radio.component.css']
})
export class RadioComponent implements OnInit, OnDestroy {
  readonly apiBases = [
    'https://api.radio-browser.info',
    'https://de1.api.radio-browser.info',
    'https://nl1.api.radio-browser.info',
  ];

  readonly nationalPresets = [
    'Verbena FM',
    'Kyoto FM',
    'Motiva FM',
    'Los 40',
    'Cadena SER',
    'COPE',
    'RNE',
    'Cadena Dial',
    'Rock FM',
    'Kiss FM',
    'Radio Galega',
    'Radio Voz',
    'Capital Radio',
  ];

  readonly tagFilters = ['Todas', 'news', 'pop', 'rock', 'dance', 'latin', 'talk', 'sports', 'jazz'];
  readonly fallbackStations: RadioBrowserStation[] = [
    {
      stationuuid: 'fallback-verbena-fm',
      name: 'Verbena FM',
      url: 'https://streaming12.elitecomunicacion.es:8222/stream',
      url_resolved: 'https://streaming12.elitecomunicacion.es:8222/stream',
      homepage: 'https://verbenafm.com',
      favicon: 'https://pbs.twimg.com/profile_images/1463159511133442059/uVV15n4k_200x200.jpg',
      tags: 'verbena, fiesta, orquestas, galicia, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: 'Galicia',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 192,
      votes: 0,
      clickcount: 9999,
      lastcheckok: 1,
      fallbackUrls: [
        'https://streaming12.elitecomunicacion.es:8222/stream?type=.mp3',
        'http://streaming12.elitecomunicacion.es:8222/stream',
        'http://streaming12.elitecomunicacion.es:8222/stream?type=.mp3'
      ]
    },
    {
      stationuuid: 'fallback-kyoto-fm',
      name: 'Kyoto FM',
      url: 'https://sonic.mediatelekom.net/8148/stream',
      url_resolved: 'https://sonic.mediatelekom.net/8148/stream',
      homepage: 'https://www.kyotofm.es',
      favicon: 'https://graph.facebook.com/kyotofm/picture?width=200&height=200',
      tags: 'dance, latin, pop, galicia, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: 'Galicia',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 9998,
      lastcheckok: 1,
      fallbackUrls: [
        'http://178.32.60.136:8148/;',
        'http://178.32.60.136:8148/;stream.nsv'
      ]
    },
    {
      stationuuid: 'fallback-motiva-fm',
      name: 'Motiva FM',
      url: 'https://stream.motivafm.com/listen/motiva/motiva.mp3',
      url_resolved: 'https://stream.motivafm.com/listen/motiva/motiva.mp3',
      homepage: 'https://motivafm.com',
      favicon: 'https://media.motivafm.es/wp-content/uploads/2020/04/10224952/cropped-motiva-192x192.png',
      tags: 'reggaeton, urbano, latin, pop latino, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: 'Alicante',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 9997,
      lastcheckok: 1,
      fallbackUrls: [
        'https://server12.mediasector.es/listen/motiva/motiva-alicante.mp3',
        'https://stream.mediasector.es/radio/8120/motiva-alicante.mp3',
        'https://stream.mediasector.es/radio/8120/radio.mp3',
        'http://stream.mediasector.es/radio/8120/radio.mp3'
      ]
    },
    {
      stationuuid: 'fallback-los40',
      name: 'Los 40',
      url: 'https://playerservices.streamtheworld.com/api/livestream-redirect/Los40.mp3',
      url_resolved: 'https://playerservices.streamtheworld.com/api/livestream-redirect/Los40.mp3',
      homepage: 'https://los40.com',
      favicon: 'https://graph.facebook.com/los40/picture?width=200&height=200',
      tags: 'pop, hits, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    },
    {
      stationuuid: 'fallback-cadena-ser',
      name: 'Cadena SER',
      url: 'https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER.mp3',
      url_resolved: 'https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER.mp3',
      homepage: 'https://cadenaser.com',
      favicon: 'https://graph.facebook.com/cadenaser/picture?width=200&height=200',
      tags: 'news, talk, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
      fallbackUrls: [
        'https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER_ALT1.mp3',
        'https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER_ALT2.mp3'
      ]
    },
    {
      stationuuid: 'fallback-cope',
      name: 'COPE',
      url: 'https://flucast24-h-cloud.flumotion.com/cope/net1.mp3',
      url_resolved: 'https://flucast24-h-cloud.flumotion.com/cope/net1.mp3',
      homepage: 'https://www.cope.es',
      favicon: 'https://graph.facebook.com/COPE/picture?width=200&height=200',
      tags: 'news, talk, sports, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
      fallbackUrls: ['https://flucast-b04-04.flumotion.com/cope/net2.mp3']
    },
    {
      stationuuid: 'fallback-rne',
      name: 'Radio Nacional',
      url: 'https://dispatcher.rndfnk.com/crtve/rne1/gal/mp3/high',
      url_resolved: 'https://dispatcher.rndfnk.com/crtve/rne1/gal/mp3/high',
      homepage: 'https://www.rtve.es/radio/',
      favicon: 'https://graph.facebook.com/radionacionalrne/picture?width=200&height=200',
      tags: 'news, talk, public, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
      fallbackUrls: ['https://dispatcher.rndfnk.com/crtve/rne5/lcg/mp3/high']
    },
    {
      stationuuid: 'fallback-cadena-dial',
      name: 'Cadena Dial',
      url: 'https://playerservices.streamtheworld.com/api/livestream-redirect/CADENADIAL.mp3',
      url_resolved: 'https://playerservices.streamtheworld.com/api/livestream-redirect/CADENADIAL.mp3',
      homepage: 'https://www.cadenadial.com',
      favicon: 'https://graph.facebook.com/cadenadial/picture?width=200&height=200',
      tags: 'latin, pop, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    },
    {
      stationuuid: 'fallback-rock-fm',
      name: 'Rock FM',
      url: 'https://flucast-b04-04.flumotion.com/cope/rockfm.mp3',
      url_resolved: 'https://flucast-b04-04.flumotion.com/cope/rockfm.mp3',
      homepage: 'https://www.rockfm.fm',
      favicon: 'https://graph.facebook.com/rockfm/picture?width=200&height=200',
      tags: 'rock, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    },
    {
      stationuuid: 'fallback-kiss-fm',
      name: 'Kiss FM',
      url: 'https://kissfm.kissfmradio.cires21.com/kissfm.mp3',
      url_resolved: 'https://kissfm.kissfmradio.cires21.com/kissfm.mp3',
      homepage: 'https://www.kissfm.es',
      favicon: 'https://graph.facebook.com/kissfmspain/picture?width=200&height=200',
      tags: 'pop, hits, 80s, 90s, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
      fallbackUrls: [
        'https://bbkissfm.kissfmradio.cires21.com:8443/bbkissfm/mp3/icecast.audio'
      ]
    },
    {
      stationuuid: 'fallback-radio-galega',
      name: 'Radio Galega',
      url: 'https://wecast-b03-03.flumotion.com/radiogalega/live.mp3',
      url_resolved: 'https://wecast-b03-03.flumotion.com/radiogalega/live.mp3',
      homepage: 'https://www.crtvg.es/rg',
      favicon: 'https://graph.facebook.com/aradiogalega/picture?width=200&height=200',
      tags: 'news, talk, galicia, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: 'Galicia',
      language: 'galician, spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
      fallbackUrls: ['http://wecast-b02-03.flumotion.com/radiogalega/live.mp3']
    },
    {
      stationuuid: 'fallback-radio-galega-musica',
      name: 'Radio Galega Musica',
      url: 'https://wecast-b03-02.flumotion.com/radiogalega-musica/live.mp3',
      url_resolved: 'https://wecast-b03-02.flumotion.com/radiogalega-musica/live.mp3',
      homepage: 'https://www.crtvg.es/rg',
      favicon: 'https://graph.facebook.com/aradiogalega/picture?width=200&height=200',
      tags: 'music, galicia, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: 'Galicia',
      language: 'galician, spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    },
    {
      stationuuid: 'fallback-radio-voz',
      name: 'Radio Voz',
      url: 'https://live.radiovoz.es/mp3/stream_santiago.mp3',
      url_resolved: 'https://live.radiovoz.es/mp3/stream_santiago.mp3',
      homepage: 'https://www.radiovoz.com',
      favicon: 'https://graph.facebook.com/radiovoz/picture?width=200&height=200',
      tags: 'news, galicia, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: 'Galicia',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    },
    {
      stationuuid: 'fallback-capital-radio',
      name: 'Capital Radio',
      url: 'https://capitalradio-live.flumotion.com/capital-radio/capitalradio.mp3',
      url_resolved: 'https://capitalradio-live.flumotion.com/capital-radio/capitalradio.mp3',
      homepage: 'https://www.capitalradio.es',
      favicon: 'https://graph.facebook.com/capitalradiob/picture?width=200&height=200',
      tags: 'news, economy, talk, nacional',
      country: 'Spain',
      countrycode: 'ES',
      state: '',
      language: 'spanish',
      codec: 'MP3',
      bitrate: 128,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    },
  ];

  stations: RadioBrowserStation[] = [];
  selectedStation: RadioBrowserStation | null = null;
  searchQuery = '';
  directUrl = '';
  scope: RadioScope = 'ES';
  activeTag = 'Todas';
  activePreset = '';
  loading = false;
  playing = false;
  buffering = false;
  error = '';
  volume = 0.78;

  private sub?: Subscription;
  private playbackSub?: Subscription;
  private apiIndex = 0;
  private brokenLogos = new Set<string>();

  constructor(
    private http: HttpClient,
    private router: Router,
    private musicService: MusicService,
    private translate: TranslateService,
    private radioPlayback: RadioPlaybackService
  ) {}

  ngOnInit(): void {
    this.playbackSub = this.radioPlayback.state$.subscribe(state => {
      this.selectedStation = state.selectedStation;
      this.playing = state.playing;
      this.buffering = state.buffering;
      this.error = state.error;
      this.volume = state.volume;
    });
    this.loadNational();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.playbackSub?.unsubscribe();
  }

  goHome(): void {
    this.router.navigate(['/']);
  }

  loadNational(): void {
    this.scope = 'ES';
    this.activePreset = '';
    this.searchQuery = '';
    this.stations = this.fallbackStations;
    this.searchStations();
  }

  searchPreset(preset: string): void {
    this.scope = 'ES';
    this.activePreset = preset;
    this.searchQuery = preset;
    this.activeTag = 'Todas';
    this.searchStations();
  }

  searchStations(): void {
    this.loading = true;
    this.error = '';

    let params = new HttpParams()
      .set('hidebroken', 'true')
      .set('order', 'clickcount')
      .set('reverse', 'true')
      .set('limit', '48');

    const query = this.searchQuery.trim();
    if (query) params = params.set('name', query);
    if (this.scope === 'ES') params = params.set('countrycode', 'ES');
    if (this.activeTag !== 'Todas') params = params.set('tag', this.activeTag);

    this.fetchStations(params);
  }

  setScope(scope: RadioScope): void {
    this.scope = scope;
    this.activePreset = '';
    this.searchStations();
  }

  setTag(tag: string): void {
    this.activeTag = tag;
    this.activePreset = '';
    this.searchStations();
  }

  tune(station: RadioBrowserStation): void {
    if (this.selectedStation?.stationuuid === station.stationuuid && this.playing) {
      this.pause();
      return;
    }

    this.musicService.mainPlayer.pause();
    this.radioPlayback.playStation(station);
  }

  tuneDirect(): void {
    const url = this.directUrl.trim();
    if (!url) return;

    this.musicService.mainPlayer.pause();
    this.radioPlayback.playDirect(url);
  }

  pause(): void {
    this.radioPlayback.pause();
  }

  stop(): void {
    this.radioPlayback.stop();
  }

  setVolume(event: Event): void {
    this.radioPlayback.setVolume(+(event.target as HTMLInputElement).value);
  }

  isActive(station: RadioBrowserStation): boolean {
    return this.selectedStation?.stationuuid === station.stationuuid;
  }

  stationMeta(station: RadioBrowserStation): string {
    return [
      station.countrycode || station.country,
      station.codec,
      station.bitrate ? `${station.bitrate} kbps` : '',
    ].filter(Boolean).join(' · ');
  }

  stationTags(station: RadioBrowserStation): string[] {
    return (station.tags || '')
      .split(',')
      .map(t => t.trim())
      .filter(Boolean)
      .slice(0, 3);
  }

  logoBroken(station: RadioBrowserStation): boolean {
    return this.brokenLogos.has(station.stationuuid || station.name);
  }

  onLogoError(event: Event, station: RadioBrowserStation): void {
    this.brokenLogos.add(station.stationuuid || station.name);
    (event.target as HTMLImageElement).style.display = 'none';
  }

  private fetchStations(params: HttpParams): void {
    const base = this.apiBases[this.apiIndex % this.apiBases.length];
    this.sub?.unsubscribe();
    this.sub = this.http.get<RadioBrowserStation[]>(`${base}/json/stations/search`, { params })
      .pipe(catchError(() => of(null)))
      .subscribe(result => {
        if (result === null && this.apiIndex < this.apiBases.length - 1) {
          this.apiIndex += 1;
          this.fetchStations(params);
          return;
        }

        this.loading = false;
        if (result === null) {
          this.useFallbackStations();
          return;
        }

        this.stations = this.withPinnedStations(this.cleanStations(result));
        if (!this.stations.length) this.useFallbackStations(this.translate.instant('RADIO.ERROR_NO_RESULTS'));
      });
  }

  private useFallbackStations(message = this.translate.instant('RADIO.ERROR_CATALOG_FALLBACK')): void {
    const query = this.searchQuery.trim().toLowerCase();
    const tag = this.activeTag.toLowerCase();

    let stations = this.fallbackStations;
    if (query) {
      stations = stations.filter(station =>
        station.name.toLowerCase().includes(query) ||
        station.tags.toLowerCase().includes(query)
      );
    }
    if (this.activeTag !== 'Todas') {
      stations = stations.filter(station => station.tags.toLowerCase().includes(tag));
    }

    if (!stations.length && this.scope === 'ES') {
      this.stations = this.fallbackStations;
      this.error = `${message} ${this.translate.instant('RADIO.ERROR_NOT_SAVED')}`;
      return;
    }

    this.stations = stations;
    this.error = stations.length ? message : `${message} ${this.translate.instant('RADIO.ERROR_TRY_DIRECT')}`;
  }

  private cleanStations(stations: RadioBrowserStation[]): RadioBrowserStation[] {
    const seen = new Set<string>();
    return stations
      .filter(station => station.name && (station.url_resolved || station.url) && station.lastcheckok !== 0)
      .filter(station => {
        const key = `${station.name}|${station.url_resolved || station.url}`.toLowerCase();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, 36);
  }

  private withPinnedStations(stations: RadioBrowserStation[]): RadioBrowserStation[] {
    if (this.scope !== 'ES') return stations;

    const query = this.searchQuery.trim().toLowerCase();
    const tag = this.activeTag.toLowerCase();
    const pinned = this.fallbackStations.filter(station => {
      const matchesQuery = !query ||
        station.name.toLowerCase().includes(query) ||
        station.tags.toLowerCase().includes(query);
      const matchesTag = this.activeTag === 'Todas' || station.tags.toLowerCase().includes(tag);
      return matchesQuery && matchesTag;
    });

    const seen = new Set<string>();
    return [...pinned, ...stations].filter(station => {
      const key = `${station.name}|${station.url_resolved || station.url}`.toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    }).slice(0, 36);
  }

}
