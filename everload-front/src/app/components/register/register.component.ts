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
  artistHeroImages: string[] = Array(6).fill('none');
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
    this.artistHeroImages = Array.from({ length: 6 }, (_, index) => this.toCssUrl(images[index] || ''));
  }

  private toCssUrl(url: string): string {
    if (!url.trim()) return 'none';
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
