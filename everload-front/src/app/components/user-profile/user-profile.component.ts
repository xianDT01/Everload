import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';

interface ProfileData {
  id: number;
  username: string;
  email: string;
  role: string;
  status: string;
  avatarUrl?: string;
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

  private readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

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

  // Avatar
  avatarLoading = false;
  avatarError = '';

  constructor(
    private http: HttpClient,
    public authService: AuthService,
    private router: Router
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
      },
      error: () => this.profileError = 'Error al cargar el perfil'
    });
  }

  get avatarUrl(): string | null {
    const url = this.profile?.avatarUrl;
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.BASE}${url}`;
  }

  saveProfile(): void {
    if (!this.editUsername.trim() || !this.editEmail.trim()) {
      this.profileError = 'El nombre y el email no pueden estar vacíos';
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
        this.profileMsg = 'Perfil actualizado correctamente';
        this.savingProfile = false;

        // Update stored user and refresh JWT so subsequent requests work with the new username
        this.authService.updateStoredUser({ username: res.user.username, email: res.user.email });
        this.authService.updateToken(res.newToken);
      },
      error: err => {
        this.profileError = err.error?.error || 'Error al actualizar el perfil';
        this.savingProfile = false;
      }
    });
  }

  changePassword(): void {
    this.passwordMsg = '';
    this.passwordError = '';

    if (!this.currentPassword || !this.newPassword || !this.confirmPassword) {
      this.passwordError = 'Rellena todos los campos';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.passwordError = 'Las contraseñas nuevas no coinciden';
      return;
    }
    if (this.newPassword.length < 6) {
      this.passwordError = 'La nueva contraseña debe tener al menos 6 caracteres';
      return;
    }

    this.savingPassword = true;
    this.http.put<{ message: string }>(`${this.BASE}/api/user/profile/password`, {
      currentPassword: this.currentPassword,
      newPassword: this.newPassword
    }).subscribe({
      next: res => {
        this.passwordMsg = res.message;
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.savingPassword = false;
      },
      error: err => {
        this.passwordError = err.error?.error || 'Error al cambiar la contraseña';
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
      error: err => {
        this.avatarError = err.error?.error || 'Error al subir la imagen';
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
        this.avatarError = 'Error al eliminar el avatar';
        this.avatarLoading = false;
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