import { ApiBaseService } from './api-base.service';

describe('ApiBaseService', () => {
  let service: ApiBaseService;

  beforeEach(() => {
    service = new ApiBaseService();
    localStorage.removeItem('everload.backendUrl');
    delete (window as any).Capacitor;
  });

  it('loads and normalizes runtime backend and Android version configuration', async () => {
    spyOn(window, 'fetch').and.returnValue(Promise.resolve({
      ok: true,
      json: () => Promise.resolve({
        backendUrl: ' https://api.example.test/// ',
        androidVersionCode: '42.9',
        androidVersionName: ' 2.4 '
      })
    } as Response));

    await service.load();

    expect(service.backendUrl).toBe('https://api.example.test');
    expect(service.androidVersionCode).toBe(42);
    expect(service.androidVersionName).toBe('2.4');
  });

  it('ignores unavailable and malformed runtime configuration safely', async () => {
    spyOn(window, 'fetch').and.returnValue(Promise.resolve({ ok: false } as Response));
    await service.load();
    expect(service.androidVersionCode).toBe(0);

    (window.fetch as jasmine.Spy).and.returnValue(Promise.reject(new Error('offline')));
    await service.load();
    expect(service.backendUrl).toBe('');
  });

  it('uses the first runtime backend candidate in browser environments', async () => {
    spyOn(window, 'fetch').and.returnValue(Promise.resolve({
      ok: true,
      json: () => Promise.resolve({
        fallbackBackendUrls: ['https://first.test/', 'https://second.test']
      })
    } as Response));

    await service.load();

    expect(service.backendUrl).toBe('https://first.test');
  });

  it('uses a saved backend URL and leaves production-host fallback empty', () => {
    localStorage.setItem('everload.backendUrl', ' https://saved.test/// ');
    expect(service.backendUrl).toBe('https://saved.test');

    localStorage.removeItem('everload.backendUrl');
    expect(service.backendUrl).toBe('');
  });

  it('rewrites local API origins, prefixes API paths, and leaves external URLs untouched', () => {
    localStorage.setItem('everload.backendUrl', 'https://api.example.test');

    expect(service.withBackend('http://localhost:8080/api/music')).toBe('https://api.example.test/api/music');
    expect(service.withBackend('http://127.0.0.1:8080/api/music')).toBe('https://api.example.test/api/music');
    expect(service.withBackend('/api/music')).toBe('https://api.example.test/api/music');
    expect(service.withBackend('/assets/cover.jpg')).toBe('/assets/cover.jpg');
  });

  it('uses the Android emulator backend when running natively without runtime config', () => {
    (window as any).Capacitor = { isNativePlatform: () => true };

    expect(service.isNativePlatform()).toBeTrue();
    expect(service.backendUrl).toBe('http://10.0.2.2:8080');
  });

  it('rejects non-positive and non-finite Android version codes', async () => {
    spyOn(window, 'fetch').and.returnValue(Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ backendUrl: '', androidVersionCode: 'NaN', androidVersionName: '' })
    } as Response));

    await service.load();

    expect(service.androidVersionCode).toBe(0);
    expect(service.androidVersionName).toBe('');
  });
});
