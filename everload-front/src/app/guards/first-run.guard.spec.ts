import { of, firstValueFrom, throwError } from 'rxjs';
import { FirstRunGuard } from './first-run.guard';

describe('FirstRunGuard', () => {
  it('redirects a new installation to setup', async () => {
    const router: any = { parseUrl: jasmine.createSpy().and.returnValue('setup-url') };
    const guard = new FirstRunGuard({ status: () => of({ required: true }) } as any, router);
    expect(await firstValueFrom(guard.canActivate())).toBe('setup-url' as any);
  });
  it('preserves existing installations', async () => {
    const guard = new FirstRunGuard({ status: () => of({ required: false }) } as any, {} as any);
    expect(await firstValueFrom(guard.canActivate())).toBeTrue();
  });
  it('does not lock out users when the status request fails', async () => {
    const guard = new FirstRunGuard({ status: () => throwError(() => new Error('offline')) } as any, {} as any);
    expect(await firstValueFrom(guard.canActivate())).toBeTrue();
  });
});
