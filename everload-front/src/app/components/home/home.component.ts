import { Component } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {
  constructor(private translate: TranslateService) {
    // Establecer idioma por defecto
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) {
      translate.use(savedLang);
    }
  }
  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang); // Guardar selección
  }
}
