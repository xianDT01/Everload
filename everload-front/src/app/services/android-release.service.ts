import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBaseService } from './api-base.service';

export interface AndroidRelease {
  available: boolean;
  versionName: string;
  versionCode: string;
  minAndroidVersion: string;
  releaseNotes: string;
  fileName: string;
  sizeBytes: number;
  sizeFormatted: string;
  uploadedAt: string;
  downloadUrl: string;
}

@Injectable({ providedIn: 'root' })
export class AndroidReleaseService {
  private get baseUrl(): string {
    return `${this.apiBase.backendUrl || ''}/api/app-release/android`;
  }

  constructor(private http: HttpClient, private apiBase: ApiBaseService) {}

  getRelease(): Observable<AndroidRelease> {
    return this.http.get<AndroidRelease>(this.baseUrl);
  }

  downloadApk(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/download`, { responseType: 'blob' });
  }

  uploadRelease(formData: FormData): Observable<HttpEvent<AndroidRelease>> {
    return this.http.post<AndroidRelease>(this.baseUrl, formData, {
      observe: 'events',
      reportProgress: true
    });
  }

  deleteRelease(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }

  absoluteDownloadUrl(downloadUrl: string): string {
    if (!downloadUrl) return '';
    if (/^https?:\/\//i.test(downloadUrl)) return downloadUrl;
    return `${this.apiBase.backendUrl || ''}${downloadUrl}`;
  }
}
