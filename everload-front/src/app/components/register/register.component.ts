import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent implements OnInit {
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
  email = '';
  password = '';
  confirmPassword = '';
  error = '';
  success = '';
  loading = false;
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private translate: TranslateService,
    private http: HttpClient,
    private apiBase: ApiBaseService
  ) {}

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

  register(): void {
    if (!this.username || !this.email || !this.password) {
      this.error = this.translate.instant('REGISTER.ERROR_FILL_ALL');
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.error = this.translate.instant('REGISTER.ERROR_PASSWORDS_MISMATCH');
      return;
    }
    if (this.password.length < 6) {
      this.error = this.translate.instant('REGISTER.ERROR_PASSWORD_TOO_SHORT');
      return;
    }

    this.loading = true;
    this.error = '';

    this.authService.register({ username: this.username, email: this.email, password: this.password }).subscribe({
      next: () => {
        this.loading = false;
        this.success = this.translate.instant('REGISTER.SUCCESS');
        setTimeout(() => this.router.navigate(['/login']), 3000);
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.error || this.translate.instant('REGISTER.ERROR');
      }
    });
  }
}
