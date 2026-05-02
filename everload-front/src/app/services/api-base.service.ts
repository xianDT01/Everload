import { Injectable } from '@angular/core';

interface RuntimeConfig {
  backendUrl?: string;
  fallbackBackendUrls?: string[];
}

@Injectable({ providedIn: 'root' })
export class ApiBaseService {
  private configuredBackendUrl = '';

  async load(): Promise<void> {
    if (typeof window === 'undefined') return;

    try {
      const response = await fetch('./assets/config/runtime-config.json', { cache: 'no-store' });
      if (!response.ok) return;

      const config = await response.json() as RuntimeConfig;
      const candidates = [
        config.backendUrl,
        ...(config.fallbackBackendUrls || [])
      ].map(url => this.normalize(url || '')).filter(Boolean);

      this.configuredBackendUrl = await this.resolveReachableBackend(candidates);
    } catch {
      this.configuredBackendUrl = '';
    }
  }

  get backendUrl(): string {
    if (this.configuredBackendUrl) return this.configuredBackendUrl;
    if (typeof window === 'undefined') return '';

    const stored = localStorage.getItem('everload.backendUrl');
    if (stored) return this.normalize(stored);

    const host = window.location.hostname;
    const port = window.location.port;

    if ((host === 'localhost' || host === '127.0.0.1') && port === '4200') {
      return 'http://localhost:8080';
    }

    return this.isCapacitor() ? 'http://10.0.2.2:8080' : '';
  }

  withBackend(pathOrUrl: string): string {
    const backendUrl = this.backendUrl;
    if (!backendUrl) return pathOrUrl;

    if (pathOrUrl.startsWith('http://localhost:8080') || pathOrUrl.startsWith('http://127.0.0.1:8080')) {
      return pathOrUrl.replace(/^http:\/\/(localhost|127\.0\.0\.1):8080/, backendUrl);
    }

    if (pathOrUrl.startsWith('/api/') || pathOrUrl === '/api') {
      return `${backendUrl}${pathOrUrl}`;
    }

    return pathOrUrl;
  }

  private isCapacitor(): boolean {
    const capacitor = (window as any).Capacitor;
    return window.location.protocol === 'capacitor:' || !!capacitor?.isNativePlatform?.();
  }

  private normalize(url: string): string {
    return url.trim().replace(/\/+$/, '');
  }

  private async resolveReachableBackend(candidates: string[]): Promise<string> {
    if (candidates.length === 0) return '';
    if (!this.isCapacitor()) return candidates[0];

    for (const candidate of candidates) {
      if (await this.canReach(candidate)) return candidate;
    }

    return candidates[0];
  }

  private async canReach(baseUrl: string): Promise<boolean> {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 2500);

    try {
      const response = await fetch(`${baseUrl}/api/maintenance/status`, {
        cache: 'no-store',
        signal: controller.signal
      });
      return response.ok;
    } catch {
      return false;
    } finally {
      window.clearTimeout(timeout);
    }
  }
}
