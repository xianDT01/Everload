import { BehaviorSubject, of, Subject } from 'rxjs';
import { ModernLayoutComponent } from './modern-layout.component';

describe('ModernLayoutComponent notification click', () => {
  it('opens the full player once and stops listening when the layout is destroyed', () => {
    const requested = new Subject<void>();
    const fullscreen = new BehaviorSubject(false);
    const state: any = { showQueue$: of(false), showFullscreen$: fullscreen,
      get showFullscreen() { return fullscreen.value; },
      toggleFullscreen: jasmine.createSpy().and.callFake(() => fullscreen.next(!fullscreen.value)) };
    const component = new ModernLayoutComponent({ playerOpenRequested$: requested } as any, state);
    component.ngOnInit();
    requested.next();
    requested.next();
    expect(component.showFullscreen).toBeTrue();
    expect(state.toggleFullscreen).toHaveBeenCalledTimes(1);
    component.ngOnDestroy();
    fullscreen.next(false);
    requested.next();
    expect(state.toggleFullscreen).toHaveBeenCalledTimes(1);
  });
});
