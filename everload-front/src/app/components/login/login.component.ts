import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  constructor(private authService: AuthService, private router: Router, private translate: TranslateService) {
    if (this.authService.isLoggedIn() && !this.authService.isPending()) {
      this.router.navigate(['/']);
    }
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