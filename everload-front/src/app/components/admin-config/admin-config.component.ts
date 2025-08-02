import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ViewChild, ElementRef } from '@angular/core';


@Component({
  selector: 'app-admin-config',
  templateUrl: './admin-config.component.html',
  // styleUrls: ['./admin-config.component.scss']
  styleUrls: ['./admin-config.component.css']

})
export class AdminConfigComponent implements OnInit {
  config = {
    clientId: '',
    clientSecret: '',
    apiKey: ''

  };
  intervalId: any;
  mensaje = '';
  cargando = false;

  constructor(private http: HttpClient) { }
  

  ngOnInit(): void {
    // Cargar la config una sola vez
    this.http.get<any>('http://localhost:8080/api/admin/config')
      .subscribe({
        next: data => this.config = data,
        error: err => this.mensaje = '❌ Error al cargar la configuración'
      });

    // Cargar logs e historial por primera vez
    this.cargarLogs();
    this.cargarHistorial();

    // Refrescar logs e historial cada 10 segundos
    this.intervalId = setInterval(() => {
      this.cargarLogs();
      this.cargarHistorial();
    }, 10000); // 10 segundos
  }



  guardarCambios(): void {
    this.cargando = true;
    this.http.post('http://localhost:8080/api/admin/config', this.config)
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

    this.http.post('http://localhost:8080/api/admin/update-yt-dlp', null)
      .subscribe({
        next: (data: any) => {
          this.mensaje = typeof data === 'string' ? data : '✅ Actualización completada';
          this.cargando = false;
        },
        error: () => {
          this.mensaje = '❌ Error al actualizar yt-dlp';
          this.cargando = false;
        }
      });
  }


  logs: string[] = [];
  filtroLog = '';
  @ViewChild('logContainer') logContainer!: ElementRef;

  cargarLogs(): void {
    this.http.get<string[]>(`http://localhost:8080/api/admin/logs?lines=100&filter=${this.filtroLog}`)
      .subscribe({
        next: data => {
          this.logs = data;
          setTimeout(() => {
            const element = this.logContainer.nativeElement;
            element.scrollTop = element.scrollHeight;
          }, 50); // Pequeño retraso para asegurar renderizado
        },
        error: () => this.logs = ['❌ Error al cargar logs']
      });
  }


  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  historial: any[] = [];

  cargarHistorial(): void {
    this.http.get<any[]>('http://localhost:8080/api/admin/historial')
      .subscribe({
        next: data => this.historial = data,
        error: () => this.historial = [{ titulo: '❌ Error al cargar historial' }]
      });
  }

  mensajeLimpieza: string = '';

  limpiarTemporales() {
    this.http.get('http://localhost:8080/api/admin/limpiarTemp', { responseType: 'text' })
      .subscribe({
        next: (respuesta) => {
          this.mensajeLimpieza = respuesta;
          this.cargarHistorial(); // Opcional: refresca historial tras limpiar
        },
        error: () => {
          this.mensajeLimpieza = '❌ Error al intentar limpiar temporales';
        }
      });
  }

  mensajeVaciarHistorial: string = '';

  vaciarHistorial(): void {
    this.http.delete('http://localhost:8080/api/admin/historial/vaciar', { responseType: 'text' })
      .subscribe({
        next: (respuesta) => {
          this.mensajeVaciarHistorial = respuesta;
          this.cargarHistorial(); // Refrescar tabla
        },
        error: () => {
          this.mensajeVaciarHistorial = '❌ Error al vaciar historial';
        }
      });
  }

mensajeLogLimpio: string = '';

limpiarLog(): void {
  this.http.post('http://localhost:8080/api/admin/logs/limpiar', null, { responseType: 'text' })
    .subscribe({
      next: (msg) => {
        this.mensajeLogLimpio = msg;
        this.cargarLogs(); // refrescar tras limpiar
      },
      error: () => this.mensajeLogLimpio = '❌ Error al limpiar el log'
    });
}

  getIconPath(plataforma: string): string {
    const rutas: { [key: string]: string } = {
      YouTube: '/assets/youtube-icon-logo-719479.png',
      Twitter: '/assets/twitter-icon.png',
      Facebook: '/assets/fb sin fondo.png',
      Instagram: '/assets/instagram-icon.png',
      Spotify: '/assets/Spotify.png',
      TikTok: '/assets/tiktok_logo.png'
    };
    return rutas[plataforma] || '';
  }
}

