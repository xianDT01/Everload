import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

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

@Component({
  selector: 'app-admin-config',
  templateUrl: './admin-config.component.html',
  styleUrls: ['./admin-config.component.css']
})
export class AdminConfigComponent implements OnInit, OnDestroy {

  // 🔧 Si tienes environments, sustituye por environment.apiUrl
  private readonly BASE = 'http://localhost:8080';

  config: AdminConfig = {
    clientId: '',
    clientSecret: '',
    apiKey: ''
  };

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
    backend: '⏳',
    youtube: '⏳',
    spotify: '⏳',
    tiktok: '⏳',
    facebook: '⏳',
    instagram: '⏳'
  };

  constructor(private http: HttpClient, private translate: TranslateService) {
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) translate.use(savedLang);
  }

  ngOnInit(): void {
    // Config
    this.http.get<AdminConfig>(`${this.BASE}/api/admin/config`)
      .subscribe({
        next: data => this.config = data,
        error: () => this.mensaje = '❌ Error al cargar la configuración'
      });

    // Cargar logs e historial inicialmente
    this.cargarLogs();
    this.cargarHistorial();

    // Refrescar cada 10s
    this.intervalId = setInterval(() => {
      this.cargarLogs();
      this.cargarHistorial();
    }, 10_000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  guardarCambios(): void {
    this.cargando = true;
    this.http.post(`${this.BASE}/api/admin/config`, this.config)
      .subscribe({
        next: () => {
          this.mensaje = '✅ Configuración guardada correctamente';
          this.cargando = false;
        },
        error: () => {
          this.mensaje = '❌ Error al guardar la configuración';
          this.cargando = false;
        }
      });
  }

  actualizarYtDlp(): void {
    this.mensaje = '⏳ Actualizando yt-dlp...';
    this.cargando = true;

    this.http.post(`${this.BASE}/api/admin/update-yt-dlp`, null, { responseType: 'text' })
      .subscribe({
        next: (respuesta) => {
          this.mensaje = '📦 ' + respuesta;
          this.cargando = false;
        },
        error: (error) => {
          if (error?.error) {
            this.mensaje = '❌ ' + error.error;
          } else {
            this.mensaje = '❌ Error desconocido al actualizar yt-dlp';
          }
          this.cargando = false;
        }
      });
  }

  cargarLogs(): void {
    const params = new HttpParams()
      .set('lines', '100')
      .set('filter', this.filtroLog || '');

    this.http.get<string[]>(`${this.BASE}/api/admin/logs`, { params })
      .subscribe({
        next: data => {
          this.logs = data || [];
          // Asegurar scroll al final tras render
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
    // El backend tiene alias: /api/admin/history y /api/admin/historial
    // Aquí mantenemos el español para compatibilidad.
    this.http.get<DownloadHistoryDto[]>(`${this.BASE}/api/admin/historial`)
      .subscribe({
        next: data => {
          const list = Array.isArray(data) ? data : [];
          // Mapear a las claves que espera tu template (en español)
          this.historial = list.map(d => ({
            titulo: d.title,
            tipo: d.type,
            plataforma: d.platform,
            fecha: d.createdAt
          }));
        },
        error: () => {
          this.historial = [{ titulo: '❌ Error al cargar historial', tipo: '', plataforma: '', fecha: '' }];
        }
      });
  }

  limpiarTemporales(): void {
    // Alias en backend: { "/clear-temp", "/limpiarTemp" } -> mantenemos español
    this.http.get(`${this.BASE}/api/admin/limpiarTemp`, { responseType: 'text' })
      .subscribe({
        next: (respuesta) => {
          this.mensajeLimpieza = respuesta;
          // Si quieres, refresca historial o stats
          this.cargarHistorial();
        },
        error: () => {
          this.mensajeLimpieza = '❌ Error al intentar limpiar temporales';
        }
      });
  }

  vaciarHistorial(): void {
    // Alias en backend: /history/clear y /historial/vaciar -> mantenemos español
    this.http.delete(`${this.BASE}/api/admin/historial/vaciar`, { responseType: 'text' })
      .subscribe({
        next: (respuesta) => {
          this.mensajeVaciarHistorial = respuesta;
          this.cargarHistorial();
        },
        error: () => {
          this.mensajeVaciarHistorial = '❌ Error al vaciar historial';
        }
      });
  }

  limpiarLog(): void {
    // Backend renombrado a /logs/clear; si añadiste alias /limpiar, puedes dejar el antiguo.
    this.http.post(`${this.BASE}/api/admin/logs/clear`, null, { responseType: 'text' })
      .subscribe({
        next: (msg) => {
          this.mensajeLogLimpio = msg;
          this.cargarLogs();
        },
        error: () => this.mensajeLogLimpio = '❌ Error al limpiar el log'
      });
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
        next: (respuesta) => {
          const platform = respuesta?.platform || clave;
          const statusKey = respuesta?.status === 'ok' ? 'API_OK' : 'API_ERROR';
          this.apiEstados[clave] = this.translate.instant(statusKey, { platform });
        },
        error: () => {
          this.apiEstados[clave] = this.translate.instant('API_ERROR', { platform: clave });
        }
      });
    }
  }

  // Útil para *ngFor trackBy en historial
  trackByHistorial(_index: number, item: DownloadHistoryVm) {
    return `${item.titulo}-${item.fecha}`;
  }
}
