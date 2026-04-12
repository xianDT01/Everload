import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-nas-music',
  templateUrl: './nas-music.component.html',
  styleUrls: ['./nas-music.component.css']
})
export class NasMusicComponent implements OnInit {
  mode: 'library' | 'deck' = 'library';

  constructor(private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const modeParam = this.route.snapshot.queryParamMap.get('mode');
    if (modeParam === 'deck' || modeParam === 'library') {
      this.mode = modeParam;
    }
  }

  goBack() {
    this.router.navigate(['/']);
  }
}