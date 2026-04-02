import { Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService, AuthResponse } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {

  @ViewChild('avatarInput') avatarInput!: ElementRef<HTMLInputElement>;

  menuOpen = false;
  currentUser: AuthResponse | null = null;
  avatarUrl: string | null = null;
  avatarError = '';
  avatarLoading = false;

  constructor(
    private translate: TranslateService,
    public authService: AuthService,
    private router: Router
  ) {
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) translate.use(savedLang);

    // Suscribirse para reflejar cambios de avatar en tiempo real
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.avatarUrl = this.authService.getAvatarUrl();
    });
  }

  get isAdmin(): boolean { return this.authService.isAdmin(); }
  get hasNasAccess(): boolean { return this.authService.hasNasAccess(); }

  toggleMenu(): void { this.menuOpen = !this.menuOpen; }
  closeMenu(): void { this.menuOpen = false; }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.closeMenu(); }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ── Avatar ────────────────────────────────────────────────────────────────

  openAvatarPicker(): void {
    this.avatarInput.nativeElement.click();
  }

  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const allowed = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    if (!allowed.includes(file.type)) {
      this.avatarError = 'Solo se permiten JPG, PNG, WebP o GIF';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.avatarError = 'El archivo no puede superar 5 MB';
      return;
    }

    this.avatarError = '';
    this.avatarLoading = true;
    this.authService.uploadAvatar(file).subscribe({
      next: () => { this.avatarLoading = false; },
      error: (err) => {
        this.avatarLoading = false;
        this.avatarError = err.error?.error || 'Error al subir el avatar';
      }
    });
    // Limpiar el input para que se pueda reseleccionar el mismo fichero
    input.value = '';
  }

  removeAvatar(): void {
    if (!confirm('¿Eliminar tu foto de perfil?')) return;
    this.avatarLoading = true;
    this.authService.removeAvatar().subscribe({
      next: () => { this.avatarLoading = false; },
      error: () => { this.avatarLoading = false; }
    });
  }

  getRoleBadgeClass(): string {
    const role = this.currentUser?.role;
    if (role === 'ADMIN') return 'badge-admin';
    if (role === 'NAS_USER') return 'badge-nas';
    return 'badge-basic';
  }
}