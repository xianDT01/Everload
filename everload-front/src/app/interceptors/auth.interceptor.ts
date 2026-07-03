import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { ApiBaseService } from '../services/api-base.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(
    private authService: AuthService,
    private apiBase: ApiBaseService,
    private router: Router
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // El token solo viaja al backend propio. Sin este filtro, las llamadas a APIs
    // externas (iTunes, radio-browser…) se llevaban el JWT del usuario, y un 401
    // de cualquiera de ellas cerraba la sesión.
    if (!this.isBackendRequest(req.url)) {
      return next.handle(req);
    }

    // Detect expired token before making the request to avoid mid-flight 401s
    if (this.authService.getToken() && this.authService.isTokenExpired()) {
      this.authService.logout();
      this.router.navigate(['/login']);
      return throwError(() => new Error('Session expired'));
    }

    const token = this.authService.getToken();

    const authReq = token
      ? req.clone({ headers: req.headers.set('Authorization', `Bearer ${token}`) })
      : req;

    return next.handle(authReq).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.authService.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => error);
      })
    );
  }

  /** True si la URL es relativa (misma origin) o apunta al backendUrl configurado. */
  private isBackendRequest(url: string): boolean {
    if (!/^https?:\/\//i.test(url)) return true;
    const base = this.apiBase.backendUrl;
    return !!base && url.startsWith(base);
  }
}
