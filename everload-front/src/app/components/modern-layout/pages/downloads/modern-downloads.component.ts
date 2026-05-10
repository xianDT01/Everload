import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-modern-downloads',
  template: `
    <div class="mdl-page">
      <h1 class="mdl-title">⬇️ Descargas</h1>
      <p class="mdl-desc">Las descargas se gestionan desde la vista clásica.</p>
      <button class="mdl-btn" (click)="go()">Ir a descargas</button>
    </div>
  `,
  styles: [`
    .mdl-page { padding: 48px; display: flex; flex-direction: column; align-items: center; gap: 16px; }
    .mdl-title { font-size: 24px; font-weight: 800; color: #e4e4e7; margin: 0; }
    .mdl-desc { color: #71717a; font-size: 14px; margin: 0; }
    .mdl-btn { background: #6366f1; border: none; color: #fff; padding: 10px 24px; border-radius: 8px; font-size: 14px; cursor: pointer; }
    .mdl-btn:hover { background: #818cf8; }
  `]
})
export class ModernDownloadsComponent {
  constructor(private router: Router) {}
  go() { this.router.navigate(['/youtube-download']); }
}
