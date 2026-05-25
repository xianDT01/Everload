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
  private readonly fallbackArtistImages = [
    "/api/music/artist-auto-image/david_guetta.jpg",
    "/api/music/artist-auto-image/aitana.jpg",
    "/api/music/artist-auto-image/maluma.jpg",
    "/api/music/artist-auto-image/daddy_yankee.jpg",
    "/api/music/artist-auto-image/inna.jpg",
    "/api/music/artist-auto-image/sash.jpg",
  ];

  artistHeroImages = this.fallbackArtistImages.map(url => this.toCssUrl(url));
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
    const filled = images
      .filter(url => !!url)
      .slice(0, 6);
    while (filled.length < 6) {
      filled.push(this.fallbackArtistImages[filled.length % this.fallbackArtistImages.length]);
    }
    this.artistHeroImages = filled.map(url => this.toCssUrl(url));
  }

  private toCssUrl(url: string): string {
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
          this.router.navigate(['/']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.error || this.translate.instant('LOGIN.ERROR');
      }
    });
  }
}
