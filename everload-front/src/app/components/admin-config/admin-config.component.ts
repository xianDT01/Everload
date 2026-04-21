import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { NasService, NasPath } from '../../services/nas.service';

// ── Sistema tab interfaces ─────────────────────────────────────────────────────

interface MaintenanceStatus {
  active: boolean;
  message: string;
}

interface BackupDto {
  name: string;
  sizeBytes: number;
  sizeFormatted: string;
  createdAt: string;
}

interface SystemInfoDto {
  appVersion: string;
  currentCommit?: string;
  javaVersion: string;
  uptimeSeconds: number;
  dbPath: string;
  dbSizeBytes: number;
  dbSizeFormatted: string;
}

interface UpdateCheckDto {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  releaseUrl: string;
  releaseNotes: string;
  checkConfigured: boolean;
  error: string;
  currentCommit?: string;
  latestCommit?: string;
  commitMessage?: string;
  commitDate?: string;
  commitUrl?: string;
}

// ─────────────────────────────────────────────────────────────────────────────

interface AdminConfig {
  clientId: string;
  clientSecret: string;
  apiKey: string;
  acoustidApiKey: string;
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
  online?: boolean;
  lastSeen?: string;
  showLastSeen?: boolean;
}

interface AdminChatGroup {
  id: number;
  name: string;
  description?: string;
  type: string;
  createdAt: string;
  memberCount: number;
  messageCount: number;
  lastMessage?: string;
  lastMessageTime?: string;
  createdByUsername?: string;
}

interface AdminChatMessage {
  id: number;
  senderUsername: string;
  content: string;
  messageType: string;
  videoTitle?: string;
  sentAt: string;
}

interface AdminChatMember {
  username: string;
  role: string;
  avatarUrl?: string;
  joinedAt: string;
}

@Component({
  selector: 'app-admin-config',
  templateUrl: './admin-config.component.html',
  styleUrls: ['./admin-config.component.css']
})
export class AdminConfigComponent implements OnInit, OnDestroy {

  activeTab: 'config' | 'users' | 'nas' | 'logs' | 'history' | 'chat' | 'audit' | 'sistema' = 'config';

