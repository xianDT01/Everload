import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { AppUpdatePrompt, PwaUpdateService } from '../../services/pwa-update.service';

@Component({
  selector: 'app-pwa-update-banner',
  templateUrl: './pwa-update-banner.component.html',
  styleUrls: ['./pwa-update-banner.component.css']
})
export class PwaUpdateBannerComponent implements OnInit, OnDestroy {
  prompt: AppUpdatePrompt | null = null;
  private sub?: Subscription;

  constructor(private pwaUpdate: PwaUpdateService) {}

  ngOnInit(): void {
    this.sub = this.pwaUpdate.updatePrompt$.subscribe(prompt => {
      this.prompt = prompt;
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  reload(): void {
    this.pwaUpdate.applyUpdate();
  }

  dismiss(): void {
    this.pwaUpdate.dismiss();
  }
}
