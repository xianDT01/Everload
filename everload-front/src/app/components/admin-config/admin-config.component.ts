import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { NasService, NasPath } from '../../services/nas.service';

interface AdminConfig {
  clientId: string;
  clientSecret: string;
  apiKey: string;
}

interface DownloadHistoryDto {
  title: string;
  type: string;
  platform: string;
  createdAt: string;
}

interface DownloadHistoryVm {
  titulo: string;
  tipo: string;
  plataforma: string;
  fecha: string;
}

interface UserDto {
  id: number;
  username: string;
  email: string;
  role: string;
  status: string;
  avatarUrl?: string;
  createdAt: string;
}

@Component({
  selector: 'app-admin-config',
  templateUrl: './admin-config.component.html',
  styleUrls: ['./admin-config.component.css']
})
export class AdminConfigComponent implements OnInit, OnDestroy {

  activeTab: 'config' | 'users' | 'nas' | 'logs' | 'history' = 'config';

  private readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    if (host === 'localhost' || host === '127.0.0.1') return 'http://localhost:8080';
    return '';
  })();

  config: AdminConfig = { clientId: '', clientSecret: '', apiKey: '' };
  intervalId: any;
  mensaje = '';
  cargando = false;

  logs: string[] = [];
  filtroLog = '';
  @ViewChild('logContainer') logContainer!: ElementRef<HTMLDivElement>;

  historial: DownloadHistoryVm[] = [];
  mensajeLimpieza = '';
  mensajeVaciarHistorial = '';
  mensajeLogLimpio = '';

  apiEstados: { [key: string]: string } = {
    youtube: '⏳', spotify: '⏳', tiktok: '⏳', facebook: '⏳', instagram: '⏳'
  };

  // Gestión de usuarios
  pendingUsers: UserDto[] = [];
  activeUsers: UserDto[] = [];
  userMsg = '';
  roles = ['ADMIN', 'NAS_USER', 'BASIC_USER'];

  // NAS paths
  nasPaths: NasPath[] = [];
  newNasName = '';
  newNasPath = '';
  newNasDesc = '';
  nasMsg = '';

  constructor(
    private http: HttpClient,
    private translate: TranslateService,
    private nasService: NasService
  ) {
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) translate.use(savedLang);
  }

  ngOnInit(): void {
    this.http.get<AdminConfig>(`${this.BASE}/api/admin/config`).subscribe({
      next: data => this.config = data,
      error: () => this.mensaje = '❌ Error al cargar la configuración'
    });
    this.cargarLogs();
    this.cargarHistorial();
    this.loadPendingUsers();
    this.loadActiveUsers();
    this.loadNasPaths();

    this.intervalId = setInterval(() => {
      this.cargarLogs();
      this.cargarHistorial();
    }, 10_000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) { clearInterval(this.intervalId); this.intervalId = null; }
  }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  guardarCambios(): void {
    this.cargando = true;
    this.http.post(`${this.BASE}/api/admin/config`, this.config).subscribe({
      next: () => { this.mensaje = '✅ Configuración guardada'; this.cargando = false; },
      error: () => { this.mensaje = '❌ Error al guardar'; this.cargando = false; }
    });
  }

  actualizarYtDlp(): void {
    this.mensaje = '⏳ Actualizando yt-dlp...';
    this.cargando = true;
    this.http.post(`${this.BASE}/api/admin/update-yt-dlp`, null, { responseType: 'text' }).subscribe({
      next: (r) => { this.mensaje = '📦 ' + r; this.cargando = false; },
      error: (err) => { this.mensaje = '❌ ' + (err?.error || 'Error'); this.cargando = false; }
    });
  }

  cargarLogs(): void {
    const params = new HttpParams().set('lines', '100').set('filter', this.filtroLog || '');
    this.http.get<string[]>(`${this.BASE}/api/admin/logs`, { params }).subscribe({
      next: data => {
        this.logs = data || [];
        setTimeout(() => {
          if (this.logContainer?.nativeElement) {
            const el = this.logContainer.nativeElement;
            el.scrollTop = el.scrollHeight;
          }
        }, 50);
      },
      error: () => this.logs = ['❌ Error al cargar logs']
    });
  }

  cargarHistorial(): void {
    this.http.get<DownloadHistoryDto[]>(`${this.BASE}/api/admin/historial`).subscribe({
      next: data => {
        const list = Array.isArray(data) ? data : [];
        this.historial = list.map(d => ({
          titulo: d.title, tipo: d.type, plataforma: d.platform, fecha: d.createdAt
        }));
      },
      error: () => this.historial = [{ titulo: '❌ Error', tipo: '', plataforma: '', fecha: '' }]
    });
  }

  limpiarTemporales(): void {
    this.http.get(`${this.BASE}/api/admin/limpiarTemp`, { responseType: 'text' }).subscribe({
      next: (r) => { this.mensajeLimpieza = r; this.cargarHistorial(); },
      error: () => this.mensajeLimpieza = '❌ Error al limpiar temporales'
    });
  }

  vaciarHistorial(): void {
    this.http.delete(`${this.BASE}/api/admin/historial/vaciar`, { responseType: 'text' }).subscribe({
      next: (r) => { this.mensajeVaciarHistorial = r; this.cargarHistorial(); },
      error: () => this.mensajeVaciarHistorial = '❌ Error al vaciar historial'
    });
  }

  limpiarLog(): void {
    this.http.post(`${this.BASE}/api/admin/logs/clear`, null, { responseType: 'text' }).subscribe({
      next: (msg) => { this.mensajeLogLimpio = msg; this.cargarLogs(); },
      error: () => this.mensajeLogLimpio = '❌ Error al limpiar el log'
    });
  }

  comprobarApis(): void {
    const endpoints: Record<string, string> = {
      youtube: `${this.BASE}/api/admin/test-api/youtube`,
      spotify: `${this.BASE}/api/admin/test-api/spotify`,
      tiktok: `${this.BASE}/api/admin/test-api/tiktok`,
      facebook: `${this.BASE}/api/admin/test-api/facebook`,
      instagram: `${this.BASE}/api/admin/test-api/instagram`
    };
    for (const [clave, url] of Object.entries(endpoints)) {
      this.http.get<any>(url).subscribe({
        next: (r) => {
          const statusKey = r?.status === 'ok' ? 'API_OK' : 'API_ERROR';
          this.apiEstados[clave] = this.translate.instant(statusKey, { platform: r?.platform || clave });
        },
        error: () => this.apiEstados[clave] = this.translate.instant('API_ERROR', { platform: clave })
      });
    }
  }

  // ── Gestión de usuarios ────────────────────────────────────────────────────

  loadPendingUsers(): void {
    this.http.get<UserDto[]>(`${this.BASE}/api/admin/users/pending`).subscribe({
      next: data => this.pendingUsers = data,
      error: () => {}
    });
  }

  loadActiveUsers(): void {
    this.http.get<UserDto[]>(`${this.BASE}/api/admin/users/active`).subscribe({
      next: data => this.activeUsers = data,
      error: () => {}
    });
  }

  approveUser(user: UserDto, role: string): void {
    this.http.put(`${this.BASE}/api/admin/users/${user.id}`, { role, status: 'ACTIVE' }).subscribe({
      next: () => {
        this.userMsg = `✅ ${user.username} aprobado como ${role}`;
        this.loadPendingUsers();
        this.loadActiveUsers();
      },
      error: () => this.userMsg = '❌ Error al aprobar usuario'
    });
  }

  rejectUser(user: UserDto): void {
    this.http.put(`${this.BASE}/api/admin/users/${user.id}`, { status: 'REJECTED' }).subscribe({
      next: () => {
        this.userMsg = `❌ ${user.username} rechazado`;
        this.loadPendingUsers();
      },
      error: () => this.userMsg = '❌ Error al rechazar'
    });
  }

  changeRole(user: UserDto, role: string): void {
    this.http.put(`${this.BASE}/api/admin/users/${user.id}`, { role }).subscribe({
      next: () => {
        this.userMsg = `✅ Rol de ${user.username} cambiado a ${role}`;
        user.role = role;
      },
      error: () => this.userMsg = '❌ Error al cambiar rol'
    });
  }

  revokeUser(user: UserDto): void {
    if (!confirm(`¿Revocar acceso de ${user.username}?`)) return;
    this.http.post(`${this.BASE}/api/admin/users/${user.id}/revoke`, null).subscribe({
      next: () => {
        this.userMsg = `✅ Acceso revocado a ${user.username}`;
        this.loadActiveUsers();
      },
      error: () => this.userMsg = '❌ Error al revocar acceso'
    });
  }

  deleteUser(user: UserDto): void {
    if (!confirm(`¿Eliminar usuario ${user.username}?`)) return;
    this.http.delete(`${this.BASE}/api/admin/users/${user.id}`).subscribe({
      next: () => {
        this.userMsg = `✅ Usuario ${user.username} eliminado`;
        this.loadPendingUsers();
        this.loadActiveUsers();
      },
      error: () => this.userMsg = '❌ Error al eliminar usuario'
    });
  }

  // ── NAS Paths ──────────────────────────────────────────────────────────────

  loadNasPaths(): void {
    this.nasService.getPaths().subscribe({
      next: paths => this.nasPaths = paths,
      error: () => {}
    });
  }

  addNasPath(): void {
    if (!this.newNasName || !this.newNasPath) {
      this.nasMsg = '❌ Nombre y ruta son obligatorios';
      return;
    }
    this.nasService.createPath({
      name: this.newNasName,
      path: this.newNasPath,
      description: this.newNasDesc
    }).subscribe({
      next: () => {
        this.nasMsg = '✅ Ruta NAS añadida';
        this.newNasName = '';
        this.newNasPath = '';
        this.newNasDesc = '';
        this.loadNasPaths();
      },
      error: (err) => this.nasMsg = '❌ ' + (err.error?.error || 'Error al añadir ruta')
    });
  }

  removeNasPath(id: number): void {
    if (!confirm('¿Eliminar esta ruta NAS?')) return;
    this.nasService.deletePath(id).subscribe({
      next: () => { this.nasMsg = '✅ Ruta eliminada'; this.loadNasPaths(); },
      error: () => this.nasMsg = '❌ Error al eliminar ruta'
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  getFullAvatarUrl(url: string | null): string {
    if (!url) return '';
    return url.startsWith('http') ? url : `${this.BASE}${url}`;
  }

  getIconPath(plataforma: string): string {
    const rutas: Record<string, string> = {
      YouTube: '/assets/youtube-icon-logo-719479.png',
      Twitter: '/assets/twitter-icon.png',
      Facebook: '/assets/fb sin fondo.png',
      Instagram: '/assets/instagram-icon.png',
      Spotify: '/assets/Spotify.png',
      TikTok: '/assets/tiktok_logo.png'
    };
    return rutas[plataforma] || '';
  }

  trackByHistorial(_index: number, item: DownloadHistoryVm) {
    return `${item.titulo}-${item.fecha}`;
  }
}