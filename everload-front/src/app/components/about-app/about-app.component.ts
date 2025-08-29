// about-app.component.ts
import { Component, AfterViewInit, ChangeDetectorRef } from '@angular/core';
import { gsap } from 'gsap';
import { MorphSVGPlugin } from 'gsap/MorphSVGPlugin';
import { TranslateService } from '@ngx-translate/core';

gsap.registerPlugin(MorphSVGPlugin);

type Lang = 'es'|'gl'|'en';

@Component({
  selector: 'app-about-app',
  templateUrl: './about-app.component.html',
  styleUrls: ['./about-app.component.css']
})
export class AboutAppComponent implements AfterViewInit {

  constructor(private translate: TranslateService, private cdr: ChangeDetectorRef) {
    const supported: Lang[] = ['es','gl','en'];
    this.translate.addLangs(supported);
    this.translate.setDefaultLang('es');

    // es-ES -> es, en-US -> en …
    const saved = localStorage.getItem('lang');
    const browser = (this.translate.getBrowserCultureLang() || 'es').slice(0,2);
    const initial = (saved || browser) as string;
    const lang: Lang = supported.includes(initial as Lang) ? initial as Lang : 'es';

    this.translate.use(lang);
  }

  setLang(lang: Lang) {
    if (this.translate.currentLang === lang) return;
    this.translate.use(lang).subscribe(() => {
      localStorage.setItem('lang', lang);
      this.cdr.markForCheck(); // asegura refresco de la vista
    });
  }

  currentLang(): Lang {
    return (this.translate.currentLang as Lang) || 'es';
  }

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
