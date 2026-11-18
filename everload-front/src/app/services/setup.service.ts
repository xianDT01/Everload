import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiBaseService } from './api-base.service';

export interface SetupStorage {
  root: string; relativePath: string; readable: boolean; writable: boolean;
  folders: { name: string; relativePath: string }[];
}
export interface SetupLibrary { id: number; name: string; path: string; readable: boolean; writable: boolean; }
export type SetupPlatform = 'spotify' | 'youtube' | 'acoustid';

@Injectable({ providedIn: 'root' })
export class SetupService {
  constructor(private http: HttpClient, private api: ApiBaseService) {}
  private get base(): string { return `${this.api.backendUrl}/api/setup`; }
  status() { return this.http.get<{ required: boolean }>(`${this.base}/status`); }
  configuration() {
    return this.http.get<{ libraries: SetupLibrary[]; services: Record<SetupPlatform, boolean> }>(`${this.base}/configuration`);
  }
  storage(path = '') { return this.http.get<SetupStorage>(`${this.base}/storage`, { params: { path } }); }
  library(name: string, relativePath: string) {
    return this.http.post<SetupLibrary>(`${this.base}/library`, { name, relativePath });
  }
  save(platform: SetupPlatform, values: Record<string, string>) {
    return this.http.post<{ status: string }>(`${this.base}/integrations/${platform}`, values);
  }
  verify(platform: SetupPlatform) {
    return this.http.post<{ status: string }>(`${this.base}/integrations/${platform}/test`, {});
  }
}
