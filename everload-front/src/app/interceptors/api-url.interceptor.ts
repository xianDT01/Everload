import { Injectable } from '@angular/core';
import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiBaseService } from '../services/api-base.service';

@Injectable()
export class ApiUrlInterceptor implements HttpInterceptor {
  constructor(private apiBase: ApiBaseService) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const url = this.apiBase.withBackend(req.url);
    return next.handle(url === req.url ? req : req.clone({ url }));
  }
}
