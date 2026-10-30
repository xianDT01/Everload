import { Injectable } from '@angular/core';
import {
  HttpInterceptor, HttpRequest, HttpHandler,
  HttpEvent, HttpErrorResponse
} from '@angular/common/http';
import { from, Observable, throwError } from 'rxjs';
import { catchError, mergeMap } from 'rxjs/operators';
import { MaintenanceService } from '../services/maintenance.service';

@Injectable()
export class MaintenanceInterceptor implements HttpInterceptor {

  constructor(private maintenanceService: MaintenanceService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 503) {
          return from(this.recordMaintenance(error.error)).pipe(
            mergeMap(() => throwError(() => error))
          );
        }
        return throwError(() => error);
      })
    );
  }

  private async recordMaintenance(payload: unknown): Promise<void> {
    try {
      let body: any = payload;
      if (body instanceof Blob) body = await body.text();
      if (typeof body === 'string') body = JSON.parse(body);
      if (body?.maintenance === true) {
        this.maintenanceService.setMaintenance(true,
          typeof body.message === 'string' ? body.message : undefined);
      }
    } catch { /* Ordinary overload/proxy responses are not maintenance announcements. */ }
  }
}
