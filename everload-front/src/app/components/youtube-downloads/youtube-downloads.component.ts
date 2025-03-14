import { Component } from '@angular/core';

@Component({
  selector: 'app-youtube-downloads',
  templateUrl: './youtube-downloads.component.html',
  styleUrls: ['./youtube-downloads.component.css']
})
export class YoutubeDownloadsComponent {
  videoUrl: string = '';

  downloadVideo() {
    console.log(`Descargar Video: ${this.videoUrl}`);
  }

  downloadMusic() {
    console.log(`Descargar Música: ${this.videoUrl}`);
  }
}
