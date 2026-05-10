import { Component, OnInit } from '@angular/core';
import { MusicService } from '../../../../services/music.service';

@Component({
  selector: 'app-modern-activity',
  templateUrl: './modern-activity.component.html',
  styleUrls: ['./modern-activity.component.css']
})
export class ModernActivityComponent implements OnInit {
  stats: any = null;
  loading = false;

  constructor(private music: MusicService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.music.getListeningStats(20).subscribe({
      next: s => { this.stats = s; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }
}
