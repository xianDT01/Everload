import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  username = '';
  email = '';
  password = '';
  confirmPassword = '';
  error = '';
  success = '';
  loading = false;
  showPassword = false;
  showConfirmPassword = false;

  constructor(private authService: AuthService, private router: Router, private translate: TranslateService) {}

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