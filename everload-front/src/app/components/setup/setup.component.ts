import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { SetupLibrary, SetupPlatform, SetupService, SetupStorage } from '../../services/setup.service';

interface ServiceEntry {
  id: SetupPlatform; title: string; icon: string; fields: { key: string; label: string; value: string; visible: boolean }[];
  configured: boolean; state: string; busy: boolean; helpOpen?: boolean;
}

@Component({
  selector: 'app-setup', standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, MatIconModule, TranslateModule],
  templateUrl: './setup.component.html', styleUrls: ['./setup.component.css']
})
export class SetupComponent implements OnInit, OnDestroy {
  @ViewChild('stepTitle') stepTitle?: ElementRef<HTMLElement>;
  step = 1;
  checking = true;
  closed = false;
  busy = false;
  error = '';
  attempted = false;
  showPassword = false;
  showConfirmation = false;
  showCodeHelp = false;
  language = localStorage.getItem('language') || 'es';
  readonly languages = [{ id: 'es', label: 'Español' }, { id: 'gl', label: 'Galego' }, { id: 'en', label: 'English' }];
  readonly steps = ['WELCOME', 'ADMIN', 'LIBRARY', 'SERVICES', 'SUMMARY'];
  readonly codeCommand = 'docker compose exec everload cat /app/data/setup-code';
  readonly serviceGuides: Record<SetupPlatform, { portal: string; docs: string }> = {
    spotify: { portal: 'https://developer.spotify.com/dashboard', docs: 'https://developer.spotify.com/documentation/web-api/concepts/apps' },
    youtube: { portal: 'https://console.cloud.google.com/apis/credentials', docs: 'https://developers.google.com/youtube/data/getting-started' },
    acoustid: { portal: 'https://acoustid.org/webservice', docs: 'https://acoustid.org/faq' }
  };
  storage?: SetupStorage;
  storageBusy = false;
  name = '';
  libraries: SetupLibrary[] = [];
  private destroyed = false;
  private authSubscription?: Subscription;
  readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(128)]],
    username: ['', [Validators.required, Validators.pattern('[a-zA-Z0-9_.-]{3,40}')]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(72)]],
    confirmation: ['', [Validators.required, Validators.maxLength(72)]]
  });
  readonly services: ServiceEntry[] = [
    { id: 'spotify', title: 'Spotify', icon: 'music_note', configured: false, state: '', busy: false,
      fields: [{ key: 'clientId', label: 'CLIENT_ID', value: '', visible: true }, { key: 'clientSecret', label: 'CLIENT_SECRET', value: '', visible: false }] },
    { id: 'youtube', title: 'YouTube', icon: 'play_circle', configured: false, state: '', busy: false,
      fields: [{ key: 'apiKey', label: 'API_KEY', value: '', visible: false }] },
    { id: 'acoustid', title: 'AcoustID', icon: 'fingerprint', configured: false, state: '', busy: false,
      fields: [{ key: 'acoustidApiKey', label: 'API_KEY', value: '', visible: false }] }
  ];

  constructor(private fb: FormBuilder, private setup: SetupService, public auth: AuthService,
              private router: Router, private translate: TranslateService, private changeDetector: ChangeDetectorRef) {}

  t(key: string, params?: object): string { return this.translate.instant('SETUP.' + key, params); }
  async ngOnInit(): Promise<void> {
    this.authSubscription = this.auth.currentUser$.subscribe(() => {
      if (this.step >= 3 && !this.auth.isLoggedIn()) this.close();
    });
    try {
      const status = await firstValueFrom(this.setup.status());
      if (!status.required) {
        if (this.auth.isLoggedIn() && this.auth.isAdmin() && sessionStorage.getItem('everload_setup_step')) {
          this.go(Math.max(3, Math.min(5, Number(sessionStorage.getItem('everload_setup_step')) || 3)));
          await this.loadConfiguration();
          if (this.step === 3) await this.browse();
        } else this.close();
      }
    } catch { this.error = 'LOAD_FAILED'; }
    finally { this.checking = false; }
  }
  ngOnDestroy(): void { this.destroyed = true; this.authSubscription?.unsubscribe(); this.clearSecrets(); }
  setLanguage(language: string): void {
    this.language = language; localStorage.setItem('language', language); this.translate.use(language);
    document.documentElement.lang = language;
  }
  start(): void { this.go(2); }
  retry(): void { window.location.reload(); }
  go(step: number): void {
    if (step < 3 && this.step >= 3) return;
    this.error = ''; this.step = step;
    if (step >= 3) sessionStorage.setItem('everload_setup_step', String(step));
    setTimeout(() => {
      if (!this.destroyed) {
        this.changeDetector.detectChanges();
        this.stepTitle?.nativeElement.focus();
      }
    });
  }
  fieldError(field: string): boolean {
    const control = this.form.get(field);
    return !!control && (this.attempted || control.touched) &&
      (control.invalid || (field === 'confirmation' && control.value !== this.form.controls.password.value));
  }
  async createAdmin(): Promise<void> {
    this.attempted = true;
    if (this.form.invalid || this.form.controls.password.value !== this.form.controls.confirmation.value || this.busy) return;
    this.busy = true; this.error = '';
    try {
      await firstValueFrom(this.auth.createAdministrator(this.form.getRawValue()));
      this.form.reset(); this.go(3);
      await this.loadConfiguration(); await this.browse();
    } catch (error: any) {
      const code = error.error?.error;
      if (code === 'SETUP_CLOSED' || (error.status === 409 && !['SETUP_USERNAME', 'SETUP_EMAIL'].includes(code))) this.close();
      else this.error = ['SETUP_CODE', 'SETUP_USERNAME', 'SETUP_EMAIL', 'SETUP_PASSWORD'].includes(code) ? code : 'CREATE_FAILED';
    } finally { this.busy = false; }
  }
  private async loadConfiguration(): Promise<void> {
    try {
      const config = await firstValueFrom(this.setup.configuration());
      this.libraries = config.libraries;
      this.services.forEach(service => service.configured = config.services[service.id]);
    } catch { this.error = 'LOAD_FAILED'; }
  }
  async browse(path = ''): Promise<void> {
    if (this.storageBusy) return;
    this.storageBusy = true; this.error = '';
    try { this.storage = await firstValueFrom(this.setup.storage(path)); }
    catch { this.storage = undefined; this.error = 'STORAGE_FAILED'; }
    finally { this.storageBusy = false; }
  }
  parent(): void {
    const parts = (this.storage?.relativePath || '').split('/').filter(Boolean); parts.pop();
    void this.browse(parts.join('/'));
  }
  async saveLibrary(): Promise<void> {
    if (this.busy || this.storageBusy || !this.storage?.readable || !this.name.trim()) return;
    this.busy = true; this.error = '';
    try {
      const library = await firstValueFrom(this.setup.library(this.name.trim(), this.storage.relativePath));
      this.libraries.push(library); this.name = ''; this.go(4);
    } catch { this.error = 'LIBRARY_FAILED'; }
    finally { this.busy = false; }
  }
  dirty(service: ServiceEntry): void { service.state = ''; }
  async saveService(service: ServiceEntry, verify = false): Promise<void> {
    if (service.busy) return;
    const values = Object.fromEntries(service.fields.map(field => [field.key, field.value.trim()]));
    const hasValues = Object.values(values).some(Boolean);
    if (hasValues && Object.values(values).some(value => !value)) { service.state = 'FIELDS'; return; }
    if (!hasValues && !service.configured) { service.state = 'FIELDS'; return; }
    service.busy = true; service.state = '';
    try {
      if (hasValues) {
        await firstValueFrom(this.setup.save(service.id, values));
        service.configured = true;
        service.fields.forEach(field => field.value = '');
      }
      service.state = 'saved';
      if (verify && service.id !== 'acoustid') service.state = (await firstValueFrom(this.setup.verify(service.id))).status;
    } catch { service.state = service.configured ? 'failed' : 'SAVE_FAILED'; }
    finally { service.busy = false; }
  }
  get serviceBusy(): boolean { return this.services.some(service => service.busy); }
  async finishServices(): Promise<void> {
    if (this.serviceBusy) return;
    for (const service of this.services) {
      if (service.fields.some(field => !!field.value.trim())) await this.saveService(service);
    }
    this.clearSecrets(); this.go(5); await this.loadConfiguration();
  }
  async summary(): Promise<void> { this.clearSecrets(); this.go(5); await this.loadConfiguration(); }
  private clearSecrets(): void { this.form.reset(); this.services.forEach(service => service.fields.forEach(field => field.value = '')); }
  close(): void { this.clearSecrets(); this.closed = true; this.error = ''; }
  enter(): void {
    sessionStorage.removeItem('everload_setup_step');
    void this.router.navigateByUrl(this.auth.isLoggedIn() ? '/' : '/login');
  }
  @HostListener('window:storage', ['$event']) onStorage(event: StorageEvent): void {
    if (event.key !== 'auth_token' && event.key !== 'auth_user') return;
    if (this.step >= 3) this.close();
    else this.setup.status().subscribe({ next: status => { if (!status.required) this.close(); }, error: () => {} });
  }
}
