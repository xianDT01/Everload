import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-nas-music',
  templateUrl: './nas-music.component.html',
  styleUrls: ['./nas-music.component.css']
})
export class NasMusicComponent {
  mode: 'library' | 'deck' = 'library';

  constructor(private router: Router) {}

  goBack() {
    this.router.navigate(['/']);
  }
}