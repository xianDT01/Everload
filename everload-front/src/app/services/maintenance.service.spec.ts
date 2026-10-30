import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MaintenanceService } from './maintenance.service';
import { ApiBaseService } from './api-base.service';

describe('MaintenanceService live status', () => {
  let service: MaintenanceService;
  let http: HttpTestingController;
  const url = 'https://backend.test/api/maintenance/status?ngsw-bypass=true';

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [{ provide: ApiBaseService, useValue: { withBackend: (path: string) => 'https://backend.test' + path } }]
    });
    service = TestBed.inject(MaintenanceService);
    http = TestBed.inject(HttpTestingController);
    spyOnProperty(document, 'hidden', 'get').and.returnValue(false);
  });

  afterEach(() => { service.ngOnDestroy(); http.verify(); });

  it('enters and leaves maintenance automatically using the configured backend', fakeAsync(() => {
    service.checkInitial();
    http.expectOne(url).flush({ active: true, message: 'Working' });
    expect(service.isActive).toBeTrue();
    tick(15000);
    http.expectOne(url).flush({ active: false, message: '' });
    expect(service.isActive).toBeFalse();
    service.ngOnDestroy();
    tick(30000);
    http.expectNone(url);
  }));

  it('does not overlap checks or clear maintenance when the backend is temporarily unavailable', fakeAsync(() => {
    service.setMaintenance(true, 'Working');
    service.checkInitial();
    service.checkInitial();
    http.expectOne(url).flush('Offline', { status: 502, statusText: 'Bad Gateway' });
    expect(service.isActive).toBeTrue();
    window.dispatchEvent(new Event('online'));
    http.expectOne(url).flush({ active: false, message: '' });
    expect(service.isActive).toBeFalse();
    service.ngOnDestroy();
  }));
});
