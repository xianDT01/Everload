import { ModernBottombarComponent } from './modern-bottombar.component';
import { MusicService } from '../../../services/music.service';
import { AuthService } from '../../../services/auth.service';
import { of } from 'rxjs';

describe('Modern metadata permissions', () => {
  let component: ModernBottombarComponent;
  let role: string;
  let nas: jasmine.SpyObj<any>;

  beforeEach(() => {
    role = 'BASIC_USER';
    const auth = Object.create(AuthService.prototype);
    auth.getCurrentUser = () => ({ role });
    nas = jasmine.createSpyObj('NasService', ['updateMetadata', 'uploadTrackCover']);
    nas.updateMetadata.and.returnValue(of({}));
    nas.uploadTrackCover.and.returnValue(of({}));
    component = new ModernBottombarComponent({} as any, { invalidateOverview: () => {} } as any,
      {} as any, { showToast: () => {} } as any, nas, auth);
    component.state = { pathId: 1, currentTrack: { path: 'song.mp3', name: 'Song', source: 'nas' } } as any;
  });

  for (const deniedRole of ['BASIC_USER', 'USER', '']) {
    it(`blocks metadata and cover edits for ${deniedRole || 'anonymous users'}`, () => {
      role = deniedRole;
      component.coverFile = new File(['image'], 'cover.png', { type: 'image/png' });
      expect(component.canEditMetadata).toBeFalse();
      component.openMetadataEditor();
      component.saveMetadata();
      component.uploadTrackCover();
      expect(component.showMetadataEditor).toBeFalse();
      expect(nas.updateMetadata).not.toHaveBeenCalled();
      expect(nas.uploadTrackCover).not.toHaveBeenCalled();
    });
  }

  for (const allowedRole of ['ADMIN', 'NAS_USER']) {
    it(`allows NAS metadata and cover edits for ${allowedRole}`, () => {
      role = allowedRole;
      Object.assign((component as any).music, {
        coverOverrideMap: new Map(), invalidateTrackCover: () => {}
      });
      expect(component.canEditMetadata).toBeTrue();
      component.openMetadataEditor();
      expect(component.showMetadataEditor).toBeTrue();
      component.setMetadataField('title', 'Updated');
      component.saveMetadata();
      expect(nas.updateMetadata).toHaveBeenCalledWith(1, 'song.mp3', 'Updated', '', '', '');
      component.coverFile = new File(['image'], 'cover.png', { type: 'image/png' });
      component.uploadTrackCover();
      expect(nas.uploadTrackCover).toHaveBeenCalled();
    });
  }

  it('does not edit YouTube tracks even for an administrator', () => {
    role = 'ADMIN';
    component.state!.currentTrack!.source = 'youtube' as any;
    expect(component.canEditMetadata).toBeFalse();
    component.saveMetadata();
    expect(nas.updateMetadata).not.toHaveBeenCalled();
  });

  it('checks permissions again if the role changes after opening the editor', () => {
    role = 'ADMIN';
    component.openMetadataEditor();
    role = 'BASIC_USER';
    component.saveMetadata();
    expect(component.canEditMetadata).toBeFalse();
    expect(nas.updateMetadata).not.toHaveBeenCalled();
  });
});

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
    return new ModernBottombarComponent(musicSpy as any, {} as any, {} as any, {} as any, {} as any, { canManageNas: () => false } as any);
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
