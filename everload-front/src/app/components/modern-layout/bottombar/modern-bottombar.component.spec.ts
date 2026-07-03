import { ModernBottombarComponent } from './modern-bottombar.component';
import { MusicService } from '../../../services/music.service';

/**
 * Focuses on the streaming-quality selector wired into the bottombar this
 * session (MusicService.QUALITY_OPTIONS pills + persisted preference).
 *
 * The component is instantiated directly with stubbed collaborators rather
 * than through TestBed: its template/ngOnInit pull in the full player/EQ/queue
 * surface (Router, ModernStateService, mainPlayer audio nodes, etc.), which is
 * unrelated to the quality-selector behavior under test here.
 */
describe('ModernBottombarComponent — stream quality selector', () => {
  let musicSpy: jasmine.SpyObj<MusicService>;

  beforeEach(() => {
    localStorage.clear();
    musicSpy = jasmine.createSpyObj<MusicService>('MusicService', ['setStreamQuality']);
  });

  afterEach(() => localStorage.clear());

  function createComponent(): ModernBottombarComponent {
    return new ModernBottombarComponent(musicSpy as any, {} as any, {} as any, {} as any, {} as any);
  }

  it('exposes MusicService.QUALITY_OPTIONS as the selectable presets', () => {
    const component = createComponent();

    expect(component.qualityOptions).toBe(MusicService.QUALITY_OPTIONS);
  });

  it('defaults streamQuality to "original" when nothing is stored', () => {
    const component = createComponent();

    expect(component.streamQuality).toBe('original');
  });

  it('initializes streamQuality from a previously stored preference', () => {
    localStorage.setItem('streamQuality', 'low');

    const component = createComponent();

    expect(component.streamQuality).toBe('low');
  });

  it('setStreamQuality() persists the choice via MusicService and updates local state', () => {
    const component = createComponent();

    component.setStreamQuality('high');

    expect(musicSpy.setStreamQuality).toHaveBeenCalledWith('high');
    expect(component.streamQuality).toBe('high');
  });
});