  private readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    if (host === 'localhost' || host === '127.0.0.1') return 'http://localhost:8080';
    return '';
  })();

  config: AdminConfig = { clientId: '', clientSecret: '', apiKey: '', acoustidApiKey: '' };
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

  themes = [
    { id: 'default', color: '#2a3b5f' },
    { id: 'ocean',   color: '#1c3645' },
    { id: 'forest',  color: '#1e7085' },
    { id: 'sunset',  color: '#3d5bc4' },
    { id: 'dark',    color: '#242424' }
  ];
  currentTheme = 'default';
  showThemePicker = false;


  constructor(
    private http: HttpClient,
    private translate: TranslateService,
    private nasService: NasService
  ) {
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) translate.use(savedLang);

    const savedTheme = localStorage.getItem('admin_theme');
    if (savedTheme) {
      this.currentTheme = savedTheme;
    }
  }

  ngOnInit(): void {
    this.http.get<AdminConfig>(`${this.BASE}/api/admin/config`).subscribe({
      next: data => this.config = data,
      error: () => this.mensaje = '❌ ' + this.translate.instant('ADMIN.FORM_LOAD_ERROR')
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

  toggleThemePicker() {
    this.showThemePicker = !this.showThemePicker;
  }

  applyTheme(themeId: string) {
    this.currentTheme = themeId;
    localStorage.setItem('admin_theme', themeId);
  }

  guardarCambios(): void {
    this.cargando = true;
    this.http.post(`${this.BASE}/api/admin/config`, this.config).subscribe({
      next: () => { this.mensaje = this.translate.instant('ADMIN.FORM_SAVE_SUCCESS'); this.cargando = false; },
      error: () => { this.mensaje = this.translate.instant('ADMIN.FORM_SAVE_ERROR'); this.cargando = false; }
    });
  }

  actualizarYtDlp(): void {
    this.mensaje = this.translate.instant('ADMIN.YTDLP_UPDATING');
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
      error: () => this.logs = ['❌ ' + this.translate.instant('ADMIN.LOG_LOAD_ERROR')]
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
      error: () => this.mensajeLimpieza = this.translate.instant('ADMIN.TEMP_CLEAN_ERROR')
    });
  }

  vaciarHistorial(): void {
    this.http.delete(`${this.BASE}/api/admin/historial/vaciar`, { responseType: 'text' }).subscribe({
      next: (r) => { this.mensajeVaciarHistorial = r; this.cargarHistorial(); },
      error: () => this.mensajeVaciarHistorial = this.translate.instant('ADMIN.HISTORY_CLEAR_ERROR')
    });
  }

  limpiarLog(): void {
    this.http.post(`${this.BASE}/api/admin/logs/clear`, null, { responseType: 'text' }).subscribe({
      next: (msg) => { this.mensajeLogLimpio = msg; this.cargarLogs(); },
      error: () => this.mensajeLogLimpio = this.translate.instant('ADMIN.LOG_CLEAR_ERROR')
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
        this.userMsg = this.translate.instant('ADMIN.USER_APPROVED', { username: user.username, role });
        this.loadPendingUsers();
        this.loadActiveUsers();
      },
      error: () => this.userMsg = '❌ ' + this.translate.instant('ADMIN.USER_APPROVE_ERROR')
    });
  }

  rejectUser(user: UserDto): void {
    this.http.put(`${this.BASE}/api/admin/users/${user.id}`, { status: 'REJECTED' }).subscribe({
      next: () => {
        this.userMsg = this.translate.instant('ADMIN.USER_REJECTED', { username: user.username });
        this.loadPendingUsers();
      },
      error: () => this.userMsg = '❌ ' + this.translate.instant('ADMIN.USER_REJECT_ERROR')
    });
  }

  changeRole(user: UserDto, role: string): void {
    this.http.put(`${this.BASE}/api/admin/users/${user.id}`, { role }).subscribe({
      next: () => {
        this.userMsg = this.translate.instant('ADMIN.ROLE_CHANGED', { username: user.username, role });
        user.role = role;
      },
      error: () => this.userMsg = '❌ ' + this.translate.instant('ADMIN.USER_ROLE_ERROR')
    });
  }

  revokeUser(user: UserDto): void {
    if (!confirm(`¿Revocar acceso de ${user.username}?`)) return;
    this.http.post(`${this.BASE}/api/admin/users/${user.id}/revoke`, null).subscribe({
      next: () => {
        this.userMsg = this.translate.instant('ADMIN.USER_REVOKED', { username: user.username });
        this.loadActiveUsers();
      },
      error: () => this.userMsg = '❌ ' + this.translate.instant('ADMIN.USER_REVOKE_ERROR')
    });
  }

  deleteUser(user: UserDto): void {
    if (!confirm(`¿Eliminar usuario ${user.username}?`)) return;
    this.http.delete(`${this.BASE}/api/admin/users/${user.id}`).subscribe({
      next: () => {
        this.userMsg = this.translate.instant('ADMIN.USER_DELETED_SUCCESS', { username: user.username });
        this.loadPendingUsers();
        this.loadActiveUsers();
      },
      error: () => this.userMsg = '❌ ' + this.translate.instant('ADMIN.USER_DELETE_ERROR')
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
    if (!this.newNasName) {
      this.nasMsg = '❌ El nombre es obligatorio';
      return;
    }
    this.nasService.createPath({
      name: this.newNasName,
      path: this.newNasPath || '.',
      description: this.newNasDesc
    }).subscribe({
      next: () => {
        this.nasMsg = this.translate.instant('ADMIN.NAS_PATH_ADDED');
        this.newNasName = '';
        this.newNasPath = '';
        this.newNasDesc = '';
        this.loadNasPaths();
      },
      error: (err) => this.nasMsg = '❌ ' + (err.error?.error || this.translate.instant('ADMIN.NAS_ERROR_ADD'))
    });
  }

  removeNasPath(id: number): void {
    if (!confirm('¿Eliminar esta ruta NAS?')) return;
    this.nasService.deletePath(id).subscribe({
      next: () => { this.nasMsg = this.translate.instant('ADMIN.NAS_PATH_DELETED'); this.loadNasPaths(); },
      error: () => this.nasMsg = '❌ ' + this.translate.instant('ADMIN.NAS_ERROR_DELETE')
    });
  }

  // ── Moderación de chat ────────────────────────────────────────────────────

  chatGroups: AdminChatGroup[] = [];
  chatGroupSearch = '';
  chatMsg = '';
  selectedChatGroup: AdminChatGroup | null = null;
  chatGroupMessages: AdminChatMessage[] = [];
  chatGroupMembers: AdminChatMember[] = [];
  chatMessageSearch = '';
  loadingChatDetail = false;

  loadChatGroups(): void {
    this.http.get<AdminChatGroup[]>(`${this.BASE}/api/admin/chat/groups`).subscribe({
      next: data => this.chatGroups = data.sort((a, b) => (b.lastMessageTime ?? '').localeCompare(a.lastMessageTime ?? '')),
      error: () => this.chatMsg = '❌ ' + this.translate.instant('ADMIN.CHAT_ERROR_LOAD')
    });
  }

  selectChatGroup(group: AdminChatGroup): void {
    if (this.selectedChatGroup?.id === group.id) {
      this.selectedChatGroup = null;
      return;
    }
    this.selectedChatGroup = group;
    this.chatGroupMessages = [];
    this.chatGroupMembers = [];
    this.chatMessageSearch = '';
    this.loadingChatDetail = true;

    this.http.get<AdminChatMessage[]>(`${this.BASE}/api/admin/chat/groups/${group.id}/messages`).subscribe({
      next: msgs => { this.chatGroupMessages = msgs; this.loadingChatDetail = false; },
      error: () => { this.loadingChatDetail = false; }
    });
    this.http.get<AdminChatMember[]>(`${this.BASE}/api/admin/chat/groups/${group.id}/members`).subscribe({
      next: members => this.chatGroupMembers = members,
      error: () => {}
    });
  }

  deleteChatGroup(group: AdminChatGroup, event: Event): void {
    event.stopPropagation();
    if (!confirm(this.translate.instant('ADMIN.CHAT_CONFIRM_DELETE_GROUP', { name: group.name }))) return;
    this.http.delete(`${this.BASE}/api/admin/chat/groups/${group.id}`).subscribe({
      next: () => {
        this.chatMsg = '✅ ' + this.translate.instant('ADMIN.CHAT_GROUP_DELETED', { name: group.name });
        if (this.selectedChatGroup?.id === group.id) this.selectedChatGroup = null;
        this.loadChatGroups();
      },
      error: () => this.chatMsg = '❌ ' + this.translate.instant('ADMIN.CHAT_ERROR_DELETE_GROUP')
    });
  }

  deleteChatMessage(msg: AdminChatMessage): void {
    if (!confirm(this.translate.instant('ADMIN.CHAT_CONFIRM_DELETE_MSG'))) return;
    this.http.delete(`${this.BASE}/api/admin/chat/messages/${msg.id}`).subscribe({
      next: () => {
        this.chatGroupMessages = this.chatGroupMessages.filter(m => m.id !== msg.id);
        if (this.selectedChatGroup) this.selectedChatGroup.messageCount--;
      },
      error: () => this.chatMsg = '❌ ' + this.translate.instant('ADMIN.CHAT_ERROR_DELETE_MSG')
    });
  }

  removeChatMember(username: string): void {
    if (!this.selectedChatGroup) return;
    if (!confirm(this.translate.instant('ADMIN.CHAT_CONFIRM_KICK', { username }))) return;
    this.http.delete(`${this.BASE}/api/admin/chat/groups/${this.selectedChatGroup.id}/members/${username}`).subscribe({
      next: () => {
        this.chatGroupMembers = this.chatGroupMembers.filter(m => m.username !== username);
        if (this.selectedChatGroup) this.selectedChatGroup.memberCount--;
      },
      error: () => this.chatMsg = '❌ ' + this.translate.instant('ADMIN.CHAT_ERROR_KICK')
    });
  }

  getChatMessagePreview(msg: AdminChatMessage): string {
    if (msg.messageType === 'YOUTUBE_SHARE') return '🎬 ' + (msg.videoTitle || 'Vídeo de YouTube');
    return msg.content;
  }

  getGroupTypeBadge(type: string): string {
    if (type === 'PRIVATE') return '👤';
    if (type === 'ANNOUNCEMENT') return '📢';
    return '👥';
  }

  get filteredChatGroups(): AdminChatGroup[] {
    if (!this.chatGroupSearch.trim()) return this.chatGroups;
    const q = this.chatGroupSearch.toLowerCase();
    return this.chatGroups.filter(g =>
      g.name.toLowerCase().includes(q) ||
      g.type.toLowerCase().includes(q) ||
      (g.createdByUsername ?? '').toLowerCase().includes(q)
    );
  }

  get filteredChatMessages(): AdminChatMessage[] {
    if (!this.chatMessageSearch.trim()) return this.chatGroupMessages;
    const q = this.chatMessageSearch.toLowerCase();
    return this.chatGroupMessages.filter(m =>
      m.senderUsername.toLowerCase().includes(q) ||
      m.content.toLowerCase().includes(q)
    );
  }

  // ── Audit Log ─────────────────────────────────────────────────────────────

  auditLogs: any[] = [];
  auditPage = 0;
  auditTotalPages = 0;
  auditTotalElements = 0;
  auditSearch = '';
  auditLoading = false;
  readonly AUDIT_PAGE_SIZE = 50;

  loadAuditLogs(page = 0): void {
    this.auditLoading = true;
    let url = `${this.BASE}/api/admin/audit?page=${page}&size=${this.AUDIT_PAGE_SIZE}`;
    if (this.auditSearch.trim()) url += `&search=${encodeURIComponent(this.auditSearch.trim())}`;
    this.http.get<any>(url).subscribe({
      next: data => {
        this.auditLogs = data.content || [];
        this.auditPage = data.number ?? 0;
        this.auditTotalPages = data.totalPages ?? 0;
        this.auditTotalElements = data.totalElements ?? 0;
        this.auditLoading = false;
      },
      error: () => { this.auditLoading = false; }
    });
  }

  auditNextPage(): void {
    if (this.auditPage < this.auditTotalPages - 1) this.loadAuditLogs(this.auditPage + 1);
  }

  auditPrevPage(): void {
    if (this.auditPage > 0) this.loadAuditLogs(this.auditPage - 1);
  }

  onAuditSearchChange(): void {
    this.loadAuditLogs(0);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  getFullAvatarUrl(url: string | null): string {
    if (!url) return '';
    return url.startsWith('http') ? url : `${this.BASE}${url}`;
  }

  formatLastSeen(dateStr?: string): string {
    if (!dateStr) return '—';
    const date = new Date(dateStr);
    const diff = Math.floor((Date.now() - date.getTime()) / 1000);
    if (diff < 60) return this.translate.instant('ADMIN.LAST_SEEN_MOMENT');
    if (diff < 3600) return this.translate.instant('ADMIN.LAST_SEEN_MIN', { count: Math.floor(diff / 60) });
    if (diff < 86400) return this.translate.instant('ADMIN.LAST_SEEN_HOUR', { count: Math.floor(diff / 3600) });
    return date.toLocaleDateString(this.translate.currentLang === 'es' ? 'es-ES' : (this.translate.currentLang === 'gl' ? 'gl-ES' : 'en-GB'), 
      { day: '2-digit', month: 'short', year: '2-digit' });
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

  // ══ TAB: SISTEMA ═════════════════════════════════════════════════════════════

  // ── Maintenance ───────────────────────────────────────────────────────────────
  maintenance: MaintenanceStatus = { active: false, message: '' };
  maintenanceNewMessage = '';
  maintenanceLoading = false;
  maintenanceMsg = '';

  loadMaintenance(): void {
    this.http.get<MaintenanceStatus>(`${this.BASE}/api/admin/maintenance`).subscribe({
      next: data => {
        this.maintenance = data;
        this.maintenanceNewMessage = data.message;
      },
      error: () => {}
    });
  }

  toggleMaintenance(): void {
    this.maintenanceLoading = true;
    this.maintenanceMsg = '';
    const newActive = !this.maintenance.active;
    this.http.put<MaintenanceStatus>(`${this.BASE}/api/admin/maintenance`, {
      active: newActive,
      message: this.maintenanceNewMessage || this.maintenance.message
    }).subscribe({
      next: data => {
        this.maintenance = data;
        this.maintenanceNewMessage = data.message;
        this.maintenanceLoading = false;
        this.maintenanceMsg = newActive
          ? this.translate.instant('ADMIN.MAINTENANCE_TOGGLE_ON')
          : this.translate.instant('ADMIN.MAINTENANCE_TOGGLE_OFF');
      },
      error: () => {
        this.maintenanceLoading = false;
        this.maintenanceMsg = this.translate.instant('ADMIN.MAINTENANCE_TOGGLE_ERROR');
      }
    });
  }

  saveMaintenance(): void {
    this.maintenanceLoading = true;
    this.maintenanceMsg = '';
    this.http.put<MaintenanceStatus>(`${this.BASE}/api/admin/maintenance`, {
      active: this.maintenance.active,
      message: this.maintenanceNewMessage
    }).subscribe({
      next: data => {
        this.maintenance = data;
        this.maintenanceLoading = false;
        this.maintenanceMsg = this.translate.instant('ADMIN.MAINTENANCE_SAVE_SUCCESS');
      },
      error: () => {
        this.maintenanceLoading = false;
        this.maintenanceMsg = this.translate.instant('ADMIN.MAINTENANCE_SAVE_ERROR');
      }
    });
  }

  // ── Backup ────────────────────────────────────────────────────────────────────
  backups: BackupDto[] = [];
  backupLoading = false;
  backupMsg = '';
  backupConfig = { retention: 10 };
  confirmRestoreBackup: BackupDto | null = null;
  confirmDeleteBackup: BackupDto | null = null;

  loadBackups(): void {
    this.backupLoading = true;
    this.http.get<BackupDto[]>(`${this.BASE}/api/admin/backup`).subscribe({
      next: data => { this.backups = data; this.backupLoading = false; },
      error: () => { this.backupLoading = false; this.backupMsg = this.translate.instant('ADMIN.BACKUP_LOAD_ERROR'); }
    });
  }

  loadBackupConfig(): void {
    this.http.get<any>(`${this.BASE}/api/admin/backup/config`).subscribe({
      next: data => this.backupConfig = data,
      error: () => {}
    });
  }

  createBackup(): void {
    this.backupLoading = true;
    this.backupMsg = this.translate.instant('ADMIN.BACKUP_CREATING'); // (Wait, I should add this key or use a generic one)
    this.http.post<BackupDto>(`${this.BASE}/api/admin/backup`, null).subscribe({
      next: data => {
        this.backupLoading = false;
        this.backupMsg = this.translate.instant('ADMIN.BACKUP_CREATED', { name: data.name, size: data.sizeFormatted });
        this.loadBackups();
      },
      error: (err) => {
        this.backupLoading = false;
        this.backupMsg = '❌ ' + (err?.error?.error || this.translate.instant('ADMIN.BACKUP_CREATE_ERROR'));
      }
    });
  }

  confirmRestore(backup: BackupDto): void {
    this.confirmRestoreBackup = backup;
  }

  restoreBackup(): void {
    if (!this.confirmRestoreBackup) return;
    const filename = this.confirmRestoreBackup.name;
    this.confirmRestoreBackup = null;
    this.backupLoading = true;
    this.backupMsg = '⏳ Restaurando base de datos...';

    this.http.post<any>(`${this.BASE}/api/admin/backup/restore`, { filename }).subscribe({
      next: (res) => {
        this.backupLoading = false;
        this.backupMsg = res.message || this.translate.instant('ADMIN.BACKUP_RESTORED');
        this.loadBackups();
      },
      error: (err) => {
        this.backupLoading = false;
        this.backupMsg = '❌ ' + (err?.error?.error || this.translate.instant('ADMIN.BACKUP_RESTORE_ERROR'));
      }
    });
  }

  confirmDelete(backup: BackupDto): void {
    this.confirmDeleteBackup = backup;
  }

  deleteBackup(): void {
    if (!this.confirmDeleteBackup) return;
    const filename = this.confirmDeleteBackup.name;
    this.confirmDeleteBackup = null;
    this.http.delete<any>(`${this.BASE}/api/admin/backup/${encodeURIComponent(filename)}`).subscribe({
      next: () => {
        this.backupMsg = this.translate.instant('ADMIN.BACKUP_DELETED', { filename });
        this.loadBackups();
      },
      error: () => this.backupMsg = '❌ ' + this.translate.instant('ADMIN.BACKUP_DELETE_ERROR')
    });
  }

  saveBackupConfig(): void {
    this.http.put<any>(`${this.BASE}/api/admin/backup/config`, this.backupConfig).subscribe({
      next: () => this.backupMsg = this.translate.instant('ADMIN.BACKUP_CONFIG_SUCCESS'),
      error: () => this.backupMsg = this.translate.instant('ADMIN.BACKUP_CONFIG_ERROR')
    });
  }

  // ── System info & Update ──────────────────────────────────────────────────────
  systemInfo: SystemInfoDto | null = null;
  updateCheck: UpdateCheckDto | null = null;
  systemLoading = false;
  systemMsg = '';
  prepareUpdateMsg = '';
  prepareUpdateLoading = false;

  loadSystemInfo(): void {
    this.systemLoading = true;
    this.http.get<SystemInfoDto>(`${this.BASE}/api/admin/system/info`).subscribe({
      next: data => { this.systemInfo = data; this.systemLoading = false; },
      error: () => { this.systemLoading = false; }
    });
  }

  checkUpdate(): void {
    this.systemLoading = true;
    this.updateCheck = null;
    this.systemMsg = this.translate.instant('ADMIN.UPDATE_SEARCHING_LONG');
    this.http.get<UpdateCheckDto>(`${this.BASE}/api/admin/system/check-update`).subscribe({
      next: data => {
        this.updateCheck = data;
        this.systemLoading = false;
        if (!data.checkConfigured) {
          this.systemMsg = this.translate.instant('ADMIN.UPDATE_NOT_CONFIGURED');
        } else if (data.error) {
          this.systemMsg = '❌ ' + this.translate.instant('ADMIN.UPDATE_ERROR', { error: data.error });
        } else if (data.updateAvailable) {
          this.systemMsg = this.translate.instant('ADMIN.UPDATE_AVAILABLE', { version: data.latestVersion });
        } else {
          this.systemMsg = this.translate.instant('ADMIN.UPDATE_UP_TO_DATE_VER', { version: data.currentVersion });
        }
      },
      error: () => {
        this.systemLoading = false;
        this.systemMsg = '❌ ' + this.translate.instant('ADMIN.UPDATE_CONNECT_ERROR');
      }
    });
  }

  prepareUpdate(): void {
    this.prepareUpdateLoading = true;
    this.prepareUpdateMsg = this.translate.instant('ADMIN.UPDATE_PREPARING_LONG');
    this.http.post<any>(`${this.BASE}/api/admin/system/prepare-update`, {
      message: 'La aplicación se está actualizando. Vuelve en unos minutos.'
    }).subscribe({
      next: (res) => {
        this.prepareUpdateLoading = false;
        this.prepareUpdateMsg = res.message;
        this.loadMaintenance();
        this.loadBackups();
      },
      error: (err) => {
        this.prepareUpdateLoading = false;
        this.prepareUpdateMsg = '❌ ' + (err?.error?.error || this.translate.instant('ADMIN.UPDATE_PREPARING_ERROR'));
      }
    });
  }

  formatUptime(seconds: number): string {
    if (!seconds) return '—';
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m ${seconds % 60}s`;
  }

  onSistemaTab(): void {
    this.loadMaintenance();
    this.loadBackups();
    this.loadBackupConfig();
    this.loadSystemInfo();
  }
}