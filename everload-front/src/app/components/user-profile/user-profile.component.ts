import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

interface ProfileData {
  id: number;
  username: string;
  email: string;
  role: string;
  status: string;
  avatarUrl?: string;
  showLastSeen?: boolean;
  lastSeen?: string;
}

interface UpdateProfileResponse {
  user: ProfileData;
  newToken: string;
}

@Component({
  selector: 'app-user-profile',
  templateUrl: './user-profile.component.html',
  styleUrls: ['./user-profile.component.css']
})
export class UserProfileComponent implements OnInit {

  @ViewChild('avatarInput') avatarInput!: ElementRef<HTMLInputElement>;

  private get BASE(): string {
    return this.apiBase.backendUrl;
  }

  profile: ProfileData | null = null;

  // Edición de datos
  editUsername = '';
  editEmail = '';
  profileMsg = '';
  profileError = '';
  savingProfile = false;

  // Cambio de contraseña
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  passwordMsg = '';
  passwordError = '';
  savingPassword = false;

  // Privacy
  showLastSeen = true;
  privacyMsg = '';
  savingPrivacy = false;

  // Avatar
  avatarLoading = false;
  avatarError = '';

  // Navegación lateral
  activeSection: 'datos' | 'privacidad' | 'seguridad' = 'datos';

  constructor(
    private http: HttpClient,
    public authService: AuthService,
    private apiBase: ApiBaseService,
    private router: Router,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.http.get<ProfileData>(`${this.BASE}/api/user/profile`).subscribe({
      next: data => {
        this.profile = data;
        this.editUsername = data.username;
        this.editEmail = data.email;
        this.showLastSeen = data.showLastSeen !== false; // default true
      },
      error: () => this.profileError = this.translate.instant('PROFILE.ERROR_LOAD')
    });
  }

  /** Iniciales para el avatar cuando no hay foto. */
  get initials(): string {
    const name = (this.profile?.username || '').trim();
    if (!name) return '?';
    const parts = name.split(/[\s_.-]+/).filter(Boolean);
    const chars = parts.length >= 2 ? parts[0][0] + parts[1][0] : name.slice(0, 2);
    return chars.toUpperCase();
  }

  scrollTo(id: 'datos' | 'privacidad' | 'seguridad'): void {
    this.activeSection = id;
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  get avatarUrl(): string | null {
    const url = this.profile?.avatarUrl;
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.BASE}${url}`;
  }

  saveProfile(): void {
    if (!this.editUsername.trim() || !this.editEmail.trim()) {
      this.profileError = this.translate.instant('PROFILE.ERROR_USERNAME_EMAIL_REQUIRED');
      return;
    }
    this.savingProfile = true;
    this.profileMsg = '';
    this.profileError = '';

    this.http.put<UpdateProfileResponse>(`${this.BASE}/api/user/profile`, {
      username: this.editUsername.trim(),
      email: this.editEmail.trim()
    }).subscribe({
      next: res => {
        this.profile = { ...this.profile!, username: res.user.username, email: res.user.email };
        this.profileMsg = this.translate.instant('PROFILE.UPDATED');
        this.savingProfile = false;

        // Update stored user and refresh JWT so subsequent requests work with the new username
        this.authService.updateStoredUser({ username: res.user.username, email: res.user.email });
        this.authService.updateToken(res.newToken);
      },
      error: () => {
        this.profileError = this.translate.instant('PROFILE.ERROR_UPDATE');
        this.savingProfile = false;
      }
    });
  }

  changePassword(): void {
    this.passwordMsg = '';
    this.passwordError = '';

    if (!this.currentPassword || !this.newPassword || !this.confirmPassword) {
      this.passwordError = this.translate.instant('PROFILE.ERROR_ALL_FIELDS_REQUIRED');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.passwordError = this.translate.instant('PROFILE.ERROR_PASSWORDS_MISMATCH');
      return;
    }
    if (this.newPassword.length < 6) {
      this.passwordError = this.translate.instant('PROFILE.ERROR_PASSWORD_MIN_LENGTH');
      return;
    }

    this.savingPassword = true;
    this.http.put<{ message: string }>(`${this.BASE}/api/user/profile/password`, {
      currentPassword: this.currentPassword,
      newPassword: this.newPassword
    }).subscribe({
      next: () => {
        this.passwordMsg = this.translate.instant('PROFILE.PASSWORD_UPDATED');
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.savingPassword = false;
      },
      error: () => {
        this.passwordError = this.translate.instant('PROFILE.ERROR_PASSWORD_UPDATE');
        this.savingPassword = false;
      }
    });
  }

  openAvatarPicker(): void {
    this.avatarInput.nativeElement.click();
  }

  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.avatarError = '';
    this.avatarLoading = true;

    this.authService.uploadAvatar(file).subscribe({
      next: res => {
        if (this.profile) this.profile.avatarUrl = res.avatarUrl;
        this.avatarLoading = false;
        input.value = '';
      },
      error: () => {
        this.avatarError = this.translate.instant('PROFILE.ERROR_AVATAR_UPLOAD');
        this.avatarLoading = false;
        input.value = '';
      }
    });
  }

  removeAvatar(): void {
    this.avatarLoading = true;
    this.authService.removeAvatar().subscribe({
      next: () => {
        if (this.profile) this.profile.avatarUrl = undefined;
        this.avatarLoading = false;
      },
      error: () => {
        this.avatarError = this.translate.instant('PROFILE.ERROR_AVATAR_REMOVE');
        this.avatarLoading = false;
      }
    });
  }

  savePrivacy(): void {
    this.savingPrivacy = true;
    this.privacyMsg = '';
    this.http.put<any>(`${this.BASE}/api/user/profile`, {
      showLastSeen: this.showLastSeen
    }).subscribe({
      next: () => {
        this.privacyMsg = this.translate.instant('PROFILE.PRIVACY_UPDATED');
        this.savingPrivacy = false;
        if (this.profile) this.profile.showLastSeen = this.showLastSeen;
      },
      error: () => {
        this.privacyMsg = this.translate.instant('PROFILE.ERROR_PRIVACY_UPDATE');
        this.savingPrivacy = false;
      }
    });
  }

  getRoleBadge(): string {
    switch (this.profile?.role) {
      case 'ADMIN': return 'badge-admin';
      case 'NAS_USER': return 'badge-nas';
      default: return 'badge-basic';
    }
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}
