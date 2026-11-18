import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  artistHeroImages: string[] = Array(6).fill('none');
  username = '';
  password = '';
  error = '';
  loading = false;
  showPassword = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private translate: TranslateService,
    private http: HttpClient,
    private apiBase: ApiBaseService
  ) {
    if (this.authService.isLoggedIn() && !this.authService.isPending()) {
      this.router.navigate(['/']);
    }
  }

  ngOnInit(): void {
    this.http.get<{ images: string[] }>(`${this.apiBase.backendUrl}/api/public/auth-hero-images`).subscribe({
      next: response => this.applyHeroImages(response?.images || []),
      error: () => {}
    });
  }

  private applyHeroImages(images: string[]): void {
    this.artistHeroImages = Array.from({ length: 6 }, (_, index) => this.toCssUrl(images[index] || ''));
  }

  private toCssUrl(url: string): string {
    if (!url.trim()) return 'none';
    const safe = url.startsWith('http') || url.startsWith('/') ? url : `/${url}`;
    return `url("${safe.replace(/"/g, '%22')}")`;
  }

  login(): void {
    if (!this.username || !this.password) return;
    this.loading = true;
    this.error = '';

    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.status === 'PENDING') {
          this.router.navigate(['/pending-approval']);
        } else {
          const playlistId = Number(sessionStorage.getItem('everload_playlist_link'));
          if (Number.isSafeInteger(playlistId) && playlistId > 0) {
            this.router.navigate(['/modern/playlists'], { queryParams: { playlist: playlistId } });
          } else {
            this.router.navigate(['/']);
          }
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.error || this.translate.instant('LOGIN.ERROR');
      }
    });
  }
}
