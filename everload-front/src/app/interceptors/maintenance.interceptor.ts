import { Injectable } from '@angular/core';
import {
  HttpInterceptor, HttpRequest, HttpHandler,
  HttpEvent, HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MaintenanceService } from '../services/maintenance.service';

@Injectable()
export class MaintenanceInterceptor implements HttpInterceptor {

  constructor(private maintenanceService: MaintenanceService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 503) {
          try {
            const body = error.error;
            // Backend sets { maintenance: true, message: "..." } when in maintenance mode
            if (body && body.maintenance === true) {
              this.maintenanceService.setMaintenance(true, body.message);
            }
          } catch {
            // Not a maintenance 503 — ignore
          }
        }
        return throwError(() => error);
      })
    );
  }
}