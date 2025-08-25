import { Component, AfterViewInit } from '@angular/core';
import { gsap } from 'gsap';
import { MorphSVGPlugin } from 'gsap/MorphSVGPlugin';

gsap.registerPlugin(MorphSVGPlugin);

@Component({
  selector: 'app-about-app',
  templateUrl: './about-app.component.html',
  styleUrls: ['./about-app.component.css']
})
export class AboutAppComponent implements AfterViewInit {

  ngAfterViewInit(): void {
    document.getElementById("fbIcon")?.addEventListener("click", () => {
      gsap.to("#fbLetter", { duration: 1, morphSVG: "#like" });
    });

    document.getElementById("igIcon")?.addEventListener("click", () => {
      gsap.to(".cameraSquareTwo", { duration: 1, morphSVG: "#heart" });
      document.getElementById("camera")!.style.display = "none";
      document.getElementById("dot")!.style.display = "none";
    });

    document.getElementById("twitterIcon")?.addEventListener("click", () => {
      gsap.to("#bird", { duration: 1, morphSVG: ".addIcon" });
    });

    document.getElementById("youtubeIcon")?.addEventListener("click", () => {
      gsap.to(".play", { duration: 1, morphSVG: "#click" });
      document.getElementById("extra")!.style.display = "none";
    });
  }
}
