import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';

@Component({
  selector: 'app-offline-banner',
  templateUrl: './offline-banner.component.html',
  styleUrls: ['./offline-banner.component.css']
})
export class OfflineBannerComponent implements OnInit, OnDestroy {
  offline = false;

  // Brief "back online" confirmation before hiding
  showingReconnected = false;
  private reconnectedTimer: any = null;

  ngOnInit(): void {
    this.offline = typeof navigator !== 'undefined' && !navigator.onLine;
  }

  ngOnDestroy(): void {
    if (this.reconnectedTimer) clearTimeout(this.reconnectedTimer);
  }

  @HostListener('window:offline')
  onOffline(): void {
    if (this.reconnectedTimer) { clearTimeout(this.reconnectedTimer); this.reconnectedTimer = null; }
    this.showingReconnected = false;
    this.offline = true;
  }

  @HostListener('window:online')
  onOnline(): void {
    this.showingReconnected = true;
    this.reconnectedTimer = setTimeout(() => {
      this.offline = false;
      this.showingReconnected = false;
    }, 2500);
  }
}
