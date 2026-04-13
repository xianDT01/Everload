import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface NasPath {
  id: number;
  name: string;
  path: string;
  description: string;
  readable: boolean;
  writable: boolean;
}

export interface NasFile {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  lastModified: string;
}

@Injectable({ providedIn: 'root' })
export class NasService {

  private readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    const port = typeof window !== 'undefined' ? window.location.port : '';
    return (host === 'localhost' || host === '127.0.0.1') && port === '4200'
      ? 'http://localhost:8080'
      : '';
  })();

  constructor(private http: HttpClient) {}

  // Rutas NAS
  getPaths(): Observable<NasPath[]> {
    return this.http.get<NasPath[]>(`${this.BASE}/api/nas/paths`);
  }

  createPath(dto: Partial<NasPath>): Observable<NasPath> {
    return this.http.post<NasPath>(`${this.BASE}/api/nas/paths`, dto);
  }

  deletePath(id: number): Observable<any> {
    return this.http.delete(`${this.BASE}/api/nas/paths/${id}`);
  }

  // Explorador
  browse(pathId: number, subPath?: string): Observable<NasFile[]> {
    let params = new HttpParams();
    if (subPath) params = params.set('subPath', subPath);
    return this.http.get<NasFile[]>(`${this.BASE}/api/nas/browse/${pathId}`, { params });
  }

  mkdir(pathId: number, folderName: string, subPath?: string): Observable<any> {
    let params = new HttpParams().set('folderName', folderName);
    if (subPath) params = params.set('subPath', subPath);
    return this.http.post(`${this.BASE}/api/nas/browse/${pathId}/mkdir`, null, { params });
  }

  deleteFile(pathId: number, relativePath: string): Observable<any> {
    const params = new HttpParams().set('relativePath', relativePath);
    return this.http.delete(`${this.BASE}/api/nas/browse/${pathId}/delete`, { params });
  }

  rename(pathId: number, relativePath: string, newName: string): Observable<any> {
    const params = new HttpParams()
      .set('relativePath', relativePath)
      .set('newName', newName);
    return this.http.put(`${this.BASE}/api/nas/browse/${pathId}/rename`, null, { params });
  }

  move(pathId: number, sourcePath: string, targetFolderPath: string): Observable<any> {
    let params = new HttpParams().set('sourcePath', sourcePath);
    if (targetFolderPath) params = params.set('targetFolderPath', targetFolderPath);
    return this.http.put(`${this.BASE}/api/nas/browse/${pathId}/move`, null, { params });
  }

  updateMetadata(pathId: number, relativePath: string, title: string, artist: string): Observable<any> {
    return this.http.put(`${this.BASE}/api/music/metadata`, { pathId, relativePath, title, artist });
  }

  uploadFolderCover(pathId: number, folderPath: string, image: File): Observable<any> {
    const formData = new FormData();
    formData.append('image', image);
    let params = new HttpParams();
    if (folderPath) params = params.set('folderPath', folderPath);
    return this.http.post(`${this.BASE}/api/nas/browse/${pathId}/cover`, formData, { params });
  }
}