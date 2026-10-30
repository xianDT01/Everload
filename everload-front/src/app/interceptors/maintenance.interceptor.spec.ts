import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { firstValueFrom, throwError } from 'rxjs';
import { MaintenanceInterceptor } from './maintenance.interceptor';
import { MaintenanceService } from '../services/maintenance.service';

describe('MaintenanceInterceptor', () => {
  const request = new HttpRequest('GET', '/api/downloadInstagram', { responseType: 'blob' });

  it('recognizes maintenance JSON returned as a download Blob and preserves the original error', async () => {
    const service = jasmine.createSpyObj<MaintenanceService>('MaintenanceService', ['setMaintenance']);
    const interceptor = new MaintenanceInterceptor(service);
    const error = new HttpErrorResponse({ status: 503,
      error: new Blob([JSON.stringify({ maintenance: true, message: 'Line one\nLine two' })], { type: 'application/json' }) });
    await firstValueFrom(interceptor.intercept(request, { handle: () => throwError(() => error) }))
      .then(() => fail('Expected original error'), actual => expect(actual).toBe(error));
    expect(service.setMaintenance).toHaveBeenCalledWith(true, 'Line one\nLine two');
  });

  it('does not mistake an overloaded service or proxy HTML response for maintenance', async () => {
    const service = jasmine.createSpyObj<MaintenanceService>('MaintenanceService', ['setMaintenance']);
    const interceptor = new MaintenanceInterceptor(service);
    const error = new HttpErrorResponse({ status: 503, error: '<html>Unavailable</html>' });
    await firstValueFrom(interceptor.intercept(request, { handle: () => throwError(() => error) }))
      .catch(actual => expect(actual).toBe(error));
    expect(service.setMaintenance).not.toHaveBeenCalled();
  });
});
