import { Injectable } from '@angular/core';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard  {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(_route?: ActivatedRouteSnapshot, state?: RouterStateSnapshot): boolean {
    if (!this.authService.isLoggedIn()) {
      if (state?.url.startsWith('/modern/playlists?')) {
        const id = Number(this.router.parseUrl(state.url).queryParams['playlist']);
        if (Number.isSafeInteger(id) && id > 0) sessionStorage.setItem('everload_playlist_link', String(id));
      }
      this.router.navigate(['/login']);
      return false;
    }
    if (this.authService.isPending()) {
      this.router.navigate(['/pending-approval']);
      return false;
    }
    return true;
  }
}
